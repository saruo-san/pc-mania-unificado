# PC Mania Unificado

PC Mania es una plataforma de inventario para productos tecnológicos,
compuesta por un backend protegido con Cognito/JWT, dos aplicaciones frontend
y un microservicio de mensajería asíncrona basado en Amazon SNS, SQS, DLQ y
CloudWatch.

El repositorio reúne los módulos necesarios para ejecutar el proyecto de forma
local:

```text
pc-mania/
├── pc-mania-backend/       API de productos, Cognito/JWT y productor de eventos
├── pc-mania-mensajeria/    SNS, SQS, DLQ, consumidores y administración
├── pc-mania-admin/         Panel administrativo de inventario y mensajería
├── pc-mania-tienda/        Catálogo para clientes
├── infra/                  CloudFormation y scripts de despliegue/limpieza
├── docs/                   Arquitectura y flujo de la EV3
├── GUIA_INICIO_SERVICIOS.md
└── GUIA_PRUEBAS_MENSAJERIA.md
```

## Arquitectura

### Servicios locales

| Módulo | Puerto | Responsabilidad |
|---|---:|---|
| `pc-mania-backend` | `8080` | API REST de productos y publicación de eventos |
| `pc-mania-mensajeria` | `8081` | SNS, SQS, DLQ, consumidores y administración |
| `pc-mania-tienda` | `5173` | Catálogo de solo lectura |
| `pc-mania-admin` | `5174` | Gestión de productos y operaciones de mensajería |

### Flujo de eventos

```text
Panel admin
    │
    ▼
Backend de productos :8080
    │  POST /api/messaging/events
    ▼
Microservicio de mensajería :8081
    │
    ▼
SNS: pc-mania-events
    │ fanout con filtros
    ├── SQS: pc-mania-notifications ──► notifications consumer
    ├── SQS: pc-mania-tickets       ──► tickets consumer
    └── SQS: pc-mania-documents     ──► documents consumer
             │
             └── fallo después de 3 entregas
                    ▼
                 DLQ por dominio
```

El consumidor confirma un mensaje exitoso con `DeleteMessage`. Si ocurre un
error, no elimina el mensaje; SQS lo vuelve a entregar después del visibility
timeout y finalmente lo envía a su DLQ mediante la Redrive Policy.

## Recursos AWS

La configuración predeterminada utiliza la región `us-east-1`.

### SNS

- Tópico: `pc-mania-events`

### Colas principales

- `pc-mania-notifications`
- `pc-mania-tickets`
- `pc-mania-documents`

### DLQ

- `pc-mania-notifications-dlq`
- `pc-mania-tickets-dlq`
- `pc-mania-documents-dlq`

### Filtros SNS

```text
notifications:
  product.created
  product.updated
  product.deleted
  order.created

tickets:
  order.created

documents:
  document.requested
```

### Parámetros de entrega

```text
Colas principales: retención de 4 días
DLQ: retención de 14 días
Visibility Timeout: 30 segundos
MaxReceiveCount: 3
```

CloudWatch tiene alarmas configuradas para detectar mensajes visibles en cada
DLQ y el dashboard consulta la antigüedad aproximada del mensaje más antiguo.

## Diagrama de arquitectura

GitHub renderiza el siguiente diagrama Mermaid directamente en esta página. La
vista detallada de componentes está disponible en
[architecture-diagram.md](./.github/modernize/assessment/engines/facts/architecture-diagram.md).
La implementación del API Manager y el flujo de la EV3 se explican en
[docs/EV3-ARQUITECTURA.md](./docs/EV3-ARQUITECTURA.md).

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
~~~mermaid
flowchart TD
    subgraph ReadmeClients["Frontend clients"]
        ReadmeStore["Tienda React port 5173"]
        ReadmeAdmin["Admin React port 5174"]
    end
    subgraph ReadmeGateway["API Manager"]
        ReadmeApiGateway["Amazon API Gateway REST API"]
        ReadmeGatewayAuth["Cognito authorizer and scopes"]
    end
    subgraph ReadmeProduct["Product API port 8080"]
        ReadmeApi["Spring REST API"]
        ReadmeProductService["Product service"]
        ReadmeCatalog[("In memory catalog")]
        ReadmeEvents["Event client"]
    end
    subgraph ReadmeMessaging["Messaging API port 8081"]
        ReadmeProducer["SNS event producer"]
        ReadmeKinesisProducer["Kinesis event producer"]
        ReadmeAdminApi["Messaging administration"]
        ReadmeNotifications["Notifications consumer"]
        ReadmeTickets["Tickets consumer"]
        ReadmeDocuments["Documents consumer"]
        ReadmeProductRealtime["product-realtime KCL"]
        ReadmeProductAudit["product-audit KCL"]
        ReadmeOrderProcessing["order-processing KCL"]
        ReadmeOrderAudit["order-audit KCL"]
    end
    subgraph ReadmeAws["AWS services"]
        ReadmeTopic["SNS pc-mania-events"]
        ReadmeNotificationQueue[("SQS notifications")]
        ReadmeTicketQueue[("SQS tickets")]
        ReadmeDocumentQueue[("SQS documents")]
        ReadmeNotificationDlq[("DLQ notifications")]
        ReadmeTicketDlq[("DLQ tickets")]
        ReadmeDocumentDlq[("DLQ documents")]
        ReadmeProductStream[("Kinesis product stream 3 shards")]
        ReadmeOrderStream[("Kinesis order stream 3 shards")]
        ReadmeKinesisDlq[("Kinesis DLQ stream")]
        ReadmeLeaseTables[("DynamoDB KCL leases and checkpoints")]
        ReadmeCloudWatch["CloudWatch alarms and metrics"]
    end
    subgraph ReadmeIdentity["Identity"]
        ReadmeCognito["Amazon Cognito access tokens"]
    end

    ReadmeStore -->|"product API with VITE_API_URL"| ReadmeApiGateway
    ReadmeAdmin -->|"product API with VITE_API_URL"| ReadmeApiGateway
    ReadmeApiGateway -->|"route product API and forward JWT"| ReadmeApi
    ReadmeApiGateway -.->|"validate user pool and scopes"| ReadmeGatewayAuth
    ReadmeGatewayAuth -.-> ReadmeCognito
    ReadmeApi -.->|"validate JWT again"| ReadmeCognito
    ReadmeAdmin -->|"local messaging administration"| ReadmeAdminApi
    ReadmeApi --> ReadmeProductService
    ReadmeProductService -->|"store product"| ReadmeCatalog
    ReadmeProductService -->|"publish enabled event types"| ReadmeEvents
    ReadmeEvents -->|"SNS event HTTP request"| ReadmeProducer
    ReadmeEvents -->|"Kinesis event HTTP request"| ReadmeKinesisProducer
    ReadmeProducer -->|"publish"| ReadmeTopic
    ReadmeTopic -->|"filtered fanout"| ReadmeNotificationQueue
    ReadmeTopic -->|"filtered fanout"| ReadmeTicketQueue
    ReadmeTopic -->|"filtered fanout"| ReadmeDocumentQueue
    ReadmeNotificationQueue --> ReadmeNotifications
    ReadmeTicketQueue --> ReadmeTickets
    ReadmeDocumentQueue --> ReadmeDocuments
    ReadmeNotificationQueue -->|"after 3 failures"| ReadmeNotificationDlq
    ReadmeTicketQueue -->|"after 3 failures"| ReadmeTicketDlq
    ReadmeDocumentQueue -->|"after 3 failures"| ReadmeDocumentDlq
    ReadmeKinesisProducer -->|"product events"| ReadmeProductStream
    ReadmeKinesisProducer -->|"order events"| ReadmeOrderStream
    ReadmeProductStream --> ReadmeProductRealtime
    ReadmeProductStream --> ReadmeProductAudit
    ReadmeOrderStream --> ReadmeOrderProcessing
    ReadmeOrderStream --> ReadmeOrderAudit
    ReadmeProductRealtime --> ReadmeLeaseTables
    ReadmeProductAudit --> ReadmeLeaseTables
    ReadmeOrderProcessing --> ReadmeLeaseTables
    ReadmeOrderAudit --> ReadmeLeaseTables
    ReadmeProductRealtime -->|"unrecoverable record"| ReadmeKinesisDlq
    ReadmeOrderProcessing -->|"unrecoverable record"| ReadmeKinesisDlq
    ReadmeAdminApi -->|"metrics and alarms"| ReadmeCloudWatch
    ReadmeKinesisProducer -->|"iterator age"| ReadmeCloudWatch
    ReadmeStore -.->|"sign in"| ReadmeCognito
    ReadmeAdmin -.->|"sign in"| ReadmeCognito
    ReadmeProducer -.->|"JWT validation"| ReadmeCognito
    ReadmeKinesisProducer -.->|"JWT validation"| ReadmeCognito
~~~

El panel también conserva llamadas directas al microservicio de mensajería
para herramientas administrativas locales. Para demostrar el flujo EV3 a través
del API Manager, crea o actualiza un producto con `KINESIS_EVENTS_ENABLED=true`.

## Seguridad

El proyecto usa Amazon Cognito y access tokens JWT.

| Aplicación o componente | Scope |
|---|---|
| Tienda | `pcmania-api/read`, `pcmania-api/order` |
| Panel admin | `pcmania-api/write` |
| Productor y administración de mensajería | `pcmania-api/write` |

El endpoint de salud es público. Las operaciones de escritura y administración
requieren autenticación y validación de issuer, firma, expiración, `token_use`,
`client_id` y scope.

No se agrega un scope `pcmania-api/admin`. El App Client de la tienda debe tener
habilitado `pcmania-api/order` para que los clientes puedan crear órdenes sin
recibir permisos de administración.
El inventario se mantiene en memoria para conservar el alcance acordado.

## Configuración local

No se deben versionar credenciales, tokens, archivos `.env` ni claves AWS.
Spring Boot recibe la configuración mediante variables de entorno y AWS SDK
utiliza su cadena estándar de credenciales.

## Requisitos de instalación

### Programas necesarios

| Programa | Versión recomendada | Uso |
|---|---|---|
| Git | versión actual | Descargar y actualizar el repositorio |
| JDK | Java 17 | Backend y microservicio de mensajería |
| Node.js | 20 LTS o superior | Frontends React/Vite |
| npm | Incluido con Node.js | Dependencias y scripts frontend |
| AWS CLI | Versión 2 | Verificar cuenta y recursos AWS |
| Navegador actualizado | Chrome, Edge o Firefox | Panel, tienda y autenticación |

Maven no debe instalarse manualmente: los módulos Java incluyen `mvnw.cmd` y
utilizan Maven Wrapper. Verifica el entorno con:

```powershell
git --version
java -version
node --version
npm --version
aws --version
```

### Dependencias por módulo

Las dependencias se instalan automáticamente desde los manifiestos. No se
guardan `node_modules` ni las dependencias Maven en el repositorio.

| Módulo | Dependencias principales |
|---|---|
| `pc-mania-backend` | Spring Web MVC, Security, OAuth2 Resource Server, Validation y Jackson |
| `pc-mania-mensajeria` | Spring Boot, Security, OAuth2 Resource Server, Validation, Jackson, AWS SDK SNS, SQS y CloudWatch |
| `pc-mania-admin` | React, React DOM, AWS Amplify, TypeScript, Vite y ESLint |
| `pc-mania-tienda` | React, React DOM, AWS Amplify, TypeScript, Vite y ESLint |

Versiones relevantes:

- Java: `17`.
- Spring Boot: `4.1.1`.
- AWS SDK utilizado por mensajería: `2.31.63`.
- React: `19.2.8`.
- AWS Amplify: `6.20.0`.
- Vite: `8.2.2`.
- TypeScript: `6.0.2`.

Las versiones completas están en los `pom.xml`, `package.json` y
`package-lock.json` de cada módulo.

### Instalación de dependencias

Para los módulos Java, Maven Wrapper descarga las dependencias al ejecutar:

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-backend
.\mvnw.cmd test

Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-mensajeria
.\mvnw.cmd test
```

Para cada frontend, ejecutar una vez:

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-admin
npm install

Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-tienda
npm install
```

Después de instalar, el arranque completo está documentado en
[GUIA_INICIO_SERVICIOS.md](./GUIA_INICIO_SERVICIOS.md).

El panel admin requiere localmente:

```text
VITE_API_URL=http://localhost:8080
VITE_MESSAGING_API_URL=http://localhost:8081
VITE_REDIRECT_SIGN_IN=http://localhost:5174/
VITE_REDIRECT_SIGN_OUT=http://localhost:5174/
```

Existe una plantilla sin secretos en:

```text
pc-mania-admin/.env.example
```

Para AWS CLI:

```powershell
aws sts get-caller-identity --region us-east-1
```

Si se usan credenciales temporales, deben cargarse solo en la terminal que
ejecuta AWS CLI o el microservicio de mensajería:

```powershell
$env:AWS_ACCESS_KEY_ID="<ACCESS_KEY_ID>"
$env:AWS_SECRET_ACCESS_KEY="<SECRET_ACCESS_KEY>"
$env:AWS_SESSION_TOKEN="<SESSION_TOKEN>"
$env:AWS_DEFAULT_REGION="us-east-1"
```

## Inicio rápido

La guía completa, con cada terminal y cada variable, está en
[GUIA_INICIO_SERVICIOS.md](./GUIA_INICIO_SERVICIOS.md).

Resumen:

### Mensajería

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-mensajeria
$env:MESSAGING_CONSUMERS_ENABLED="true"
.\mvnw.cmd spring-boot:run
```

### Backend

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-backend
$env:MESSAGING_EVENTS_ENABLED="true"
$env:MESSAGING_SERVICE_URL="http://localhost:8081"
.\mvnw.cmd spring-boot:run
```

### Panel admin

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-admin
npm install
npm run dev
```

### Tienda

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-tienda
npm install
npm run dev
```

## Pruebas

La guía detallada está en
[GUIA_PRUEBAS_MENSAJERIA.md](./GUIA_PRUEBAS_MENSAJERIA.md).

Incluye:

- Salud de servicios.
- Autenticación y scopes.
- Creación, actualización y eliminación de productos.
- Publicación de eventos reales.
- Fanout y filtros SNS.
- Consumidor pausado y reanudado.
- ACK y eliminación de mensajes.
- Redrive Policy y retención.
- Fallo controlado con `forceFailure=true`.
- Reintentos y DLQ.
- Reprocesamiento.
- Alarmas y métricas CloudWatch.
- Prueba opcional de dos instancias.
- Builds y pruebas automatizadas.

## Validación de código

### Mensajería

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-mensajeria
.\mvnw.cmd clean test
```

### Backend

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-backend
.\mvnw.cmd test
```

### Panel admin

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-admin
npm run build
```

## Administración de mensajería

El panel admin consulta el microservicio y permite:

- Consultar tópico, colas y DLQ.
- Ver mensajes visibles, en proceso, retrasados y antigüedad.
- Ver el estado de los consumidores.
- Pausar y reanudar consumidores.
- Reprocesar mensajes de una DLQ.
- Configurar alarmas CloudWatch.
- Consultar las políticas de retención.

El microservicio permite además administrar la topología mediante endpoints
protegidos bajo:

```text
/api/messaging/admin/**
```

## API Manager (AWS API Gateway)

La pauta EV3 exige que el frontend consuma el backend a través de un API
Manager. En este proyecto corresponde a **Amazon API Gateway**: recibe las
llamadas del navegador, valida los access tokens de Cognito y reenvía las rutas
`/api/productos` y `POST /api/ordenes` al backend, que vuelve a validar el JWT y
publica los eventos de producto/orden en mensajería.

La definición repetible está en
[infra/api-gateway-stack.yaml](./infra/api-gateway-stack.yaml). Acepta uno o
varios ARN de User Pools de Cognito para permitir el pool de tienda y el de
administración, y requiere los scopes `pcmania-api/read`, `pcmania-api/write`
y `pcmania-api/order`. Configura `pcmania-api/order` en el App Client de la
tienda y usa `-OrderScope` si el nombre difiere. El origen del backend debe ser
público y HTTPS; API
Gateway no puede enrutar a `localhost`. Para una prueba temporal, ngrok puede
publicar el backend, pero hay que tunelizar el puerto `8080`; publicar solo el
frontend no crea un origen para Gateway. El repositorio no inicia ni configura
ngrok automáticamente. El script
[infra/deploy-api-gateway.ps1](./infra/deploy-api-gateway.ps1) muestra la URL
resultante, que se configura como `VITE_API_URL` en ambos frontends. Las
llamadas locales pueden seguir apuntando directamente a `localhost:8080`; en
un frontend público, `VITE_API_URL` no debe ser `localhost`. Las operaciones
administrativas todavía usan `VITE_MESSAGING_API_URL` y no pasan por este
Gateway, por lo que requieren conectividad aparte con el servicio de mensajería.

El API Gateway expone las APIs de productos y creación de órdenes (`POST
/api/ordenes`). El backend debe tener configuradas
`MESSAGING_SERVICE_URL` y `KINESIS_SERVICE_URL` con una dirección alcanzable
para publicar eventos; el Gateway no despliega ni reemplaza ese microservicio.
Las llamadas administrativas del panel siguen usando `VITE_MESSAGING_API_URL`.

Configura `CORS_ALLOWED_ORIGINS` en el backend con los orígenes reales de los
frontends para que las respuestas proxied permitan el acceso desde el navegador.
La limpieza del API Gateway se realiza mediante
[infra/cleanup-api-gateway.ps1](./infra/cleanup-api-gateway.ps1).

## Streaming con Amazon Kinesis

El microservicio de mensajería incorpora Amazon Kinesis como una capa adicional
para streaming y reprocesamiento; no reemplaza SNS/SQS.

### Streams

| Stream | Shards | Retención |
|---|---:|---:|
| `pc-mania-product-events` | 3 | 24 horas |
| `pc-mania-order-events` | 3 | 24 horas |
| `pc-mania-kinesis-dlq` | 1 | 24 horas |

La topología repetible está declarada en
[infra/kinesis-stack.yaml](./infra/kinesis-stack.yaml). Se puede desplegar con:

```powershell
.\infra\deploy-kinesis.ps1 -Region us-east-1
```

### Eventos y partición

El productor recibe JSON tipado y serializa `KinesisEvent`. La clave de partición
es `partitionKey`; para productos usa `product-<id>` y para pedidos usa
`order-<id>`. Esto mantiene juntos eventos del mismo recurso y distribuye
particionadamente los registros entre tres shards.

### Consumidores

Se configuran KCL Scheduler por aplicación:

- `product-realtime` y `product-audit` sobre el stream de productos.
- `order-processing` y `order-audit` sobre el stream de órdenes.

Cada aplicación utiliza una tabla DynamoDB propia para leases y checkpoints.
Varias instancias de la misma aplicación pueden compartir esa tabla y repartir
dinámicamente los shards. El checkpoint se guarda después del procesamiento
del lote y al finalizar o detener un shard. El panel consulta la tabla para
mostrar los leases, workers asignados y checkpoints; el estado del hilo KCL se
presenta por separado del estado de DynamoDB.
La deduplicación también se persiste en `pc-mania-kinesis-idempotency`, usando
una clave distinta por aplicación consumidora y evento. DynamoDB elimina los
registros con TTL después de 24 horas, la misma ventana configurada para los
streams.

### Resiliencia

- Validación estructural y de contenido.
- Idempotencia distribuida por aplicación y `eventId`, persistida en DynamoDB
  durante 24 horas para cubrir la ventana de retención de Kinesis.
- Reintentos configurables con espera creciente para fallos transitorios y
  errores de publicación al DLQ.
- Eventos malformados y fallidos enviados al `pc-mania-kinesis-dlq`; si no se
  puede persistir en DLQ, el lote no avanza el checkpoint.
- Log estructurado con application, eventId, correlationId, intento, secuencia
  y causa.
- Un evento defectuoso se deriva al DLQ y no bloquea registros posteriores si
  el envío al DLQ se completa.

### API local

```text
POST /api/kinesis/events
GET  /api/kinesis/admin/streams/status
POST /api/kinesis/admin/streams
PUT  /api/kinesis/admin/streams/{key}/shards
PUT  /api/kinesis/admin/streams/{key}/retention
DELETE /api/kinesis/admin/streams/{key}
GET  /api/kinesis/admin/consumers
POST /api/kinesis/admin/alarms/iterator-age
```

Kinesis permanece deshabilitado hasta configurar:

```powershell
$env:KINESIS_ENABLED="true"
$env:KINESIS_EVENTS_ENABLED="true"
$env:AWS_REGION="us-east-1"
```

Las credenciales deben obtenerse mediante variables temporales, perfil AWS o
rol de ejecución. No deben guardarse en el repositorio.

Para finalizar una sesión y detener inmediatamente el cobro por Kinesis:

```powershell
.\infra\cleanup-kinesis.ps1 -Region us-east-1
```

El stack también contiene la tabla DynamoDB `pc-mania-orders`; su limpieza
elimina las órdenes persistidas junto con los streams y las tablas de leases.

## Decisiones de alcance

- El inventario de productos permanece en memoria; las órdenes se pueden
  guardar en DynamoDB con `ORDERS_PERSISTENCE=dynamodb`.
- La tienda usa `pcmania-api/read` para el catálogo y `pcmania-api/order` para
  crear órdenes; no recibe permisos de escritura administrativa.
- El administrador usa `pcmania-api/write`, sin crear un scope adicional.
- La idempotencia actual vive en memoria dentro de cada processor KCL; una
  versión distribuida y durable requeriría almacenamiento compartido.
- El patrón Outbox queda como mejora futura para evitar que una operación local
  confirmada quede sin evento si mensajería está temporalmente caída.
- El API Gateway es una capa de entrada opcional para la ejecución local; para
  la demo cloud los frontends deben configurarse con la URL del stack.

## Solución de problemas

| Problema | Revisión |
|---|---|
| `Failed to fetch` en mensajería | Confirmar servicio en `8081` y reiniciarlo con CORS actualizado |
| Producto guardado sin evento | Confirmar `MESSAGING_EVENTS_ENABLED=true` en la terminal del backend |
| Mensaje no se procesa | Confirmar `MESSAGING_CONSUMERS_ENABLED=true` en la terminal de mensajería |
| `401` | Revisar token, issuer y expiración |
| `403` | Revisar scope; la tienda no debe tener `write` |
| AWS sin acceso | Ejecutar `aws sts get-caller-identity --region us-east-1` |
| Cola vacía después de publicar | El consumidor probablemente procesó y confirmó el mensaje con ACK |
