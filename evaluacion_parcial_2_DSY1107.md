# Evaluación Parcial N.º 2: Encargo y Presentación
## Mensajería asíncrona con Amazon SNS y SQS

**Asignatura:** Desarrollo Cloud Native I (DSY1107)  
**Escuela:** Informática y Telecomunicaciones  
**Año:** 2026  
**Tiempo asignado:** 2 semanas  
**Ponderación:** Encargo 12% · Presentación 18%  
**Presentaciones desde:** 21 de octubre de 2026

---

## 1. Instrucciones generales

### Descripción

En esta etapa se debe escalar el proyecto desarrollado en el Parcial 1, incorporando una capa de mensajería asíncrona con Amazon SNS y SQS para gestionar tareas como notificaciones, generación de tickets o documentos. También se deben corregir todos los pendientes y observaciones del Parcial 1.

El objetivo es demostrar que la mensajería desacoplada funciona, resiste fallos y puede administrarse sin afectar la lógica existente del sistema.

### Distribución de la evaluación

| Evaluación | Tipo | Ponderación | Modalidad |
|---|---|---:|---|
| Evaluación Parcial N.º 2 – Encargo | Encargo | 12% | En parejas |
| Evaluación Parcial N.º 2 – Presentación | Presentación | 18% | Individual |

### Plazo y entrega

- **Fecha límite:** miércoles 21 de octubre de 2026 a las **14:29**.
- Los commits posteriores a esa hora no serán considerados.
- Las presentaciones comienzan el 21 de octubre.
- Entregar el enlace del repositorio GitHub en AVA y enviar una copia al correo del docente.
- Invitar como colaborador de GitHub a `ab.silvaf@profesor.duoc.cl`.

### Requisitos que se mantienen del Parcial 1

- Integración con la base de datos cloud.
- Validación del JWT emitido por el proveedor de identidad (Cognito).
- Inicio de sesión del frontend mediante Cognito.
- Consumo del backend a través del API Manager.

### Requisitos del encargo

- El código de los componentes backend debe compilar, seguir buenas prácticas y responder a pruebas básicas.
- El frontend en React debe estar completo, modular, sin errores de compilación y con vistas funcionales.
- Implementar **un tópico SNS y tres colas SQS**, cada una con su propia DLQ y `Redrive Policy`.
- Usar dos patrones de enrutamiento:
  - **Fanout:** un mensaje se distribuye a varias colas.
  - **Filtros de suscripción:** entrega selectiva según atributos o criterios del mensaje.
- Los microservicios deben incluir productores y consumidores conectados a las colas. Los consumidores se organizan por dominio funcional.
- Los consumidores deben:
  - Confirmar los mensajes explícitamente.
  - Reintentar errores recuperables.
  - Enviar errores no recuperables a la DLQ.
  - Tolerar mensajes duplicados.
  - Registrar en logs los mensajes no entregados, incluyendo **id, intento y causa**.
- Centralizar los nombres de colas, tópicos y suscripciones en un archivo de configuración o clase de configuración.
- Declarar la topología en clases de configuración separadas de la lógica de negocio.
- Implementar un microservicio administrador con API REST para:
  - Crear y eliminar colas y tópicos.
  - Gestionar suscripciones.
  - Consultar el estado de las colas.
  - Pausar y reanudar un consumidor.
  - Reprocesar y purgar la DLQ.
- Encapsular la lógica administrativa en un servicio dedicado.
- Definir políticas de retención y limpieza por cola, justificarlas y configurar una alarma de CloudWatch sobre las DLQ.
- Subir a cada repositorio solo los archivos correspondientes a su tecnología y configurar los `.gitignore`.
- **No subir credenciales de AWS.**

### Opcionales (*nice to have*)

- Diagrama de flujo del recorrido de un mensaje hasta su confirmación, reintento o DLQ.
- Diagrama superficial de arquitectura que incluya frontend, API Manager, Cognito, microservicios, tópico, colas, DLQ, administrador y CloudWatch.

### Aspectos formales del repositorio

- Un único repositorio GitHub con:
  - Microservicios backend en Java con Spring Boot.
  - Microservicio administrador.
  - Frontend en React.
  - `README`.
- **Nombre:** `nombreproyecto_apellido1_apellido2`.
- Cada commit debe quedar asociado al nombre y apellido de quien lo realiza.
- Ambos integrantes deben realizar commits.

### Materiales y herramientas

- Materiales y contenidos académicos del curso, incluidas guías y especificaciones técnicas.
- Cuenta de AWS Academy (Learner Lab).
- Computadores disponibles en el taller de proyectos.

---

## 2. Pauta de evaluación del encargo

### Niveles de logro

| Nivel | Porcentaje | Descripción general |
|---|---:|---|
| Muy buen desempeño | 100% | Logra todos los aspectos evaluados de forma destacada. |
| Buen desempeño | 80% | Alto desempeño, con pequeñas omisiones, dificultades o errores. |
| Desempeño aceptable | 60% | Cumple los elementos básicos, pero presenta omisiones o errores. |
| Desempeño incipiente | 30% | Presenta omisiones o errores importantes que impiden demostrar el logro básico. |
| Desempeño no logrado | 0% | Ausencia del indicador o desempeño incorrecto. |

### Indicadores del encargo

| N.º | Indicador de evaluación | Pond. |
|---:|---|---:|
| 1 | Centraliza en `application.yml`, `application.properties` o una clase de configuración los nombres de colas, tópicos y suscripciones. | 12% |
| 2 | Declara la topología de mensajería —tópico, colas, DLQ con `Redrive Policy` y suscripciones— en clases de configuración o plantillas. | 13% |
| 3 | Separa la configuración de mensajería de la lógica de negocio. | 10% |
| 4 | Implementa consumidores con `@SqsListener` u otra anotación equivalente y los agrupa por dominio funcional. | 15% |
| 5 | Maneja confirmación explícita, ACK, reintentos, DLQ, duplicados y logs con id, intento y causa. | 20% |
| 6 | Expone endpoints REST claros para administrar colas, tópicos, suscripciones, estado, consumidores y DLQ. | 13% |
| 7 | Encapsula las operaciones administrativas en un servicio dedicado. | 17% |
| | **Total** | **100%** |

#### Qué se espera para obtener el mejor nivel

1. **Configuración centralizada:** todos los nombres se definen en un único lugar; no quedan valores sueltos en clases de negocio.
2. **Topología declarada:** cada caso de uso tiene declarados su tópico, colas, DLQ, `Redrive Policy` y suscripciones, con rutas identificadas y documentadas.
3. **Separación de responsabilidades:** la configuración vive en clases específicas; los servicios de negocio usan componentes ya configurados y no manejan directamente el SDK en controladores o servicios de dominio.
4. **Consumidores por dominio:** consumidores claros, bien organizados y basados en `@SqsListener` o equivalente.
5. **Manejo de errores:** confirmación explícita y coherente; reintentos para errores recuperables; DLQ para errores no recuperables; tolerancia a duplicados; logs útiles y documentación del flujo.
6. **API REST administrativa:** rutas, verbos HTTP y nombres de recursos coherentes y documentados.
7. **Servicio administrativo dedicado:** centraliza creación, eliminación, consulta de estado, pausa y reanudación de consumidores, reprocesamiento y purga de DLQ.

---

## 3. Presentación

### Descripción

La presentación es presencial y se realiza frente al docente. Aunque el proyecto se desarrolle en pareja, la evaluación es **individual**. Cada estudiante debe fundamentar la solución, justificar el diseño y las tecnologías, demostrar el funcionamiento y responder las preguntas del docente.

Si se requiere tiempo adicional, la evaluación puede extenderse a la semana siguiente.

### Demostraciones obligatorias

- [ ] Mostrar en la consola AWS el tópico SNS, las tres colas SQS, sus DLQ y las suscripciones con sus parámetros.
- [ ] Demostrar los dos patrones de enrutamiento: fanout y filtros de suscripción.
- [ ] Mostrar un flujo exitoso y otro fallido que llegue a la DLQ, incluyendo logs de reintentos.
- [ ] Ejecutar dos instancias de un consumidor sobre la misma cola; detener una y demostrar que el sistema continúa sin perder mensajes.
- [ ] Operar el microservicio administrador:
  - [ ] Crear y eliminar colas y tópicos.
  - [ ] Consultar el estado de las colas.
  - [ ] Pausar y reanudar un consumidor.
  - [ ] Reprocesar la DLQ.
- [ ] Mostrar métricas de CloudWatch:
  - [ ] Mensajes disponibles.
  - [ ] Mensajes en proceso.
  - [ ] Antigüedad del mensaje más antiguo.
  - [ ] Mensajes en la DLQ.
- [ ] Mostrar la alarma de CloudWatch y explicar su relevancia.
- [ ] Mostrar las políticas de retención y limpieza y justificar sus valores.
- [ ] Demostrar la integración completa: el frontend inicia sesión con Cognito, el JWT pasa por el API Manager al backend y el backend publica los mensajes.

### Aspectos formales

- Duración: **entre 5 y 10 minutos**.
- La exposición debe tener orden, hilo conductor y lenguaje técnico.
- Los servicios de AWS no tienen código entregado: su creación y configuración deben evidenciarse en pantalla con los detalles relevantes.
- Se recomienda preparar una lista de puntos para aprovechar el tiempo.

---

## 4. Tabla de especificaciones de la presentación

### Resultado de aprendizaje

**RA2:** Desarrolla soluciones utilizando colas para trabajar con mensajes asíncronos y responder a problemas en que la carga de trabajo es demasiado grande para utilizar un método de trabajo sincrónico.

### Indicadores de logro y evaluación

| Indicador de logro | Pond. IL | Indicador de evaluación | Pond. IE |
|---|---:|---|---:|
| **IL 2.1:** Configura un componente que utilice tecnología de cola para trabajar con mensajes asíncronos. | 30% | Muestra en AWS el tópico, las colas y las suscripciones operativas con sus parámetros. | 15% |
| | | Demuestra disponibilidad con dos instancias de un consumidor sobre la misma cola. | 15% |
| **IL 2.2:** Implementa una solución con mensajes asíncronos para los problemas planteados. | 40% | Utiliza tres colas para diferentes funcionalidades. | 7% |
| | | Cada cola tiene una DLQ. | 9% |
| | | Utiliza fanout y filtros de suscripción. | 6% |
| | | Integra microservicios con las colas para escribir y leer mensajes. | 10% |
| | | Integra microservicios con las DLQ y registra en logs los mensajes no entregados. | 8% |
| **IL 2.3:** Administra las colas para distintos casos de uso. | 30% | Opera el microservicio administrador: crear/eliminar colas y tópicos, consultar estado, pausar/reanudar y reprocesar DLQ. | 12% |
| | | Explica las métricas de monitoreo y la alarma. | 8% |
| | | Implementa políticas de retención y limpieza. | 10% |

---

## 5. Pauta de evaluación de la presentación

| N.º | Indicador | Pond. | Evidencia para el mejor nivel |
|---:|---|---:|---|
| 1 | Muestra el tópico, las colas y las suscripciones operativas en AWS. | 15% | Muestra los recursos y explica parámetros como `visibility timeout`, retención y `raw message delivery`. |
| 2 | Demuestra disponibilidad con dos instancias de un consumidor sobre la misma cola. | 15% | Muestra el reparto, detiene una instancia y demuestra continuidad sin pérdida de mensajes, explicando el mecanismo. |
| 3 | Utiliza tres colas para funcionalidades distintas. | 7% | Demuestra las tres colas y justifica la separación por funcionalidad. |
| 4 | Cada cola tiene una DLQ. | 9% | Muestra las tres DLQ, demuestra un mensaje que llega a una y explica `maxReceiveCount` y la retención. |
| 5 | Utiliza fanout y filtros de suscripción. | 6% | Demuestra ambos patrones y explica cuándo conviene usar cada uno. |
| 6 | Integra microservicios productores y consumidores. | 10% | Demuestra el flujo completo con evidencia en logs y en la consola AWS. |
| 7 | Registra en logs los mensajes no entregados. | 8% | Muestra id, intento y causa, relacionando el log con el mensaje almacenado en la DLQ. |
| 8 | Opera el microservicio administrador. | 12% | Ejecuta todas las operaciones solicitadas y muestra sus efectos en AWS. |
| 9 | Explica métricas y alarma de CloudWatch. | 8% | Explica las métricas y la alarma, e indica qué decisión tomaría ante cada situación. |
| 10 | Implementa políticas de retención y limpieza. | 10% | Justifica la retención de cada cola y DLQ, la purga y cómo se evita el crecimiento descontrolado. |
| | **Total** | **100%** | |

### Detalles esperados por indicador

- **Recursos AWS:** mostrar tópico, colas y suscripciones en funcionamiento, junto con sus parámetros.
- **Alta disponibilidad:** mostrar dos instancias consumiendo de la misma cola; al detener una, la otra debe continuar procesando mensajes.
- **Colas funcionales:** cada una debe responder a una funcionalidad diferente y tener una justificación clara.
- **DLQ:** cada cola principal debe tener su DLQ y `Redrive Policy`; explicar el máximo de recepciones antes de mover el mensaje.
- **Enrutamiento:** demostrar fanout y entrega selectiva mediante filtros.
- **Integración:** mostrar productores y consumidores de extremo a extremo.
- **Logs de errores:** permitir identificar el mensaje, el intento y la causa del fallo.
- **Administrador:** mostrar en vivo la creación y eliminación de recursos, consulta de estado, pausa/reanudación y reprocesamiento de la DLQ.
- **CloudWatch:** explicar mensajes disponibles, en proceso, antigüedad del mensaje más antiguo y mensajes en DLQ, además de la alarma.
- **Retención y limpieza:** explicar la política por cola y DLQ y cómo evita el crecimiento descontrolado.

---

## 6. Checklist final antes de entregar

### Código y repositorio

- [ ] Proyecto del Parcial 1 corregido según las observaciones.
- [ ] Backend Java/Spring Boot compila y responde a pruebas básicas.
- [ ] Frontend React modular, funcional y sin errores de compilación.
- [ ] Integración con base de datos cloud.
- [ ] Login con Cognito y validación del JWT en backend.
- [ ] Consumo del backend a través del API Manager.
- [ ] Un tópico SNS y tres colas SQS.
- [ ] Una DLQ por cada cola, con `Redrive Policy`.
- [ ] Fanout y filtros de suscripción configurados.
- [ ] Productores y consumidores por dominio funcional.
- [ ] Confirmación explícita, reintentos, manejo de errores no recuperables e idempotencia.
- [ ] Logs de mensajes fallidos con id, intento y causa.
- [ ] Nombres centralizados y topología declarada en clases de configuración.
- [ ] Microservicio administrador con todos los endpoints solicitados.
- [ ] Lógica administrativa encapsulada en un servicio dedicado.
- [ ] Retención, limpieza y alarma de CloudWatch configuradas.
- [ ] `.gitignore` configurado para cada tecnología.
- [ ] No se subieron credenciales de AWS.
- [ ] `README` con instrucciones y documentación.
- [ ] Ambos integrantes realizaron commits con su nombre y apellido.
- [ ] Repositorio con el nombre `nombreproyecto_apellido1_apellido2`.

### Presentación

- [ ] Ensayar una exposición de 5 a 10 minutos.
- [ ] Tener AWS y el proyecto listos para ejecutar.
- [ ] Probar el flujo exitoso y el flujo fallido hasta la DLQ.
- [ ] Probar las dos instancias de consumidor y detener una.
- [ ] Probar todos los endpoints del administrador.
- [ ] Preparar CloudWatch, las métricas y la alarma.
- [ ] Preparar una explicación breve de los parámetros y políticas configuradas.
- [ ] Entregar el enlace en AVA y enviarlo al correo del docente.
- [ ] Invitar a `ab.silvaf@profesor.duoc.cl` como colaborador.
- [ ] Verificar que el último commit válido sea anterior o igual al **21 de octubre de 2026 a las 14:29**.

---

**Nota:** Este Markdown organiza el contenido proporcionado. Conviene verificar los detalles de implementación y los parámetros concretos con las guías del curso y la configuración real del proyecto.
