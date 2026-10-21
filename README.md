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

## Seguridad

El proyecto usa Amazon Cognito y access tokens JWT.

| Aplicación o componente | Scope |
|---|---|
| Tienda | `pcmania-api/read` |
| Panel admin | `pcmania-api/write` |
| Productor y administración de mensajería | `pcmania-api/write` |

El endpoint de salud es público. Las operaciones de escritura y administración
requieren autenticación y validación de issuer, firma, expiración, `token_use`,
`client_id` y scope.

No se agrega un scope `pcmania-api/admin` ni una base de datos en esta versión.
El inventario se mantiene en memoria para conservar el alcance acordado.

## Configuración local

No se deben versionar credenciales, tokens, archivos `.env` ni claves AWS.
Spring Boot recibe la configuración mediante variables de entorno y AWS SDK
utiliza su cadena estándar de credenciales.

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

## Decisiones de alcance

- No se agrega una base de datos; el inventario permanece en memoria.
- La tienda mantiene únicamente permisos de lectura.
- El administrador usa `pcmania-api/write`, sin crear un scope adicional.
- La idempotencia actual es por instancia; una versión distribuida requeriría
  almacenamiento compartido.
- El patrón Outbox queda como mejora futura para evitar que una operación local
  confirmada quede sin evento si mensajería está temporalmente caída.
- API Gateway/API Manager no forma parte de la ejecución local actual; los
  frontends llaman directamente a los servicios locales.

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
