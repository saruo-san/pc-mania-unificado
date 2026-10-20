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
.\mvnw.cmd spring-boot:run
```

Health:

```text
GET http://localhost:8081/health
```

Crear la topología AWS:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/messaging/admin/topology"
```

Inspeccionar la topología:

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/messaging/admin/topology"
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

Publicar un evento:

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/messaging/events" `
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

Los endpoints administrativos todavía están destinados a la primera iteración
local. Antes de exponerlos mediante API Gateway se debe integrar Cognito/JWT y
autorizar las operaciones administrativas con un scope exclusivo.
