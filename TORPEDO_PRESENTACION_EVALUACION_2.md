# Torpedo para la presentación - Evaluación Parcial 2

## 1. Idea central

PC Mania utiliza mensajería asíncrona para desacoplar las operaciones de
productos de los procesos de notificación, tickets y documentos.

```text
Backend
  -> SNS
  -> SQS
  -> Consumidor
  -> ACK
  -> DLQ si falla varias veces
```

## 2. Arquitectura en una frase

> El backend publica un evento en SNS; SNS lo distribuye mediante fanout a
> colas SQS filtradas por dominio; cada consumidor procesa su mensaje y lo
> confirma con ACK, mientras que los mensajes fallidos terminan en una DLQ
> mediante una Redrive Policy.

## 3. Componentes

| Componente | Función |
|---|---|
| Productor | Publica eventos |
| SNS | Distribuye eventos a suscripciones |
| Suscripción | Conecta SNS con una cola |
| Filtro | Decide qué eventos recibe cada suscripción |
| SQS | Almacena mensajes hasta que un consumidor los procese |
| Consumidor | Lee y procesa mensajes |
| ACK | Confirmación mediante `DeleteMessage` |
| Visibility Timeout | Oculta temporalmente un mensaje mientras se procesa |
| Redrive Policy | Envía mensajes fallidos a una DLQ |
| DLQ | Conserva mensajes que agotaron sus reintentos |
| CloudWatch | Observa métricas y alarmas |

## 4. Topología configurada

### Tópico SNS

```text
pc-mania-events
```

### Colas principales

```text
pc-mania-notifications
pc-mania-tickets
pc-mania-documents
```

### DLQ

```text
pc-mania-notifications-dlq
pc-mania-tickets-dlq
pc-mania-documents-dlq
```

### Reintentos

```text
maxReceiveCount = 3
visibility timeout = 30 segundos
```

## 5. Fanout

Fanout significa que un mismo evento publicado una sola vez en SNS puede llegar
a varias colas.

Ejemplo:

```text
order.created
  -> notifications
  -> tickets
```

El productor no necesita enviar el mensaje dos veces ni conocer los
consumidores.

## 6. Filtros

Los filtros se aplican en las suscripciones SNS.

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

Ejemplo de filtro selectivo:

```text
document.requested
  -> documents
  -> no notifications
  -> no tickets
```

## 7. Eventos reales de productos

| Acción | Evento |
|---|---|
| Crear producto | `product.created` |
| Actualizar producto | `product.updated` |
| Eliminar producto | `product.deleted` |

Flujo de demostración:

```text
Crear producto en panel admin
  -> backend guarda producto
  -> backend publica product.created
  -> SNS distribuye
  -> notifications recibe
  -> consumidor procesa
  -> DeleteMessage confirma
```

## 8. ACK y mensajes visibles

El ACK no es un texto especial. En este proyecto es:

```text
DeleteMessage
```

Se ejecuta después de procesar exitosamente el mensaje.

Por eso la cola puede aparecer vacía en AWS: el consumidor ya leyó, procesó y
eliminó el mensaje.

Para mostrar un mensaje manualmente:

1. Pausar el consumidor.
2. Publicar o crear un producto.
3. Abrir la cola SQS.
4. Usar `Poll for messages`.
5. Reanudar el consumidor.

## 9. Reintentos y DLQ

Si el consumidor falla:

```text
No ejecuta DeleteMessage
  -> SQS vuelve a entregar el mensaje
  -> aumenta ApproximateReceiveCount
  -> después de 3 intentos llega a la DLQ
```

La DLQ no es una cola normal de trabajo. Es una cola de aislamiento para
mensajes que necesitan revisión, corrección o reprocesamiento.

## 10. Prueba de fallo controlado

El consumidor permite provocar un fallo de demostración con:

```json
{
  "eventId": "evt-failure-001",
  "eventType": "product.created",
  "payload": "evento de prueba",
  "metadata": {
    "forceFailure": "true"
  }
}
```

Resultado esperado:

```text
Intento 1 -> error
Intento 2 -> error
Intento 3 -> error
Mensaje -> DLQ
```

Para la presentación conviene mostrar:

- Log del error.
- Contador de recepción.
- Mensaje en la DLQ.
- Reprocesamiento.

## 11. Redrive Policy

La Redrive Policy relaciona una cola principal con su DLQ y define cuántas
veces se permite recibir un mensaje antes de moverlo.

En este proyecto:

```text
notifications -> notifications-dlq
tickets       -> tickets-dlq
documents     -> documents-dlq
maxReceiveCount = 3
```

## 12. Seguridad

La seguridad usa Cognito y JWT.

```text
/health                         público
POST /api/messaging/events      requiere pcmania-api/write
/api/messaging/admin/**         requiere scope administrativo
```

La tienda solo debe tener:

```text
pcmania-api/read
```

Eso explica por qué la tienda puede consultar productos, pero recibe `403` si
intenta publicar eventos. Es una restricción correcta.

## 13. Retención y CloudWatch

Configuración documentada:

```text
Colas principales: 4 días
DLQ: 14 días
```

CloudWatch observa, entre otras, la métrica:

```text
ApproximateNumberOfMessagesVisible
```

Se configuraron alarmas para detectar mensajes visibles en las DLQ.

## 14. Frases útiles para explicar conceptos

### SNS

> SNS es el distribuidor de eventos. No está pensado para que un consumidor
> haga polling directamente.

### SQS

> SQS es el buffer durable que desacopla al productor del consumidor.

### Fanout

> Fanout permite que una publicación se entregue a varios consumidores sin que
> el productor conozca cada cola.

### Filtro

> El filtro evita que una cola reciba eventos que no pertenecen a su dominio.

### ACK

> El ACK es la eliminación explícita del mensaje después de procesarlo con
> éxito.

### Visibility Timeout

> Es el período durante el cual el mensaje queda oculto mientras un consumidor
> intenta procesarlo.

### DLQ

> La DLQ aísla mensajes que fallaron repetidamente para que no bloqueen el
> flujo normal.

### Idempotencia

> La idempotencia evita efectos duplicados cuando SQS entrega nuevamente un
> mensaje.

## 15. Guion corto de demostración

1. Mostrar el tópico SNS y las tres colas.
2. Mostrar las tres DLQ y las Redrive Policies.
3. Crear un producto desde el panel admin.
4. Mostrar el log `product.created`.
5. Explicar que el mensaje fue confirmado con `DeleteMessage`.
6. Mostrar un evento `order.created` llegando a notifications y tickets.
7. Mostrar `document.requested` llegando solo a documents.
8. Provocar `forceFailure=true`.
9. Mostrar los tres intentos.
10. Mostrar el mensaje en la DLQ.
11. Reprocesar el mensaje.
12. Mostrar CloudWatch y la alarma de la DLQ.
13. Explicar los scopes Cognito.

## 16. Respuestas rápidas a preguntas típicas

### ¿Por qué usar SNS y SQS juntos?

SNS distribuye; SQS almacena y permite que los consumidores procesen de forma
independiente y con reintentos.

### ¿Qué pasa si un consumidor se cae?

El mensaje vuelve a estar visible después del Visibility Timeout y otra
instancia puede procesarlo. Si falla repetidamente, termina en la DLQ.

### ¿Por qué no aparece el mensaje al hacer polling en AWS?

Porque el consumidor ya lo procesó y ejecutó el ACK, que elimina el mensaje.

### ¿Por qué la tienda recibe 403 al publicar?

Porque la tienda solo tiene `pcmania-api/read`, que es el permiso correcto para
un usuario final.

### ¿Dónde se guardan las credenciales?

No se guardan en React ni en el código. AWS SDK usa su cadena estándar de
credenciales y Cognito entrega tokens temporales. Los archivos `.env` están
ignorados.

## 17. Pendientes que se pueden mencionar si preguntan

- Separar definitivamente `pcmania-api/write` y `pcmania-api/admin`.
- Persistir la idempotencia en almacenamiento compartido.
- Probar dos instancias consumidoras.
- Implementar Outbox para garantizar consistencia entre producto y evento.
- Crear el dashboard visual del panel admin.
