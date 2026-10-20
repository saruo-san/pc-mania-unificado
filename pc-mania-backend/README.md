# PC Mania Backend

API REST para administrar el catálogo de productos de PC Mania.

## Tecnologías

- Java 17
- Spring Boot 4.1.1
- Maven
- Spring MVC y Bean Validation

## Funcionalidades

- CRUD de productos en `/api/productos`.
- Validación de nombre, marca, categoría, precio y stock.
- Manejo centralizado de errores para productos inexistentes y datos inválidos.
- CORS habilitado para los frontends locales en los puertos 5173 y 5174.
- Carga de tres productos de ejemplo al iniciar.

## Endpoints

| Método | Ruta | Descripción |
| --- | --- | --- |
| `GET` | `/api/productos` | Lista todos los productos |
| `GET` | `/api/productos/{id}` | Obtiene un producto por ID |
| `POST` | `/api/productos` | Crea un producto y responde `201` |
| `PUT` | `/api/productos/{id}` | Actualiza un producto |
| `DELETE` | `/api/productos/{id}` | Elimina un producto y responde `204` |

Los campos `nombre`, `marca` y `categoria` son obligatorios. El `precio` debe ser mayor que cero y el `stock` no puede ser negativo.

## Estructura principal

- `Application.java`: punto de entrada de Spring Boot.
- `controller/ProductoController.java`: endpoints REST.
- `controller/GlobalExceptionHandler.java`: respuestas de error.
- `model/Producto.java`: modelo y reglas de validación.
- `service/ProductoService.java`: lógica de negocio e inventario.
- `config/CorsConfig.java`: configuración CORS.
- `src/test`: prueba de carga del contexto Spring.

## Ejecución en Windows

Desde esta carpeta:

```powershell
.\mvnw.cmd spring-boot:run
```

La API queda disponible en `http://localhost:8080`.

Para probar y compilar:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean package
```

## Estado actual

Los productos se almacenan en memoria mediante un `ConcurrentHashMap`, por lo que los cambios se pierden al reiniciar. Actualmente no hay una base de datos configurada.

Los frontends obtienen tokens de Cognito y los envían al API, pero este backend todavía no implementa filtros JWT para validar directamente esos tokens. La autenticación completa del API debe agregarse antes de un despliegue productivo.
