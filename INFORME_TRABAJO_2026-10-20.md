# Informe de trabajo - PC Mania

**Fecha:** 20 de octubre de 2026  
**Repositorio:** `pc-mania-unificado`  
**Estado al cierre:** cambios publicados y repositorio limpio

## 1. Objetivo de la sesión

Continuar la implementación de la Evaluación Parcial 2 de DSY1107, verificando
la autenticación Cognito/JWT y conectando las operaciones reales de productos
del backend con el microservicio de mensajería AWS.

## 2. Estado inicial relevante

La arquitectura del proyecto quedó separada en:

```text
pc-mania-backend      Productos, Cognito y JWT
pc-mania-mensajeria   SNS, SQS, DLQ, consumidores y administración
pc-mania-tienda       Frontend de cliente
pc-mania-admin        Frontend administrativo
```

El flujo de mensajería implementado es:

```text
Productor
  -> SNS
  -> suscripciones con filtros
  -> SQS
  -> consumidor
  -> ACK/DeleteMessage
  -> DLQ si se agotan los reintentos
```

## 3. Trabajo realizado

### 3.1 Diagnóstico del catálogo

La tienda mostraba `Failed to fetch` porque el backend no estaba levantado en
el puerto `8080`. Se comprobó:

```text
http://localhost:5173       respondió 200
http://localhost:8080       no estaba disponible
```

Después de iniciar el backend con las variables Cognito:

```text
GET /health          -> 200
GET /api/productos   -> 401 sin token
```

El `401` confirmó que la seguridad del catálogo estaba funcionando. La tienda
mantiene correctamente:

```env
VITE_API_URL=http://localhost:8080
```

### 3.2 Verificación del token de la tienda

Se decodificó localmente el payload del access token de la tienda. El token
contenía:

```json
{
  "token_use": "access",
  "client_id": "client-id-de-la-tienda",
  "scope": "openid profile pcmania-api/read email"
}
```

Conclusión:

- El token era válido.
- El token pertenecía al App Client de la tienda.
- Tenía permiso de lectura.
- No tenía permiso de escritura.

El `403` al intentar publicar eventos desde la tienda fue correcto. No se
agregó `pcmania-api/write` a la tienda, porque la tienda debe permanecer como
cliente de solo lectura.

### 3.3 Protección del microservicio de mensajería

Se implementó seguridad Cognito/JWT en `pc-mania-mensajeria`.

Se agregó:

- Spring Security.
- OAuth2 Resource Server.
- Validación de firma JWT.
- Validación de `issuer`.
- Validación de expiración.
- Validación de `token_use=access`.
- Validación de `client_id`.
- Conversión del claim `scope` a authorities de Spring Security.

Reglas:

| Recurso | Permiso |
|---|---|
| `/health` | Público |
| `POST /api/messaging/events` | `pcmania-api/write` |
| `/api/messaging/admin/**` | Scope administrativo configurado |
| Resto de endpoints | Requiere autenticación |

Por compatibilidad temporal, el scope administrativo puede coincidir con
`pcmania-api/write`. Para la configuración final se recomienda separar:

```text
pcmania-api/write
pcmania-api/admin
```

Pruebas implementadas:

- `/health` sin token responde correctamente.
- Administración sin token responde `401`.
- Administración con scope insuficiente responde `403`.
- Productor con scope de lectura responde `403`.

### 3.4 Integración de eventos reales de productos

Se conectó `pc-mania-backend` con `pc-mania-mensajeria` mediante HTTP:

```text
pc-mania-backend
  -> POST /api/messaging/events
  -> pc-mania-mensajeria
  -> SNS
```

Eventos integrados:

| Operación en productos | Tipo de evento |
|---|---|
| Crear producto | `product.created` |
| Actualizar producto | `product.updated` |
| Eliminar producto | `product.deleted` |

La publicación se controla con:

```env
MESSAGING_SERVICE_URL=http://localhost:8081
MESSAGING_EVENTS_ENABLED=false
```

Cuando se define:

```powershell
$env:MESSAGING_EVENTS_ENABLED="true"
```

el backend publica los eventos después de ejecutar la operación de producto.

El backend reenvía el header:

```text
Authorization: Bearer <access-token>
```

Así, la mensajería valida el mismo token del usuario administrativo sin
guardar tokens en el código.

### 3.5 Validación real del flujo

Se creó un producto desde el panel administrativo y se observó en la consola
de mensajería:

```text
Notificación procesada: eventId=... type=product.created
```

Esto demostró:

1. El backend creó el producto.
2. El backend publicó `product.created`.
3. SNS recibió y distribuyó el evento.
4. La suscripción de notificaciones lo entregó a SQS.
5. El consumidor leyó el mensaje.
6. El consumidor procesó el evento.
7. El consumidor ejecutó ACK mediante `DeleteMessage`.

La cola apareció vacía al usar el sondeo manual de AWS porque el consumidor ya
había procesado y eliminado el mensaje. Esto es el comportamiento esperado.

## 4. Correcciones y decisiones importantes

### 4.1 El cliente no debe publicar eventos

La tienda solo necesita:

```text
pcmania-api/read
```

No debe tener `pcmania-api/write`, porque un usuario final no debe publicar
eventos ni administrar la mensajería.

Los productores autorizados son:

- `pc-mania-backend`, para eventos de productos.
- `pc-mania-admin`, para operaciones administrativas o pruebas autorizadas.

### 4.2 Los consumidores eliminan mensajes procesados

El consumidor ejecuta el ACK después del procesamiento. Por eso, para observar
un mensaje en la consola SQS se debe:

- Pausar el consumidor antes de publicar, o
- Detener temporalmente el microservicio de mensajería.

### 4.3 El archivo `.env` no se carga automáticamente

Spring Boot no lee automáticamente `.env`. Las variables deben cargarse en la
misma sesión de PowerShell antes de ejecutar Maven.

Ejemplo:

```powershell
Get-Content .env | Where-Object {
  $_ -and -not $_.StartsWith('#')
} | ForEach-Object {
  $pair = $_ -split '=', 2
  if ($pair.Length -eq 2) {
    [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
  }
}
```

## 5. Pruebas ejecutadas

### Microservicio de mensajería

```powershell
.\mvnw.cmd test
```

Resultado:

```text
Tests run: 5
Failures: 0
Errors: 0
BUILD SUCCESS
```

### Backend

```powershell
.\mvnw.cmd test
```

Resultado:

```text
Tests run: 6
Failures: 0
Errors: 0
BUILD SUCCESS
```

## 6. Commits publicados durante el trabajo

```text
bdf2ebb feat: proteger mensajeria con Cognito y JWT
c08afc9 feat: publicar eventos de productos en mensajeria
```

El repositorio quedó limpio al finalizar la sesión.

## 7. Configuración de arranque para continuar

### Mensajería

Desde `pc-mania-mensajeria`, se deben configurar en la terminal:

```powershell
$env:COGNITO_CLIENT_ISSUER="..."
$env:COGNITO_CLIENT_ID="..."
$env:COGNITO_ADMIN_ISSUER="..."
$env:COGNITO_ADMIN_CLIENT_ID="..."
$env:MESSAGING_CONSUMERS_ENABLED="true"

.\mvnw.cmd spring-boot:run
```

Debe aparecer:

```text
Tomcat started on port 8081
Started Application
```

### Backend

Desde `pc-mania-backend`, después de cargar su `.env`:

```powershell
$env:MESSAGING_EVENTS_ENABLED="true"
$env:MESSAGING_SERVICE_URL="http://localhost:8081"

.\mvnw.cmd spring-boot:run
```

## 8. Pendientes para la próxima sesión

Orden recomendado:

1. Probar un fallo controlado con `metadata.forceFailure=true`.
2. Confirmar los tres intentos.
3. Verificar el movimiento real a la DLQ.
4. Reprocesar el mensaje desde la DLQ.
5. Separar `pcmania-api/write` y `pcmania-api/admin`.
6. Mejorar la idempotencia con almacenamiento compartido.
7. Probar dos instancias de consumidores.
8. Revisar métricas de CloudWatch y antigüedad de mensajes.
9. Considerar Outbox para evitar producto guardado sin evento.
10. Implementar el dashboard visual al final.

## 9. Seguridad

No incluir en commits ni compartir:

- Access tokens.
- Contraseñas.
- Secretos de App Clients.
- Credenciales AWS.
- Archivos `.env`.

Los tokens que aparecieron durante las pruebas deben considerarse temporales y
no deben conservarse en archivos versionados.
