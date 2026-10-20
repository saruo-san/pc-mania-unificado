# PC Mania Mensajería

Microservicio Spring Boot para la mensajería asíncrona de PC Mania con Amazon SNS y SQS.

## Primera iteración

- Un tópico SNS: `pc-mania-events`.
- Tres colas SQS: notificaciones, tickets y documentos.
- Una DLQ por cada cola.
- Redrive Policy con `maxReceiveCount` configurable.
- Suscripciones con entrega directa (`RawMessageDelivery`) y filtros por `eventType`.
- API administrativa para crear e inspeccionar la topología, reprocesar y purgar DLQ.
- Productor REST de eventos en `/api/messaging/events`.
- Consumidores por dominio para notificaciones, tickets y documentos.

Los nombres, retenciones, filtros y políticas están centralizados en
`src/main/resources/application.yml`. El servicio utiliza la cadena estándar de
credenciales de AWS SDK; no contiene credenciales.

## Ejecutar localmente

Se requiere Java 17 o superior, Maven Wrapper y credenciales AWS configuradas
fuera del repositorio:

```powershell
$env:AWS_REGION = "us-east-1"
$env:COGNITO_CLIENT_ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/REGION_POOL_CLIENTE"
$env:COGNITO_CLIENT_ID = "CLIENT_ID_CLIENTE"
$env:COGNITO_ADMIN_ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/REGION_POOL_ADMIN"
$env:COGNITO_ADMIN_CLIENT_ID = "CLIENT_ID_ADMIN"
# Para separar permisos, se puede definir un scope administrativo propio:
# $env:MESSAGING_ADMIN_SCOPE = "pcmania-api/admin"
.\mvnw.cmd spring-boot:run
```

Health:

```text
GET http://localhost:8081/health
```

Crear la topología AWS:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/messaging/admin/topology" `
  -Headers @{ Authorization = "Bearer TOKEN_ADMIN" }
```

Inspeccionar la topología:

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/messaging/admin/topology" `
  -Headers @{ Authorization = "Bearer TOKEN_ADMIN" }
```

Consultar suscripciones:

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/messaging/admin/subscriptions"
```

Eliminar una suscripción:

```powershell
Invoke-RestMethod -Method Delete `
  -Uri "http://localhost:8081/api/messaging/admin/subscriptions?arn=ARN_DE_SUSCRIPCION"
```

Eliminar una cola y su DLQ:

```powershell
Invoke-RestMethod -Method Delete `
  -Uri "http://localhost:8081/api/messaging/admin/queues/notifications"
```

Eliminar el tópico:

```powershell
Invoke-RestMethod -Method Delete `
  -Uri "http://localhost:8081/api/messaging/admin/topic"
```

La inspección de topología expone los contadores SQS de mensajes visibles,
en proceso y retrasados, además de la `RedrivePolicy`. La antigüedad del
mensaje más antiguo se incorporará mediante CloudWatch, porque SQS no la
expone como atributo de la cola.

Configurar las alarmas de CloudWatch para las tres DLQ:

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/messaging/admin/cloudwatch/dlq-alarms"
```

Consultar la política de retención y limpieza:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8081/api/messaging/admin/cloudwatch/retention-policy"
```

Las alarmas se crean en el namespace `AWS/SQS` con la métrica
`ApproximateNumberOfMessagesVisible` y umbral de un mensaje. Para esta
demostración las acciones están desactivadas; el estado de la alarma se
consulta en CloudWatch. Las colas principales retienen mensajes durante
4 días y las DLQ durante 14 días. La purga se mantiene como una operación
manual y explícita para evitar eliminar evidencia antes de la presentación.

Publicar un evento:

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/messaging/events" `
  -Headers @{ Authorization = "Bearer TOKEN_CON_PERMISO_WRITE" } `
  -ContentType "application/json" `
  -Body '{"eventId":"evt-001","eventType":"order.created","payload":"pedido creado","metadata":{"source":"pc-mania"}}'
```

La respuesta `202 Accepted` confirma que SNS aceptó el evento. Un evento
`order.created` se enruta a notificaciones y tickets; `document.requested`
solamente a documentos.

Para provocar un fallo controlado durante una demostración, agrega
`"forceFailure":"true"` en `metadata`. El consumidor registra el intento, no
confirma el mensaje y SQS aplica la `RedrivePolicy` hasta moverlo a la DLQ.

Controlar consumidores:

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/messaging/admin/consumers"
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/messaging/admin/consumers/notifications/pause"
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/messaging/admin/consumers/notifications/resume"
```

Los consumidores están desactivados por defecto para permitir ejecutar
`/health` y las pruebas sin recursos AWS creados. Después de crear la topología,
inícialos con:

```powershell
$env:MESSAGING_CONSUMERS_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

Cada consumidor confirma explícitamente los mensajes procesados con
`DeleteMessage`. Ante un error conserva el mensaje, registra `messageId`,
intento y causa, y deja que SQS aplique la `RedrivePolicy`.

Reprocesar una DLQ:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/messaging/admin/queues/notifications/dlq/reprocess"
```

Purgar una DLQ:

```powershell
Invoke-RestMethod -Method Delete -Uri "http://localhost:8081/api/messaging/admin/queues/notifications/dlq/messages"
```

## Seguridad

Todos los endpoints, excepto `/health`, requieren un access token JWT de
Cognito. El servicio valida firma, emisor, expiración, `token_use=access` y
`client_id`. El endpoint productor requiere el scope configurado en
`MESSAGING_WRITE_SCOPE` y la administración requiere
`MESSAGING_ADMIN_SCOPE`; por compatibilidad local ambos usan
`pcmania-api/write` por defecto, pero en AWS se recomienda crear y usar
`pcmania-api/admin` exclusivamente para las operaciones administrativas.

Un token sin autenticación recibe `401 Unauthorized`; un token válido sin el
scope requerido recibe `403 Forbidden`. Los tokens se envían únicamente desde
el cliente o API Gateway y nunca se almacenan en el repositorio.
