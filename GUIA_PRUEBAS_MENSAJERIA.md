# Guía de pruebas y demostraciones de mensajería

Cada prueba indica qué demuestra, cómo funciona en este proyecto, qué comandos
usar y qué resultado visual esperar en el panel o las consolas. Los comandos
no contienen credenciales reales.

## Preparación común

Mantén iniciados:

- Mensajería en `http://localhost:8081`.
- Backend en `http://localhost:8080`.
- Panel admin en `http://localhost:5174`.

Para consultar AWS:

```powershell
$env:AWS_DEFAULT_REGION="us-east-1"
```

Para las pruebas API, usa un token obtenido localmente:

```powershell
$env:ADMIN_ACCESS_TOKEN="<TOKEN_ADMINISTRADOR>"
$headers = @{
    Authorization = "Bearer $env:ADMIN_ACCESS_TOKEN"
    "Content-Type" = "application/json"
}
```

Nunca publiques ese token.

## Prueba 1: salud y disponibilidad

### Qué demuestra

Que los servicios web están levantados antes de probar mensajería.

### Comandos

```powershell
Invoke-WebRequest http://localhost:8081/health -UseBasicParsing
Invoke-WebRequest http://localhost:8080/health -UseBasicParsing
```

### Resultado esperado

Ambos responden `200` y el cuerpo indica estado `UP`.

### Demostración visual

En las terminales se observan:

```text
Tomcat started on port 8081
Tomcat started on port 8080
```

## Prueba 2: seguridad Cognito y scopes

### Qué demuestra

Que los endpoints no quedan expuestos y que cliente y administrador tienen
permisos distintos.

### Comportamiento esperado

| Solicitud | Resultado |
|---|---|
| `/health` sin token | `200` |
| Administración sin token | `401` |
| Token sin scope write | `403` |
| Admin con `pcmania-api/write` | acceso permitido |
| Tienda leyendo productos | acceso permitido |
| Tienda escribiendo productos | `403` |

### Demostración visual

En la tienda se muestra el catálogo. Si se intenta una operación de escritura,
el navegador muestra una respuesta `403`. En el panel admin, las operaciones
administrativas cargan correctamente.

## Prueba 3: creación normal de producto

### Qué demuestra

El flujo completo de productor, SNS, SQS, consumidor y ACK.

### Pasos

1. Inicia sesión en `http://localhost:5174`.
2. Crea un producto.
3. Observa el backend.
4. Observa mensajería.
5. Pulsa `Actualizar estado`.

### Resultado esperado

Backend y mensajería deben completar la operación. En mensajería aparece:

```text
Notificación procesada: eventId=... type=product.created
```

La cola puede quedar en:

```text
visibles 0
en proceso 0
```

porque se ejecutó `DeleteMessage`.

### Demostración visual

En el panel:

```text
Producto agregado correctamente.
```

En la sección AWS/MENSAJERÍA se observa el tópico y el estado de las colas.

## Prueba 4: mensaje visible con consumidor pausado

### Qué demuestra

Que SQS conserva el mensaje cuando el consumidor no está disponible.

### Pasos

1. En `pc-mania-notifications`, pulsa `Pausar`.
2. El botón debe cambiar a `Reanudar`.
3. Crea un producto.
4. Pulsa `Actualizar estado`.

### Resultado esperado

La fila muestra temporalmente:

```text
visibles 1
en proceso 0
```

Al pulsar `Reanudar`, el consumidor recibe el mensaje y la cola vuelve a
`visibles 0`.

### Demostración visual

La evidencia visual es la transición:

```text
Pausar -> visibles 1 -> Reanudar -> visibles 0
```

Esta prueba fue realizada correctamente en el proyecto.

## Prueba 5: filtros SNS

### Qué demuestra

Que cada dominio recibe solo los eventos permitidos por su suscripción.

### Consulta

```powershell
$topicArn = "arn:aws:sns:us-east-1:<ACCOUNT_ID>:pc-mania-events"
$subscriptions = aws sns list-subscriptions-by-topic `
  --topic-arn $topicArn `
  --region us-east-1 `
  --query "Subscriptions[].SubscriptionArn" `
  --output text

foreach ($subscription in ($subscriptions -split "\s+")) {
    if ($subscription) {
        aws sns get-subscription-attributes `
          --subscription-arn $subscription `
          --region us-east-1 `
          --output json
    }
}
```

### Resultado esperado

```text
notifications: product.created, product.updated, product.deleted, order.created
tickets: order.created
documents: document.requested
```

### Demostración visual

En AWS SNS se muestran tres suscripciones al tópico, cada una con su
`FilterPolicy`. Un `document.requested` no debe aparecer en notifications ni
tickets.

## Prueba 6: Redrive Policy y retención

### Qué demuestra

Que las colas tienen retención, visibility timeout y DLQ configurados.

### Comando

```powershell
aws sqs get-queue-attributes `
  --queue-url https://sqs.us-east-1.amazonaws.com/<ACCOUNT_ID>/pc-mania-notifications `
  --attribute-names All `
  --region us-east-1 `
  --query "Attributes.{Retention:MessageRetentionPeriod,Visibility:VisibilityTimeout,Redrive:RedrivePolicy}" `
  --output json
```

### Resultado esperado

```text
Retention: 345600
Visibility: 30
maxReceiveCount: 3
deadLetterTargetArn: notifications-dlq
```

### Demostración visual

En la consola SQS se muestra la cola principal enlazada con su DLQ mediante
Redrive Policy.

## Prueba 7: fallo controlado y DLQ

### Qué demuestra

El flujo de error:

```text
fallo -> reintentos -> maxReceiveCount -> DLQ -> alarma
```

### Publicación

Con `$headers` definido:

```powershell
$body = @{
    eventId = [guid]::NewGuid().ToString()
    eventType = "product.created"
    payload = @{
        id = 99999
        nombre = "Producto prueba DLQ"
        marca = "PC Mania"
    }
    metadata = @{
        forceFailure = "true"
        test = "dlq-demo"
    }
} | ConvertTo-Json -Depth 10

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8081/api/messaging/events `
  -Headers $headers `
  -Body $body
```

### Resultado esperado

Con `VisibilityTimeout=30` y `maxReceiveCount=3`, esperar aproximadamente
90-120 segundos. La consola de mensajería debe registrar fallos del mismo
evento. Después, el mensaje llega a:

```text
pc-mania-notifications-dlq
```

### Consulta

```powershell
aws sqs get-queue-attributes `
  --queue-url https://sqs.us-east-1.amazonaws.com/<ACCOUNT_ID>/pc-mania-notifications-dlq `
  --attribute-names ApproximateNumberOfMessages `
  --region us-east-1
```

### Demostración visual

En el dashboard aumenta el contador `DLQ`. En CloudWatch la alarma de esa DLQ
puede pasar a `ALARM`.

Esta prueba está implementada y preparada, pero no se completó como una nueva
demostración limpia durante la sesión.

## Prueba 8: reprocesamiento de DLQ

### Qué demuestra

Que el administrador puede devolver mensajes aislados a la cola principal.

### Pasos

1. Tener al menos un mensaje en la DLQ.
2. Pulsar `Reprocesar DLQ` en notifications.
3. Actualizar el estado.
4. Observar que vuelve a la cola principal.

### Resultado visual esperado

```text
DLQ disminuye -> cola principal aumenta -> consumidor procesa
```

Si el mensaje conserva `forceFailure=true`, volverá a fallar. Eso es esperado.

## Prueba 9: pausa y reanudación por API

### Comandos

```powershell
Invoke-RestMethod -Method Get `
  -Uri http://localhost:8081/api/messaging/admin/consumers `
  -Headers $headers

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8081/api/messaging/admin/consumers/notifications/pause `
  -Headers $headers

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8081/api/messaging/admin/consumers/notifications/resume `
  -Headers $headers
```

### Demostración visual

El botón alterna entre `Pausar` y `Reanudar`, y la cola cambia entre mensaje
visible y mensaje procesado.

## Prueba 10: CloudWatch y antigüedad

### Qué demuestra

Que la edad del mensaje más antiguo se consulta como métrica CloudWatch.

### Comando

```powershell
aws cloudwatch get-metric-statistics `
  --namespace AWS/SQS `
  --metric-name ApproximateAgeOfOldestMessage `
  --dimensions Name=QueueName,Value=pc-mania-notifications `
  --statistics Maximum `
  --period 60 `
  --start-time <UTC_START> `
  --end-time <UTC_END> `
  --region us-east-1
```

### Demostración visual

Al pausar una cola con un mensaje, el dashboard muestra una antigüedad mayor
que cero. En la prueba realizada se observó aproximadamente `197s`.

## Prueba 11: dos instancias consumidoras

### Qué demuestra

Que SQS permite distribuir mensajes entre consumidores concurrentes y mantener
la continuidad si una instancia se detiene.

### Procedimiento

1. Crear una segunda terminal del microservicio.
2. Usar otro puerto, por ejemplo `8082`, manteniendo la misma región y colas.
3. Iniciar ambas instancias con consumidores activos.
4. Publicar varios eventos.
5. Observar logs de ambas instancias.
6. Detener una instancia.
7. Publicar más eventos y comprobar que la otra continúa.

### Resultado esperado

No todas las instancias deben procesar todos los mensajes. SQS entrega cada
mensaje a un consumidor disponible y el ACK evita duplicar el procesamiento
normal.

### Estado

Esta prueba no se ejecutó todavía. Requiere iniciar una segunda instancia
local y es opcional para la demostración actual.

## Prueba 12: validación de builds

### Mensajería

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-mensajeria
.\mvnw.cmd clean test
```

Resultado validado:

```text
5 tests, 0 failures
```

### Backend

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-backend
.\mvnw.cmd test
```

Resultado validado:

```text
6 tests, 0 failures
```

### Panel admin

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-admin
npm run build
```

Resultado validado: compilación TypeScript y Vite exitosa.

## Resumen de pruebas

| Prueba | Estado |
|---|---|
| Salud de servicios | Realizada |
| Cognito y scopes | Realizada |
| Producto y evento real | Realizada |
| Consumidor pausado/reanudado | Realizada |
| Filtros SNS | Verificados en AWS |
| Redrive y retención | Verificados en AWS |
| Fallo controlado nuevo | Preparada, no ejecutada nuevamente |
| Reprocesamiento visual | Implementado, pendiente de demostración limpia |
| CloudWatch y antigüedad | Verificada |
| Dos instancias | Pendiente |
| Builds | Realizada |
