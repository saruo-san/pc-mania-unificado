# PC Mania Mensajería

Microservicio Spring Boot para la mensajería asíncrona de PC Mania con Amazon SNS y SQS.

## Primera iteración

- Un tópico SNS: `pc-mania-events`.
- Tres colas SQS: notificaciones, tickets y documentos.
- Una DLQ por cada cola.
- Redrive Policy con `maxReceiveCount` configurable.
- Suscripciones con entrega directa (`RawMessageDelivery`) y filtros por `eventType`.
- API administrativa para crear e inspeccionar la topología, reprocesar y purgar DLQ.

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
