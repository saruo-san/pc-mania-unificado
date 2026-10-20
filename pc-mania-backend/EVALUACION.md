# Duoc UC — Evaluación Parcial N° 1
**Subdirección de Diseño Curricular e Instruccional (2025)**  
*Encargo | Estudiante*

---

## Ficha de la Asignatura

| Campo | Detalle |
| :--- | :--- |
| **Sigla** | DSY1107 |
| **Nombre Asignatura** | Desarrollo Cloud Native I |
| **Tiempo Asignado** | 5 horas pedagógicas |
| **% Ponderación** | 16% |
| **Semana inicio EFT** | — |

---

## 1. Instrucciones Generales

### Descripción del Encargo
- En esta primera etapa deberás diseñar y desarrollar la **arquitectura base del sistema Pedidos360**, integrando:
  - **Frontend:** Desarrollado en React con autenticación mediante Amazon Cognito.
  - **Backend:** Desarrollado en Spring Boot, desplegado en instancias EC2 y protegido a través de AWS API Gateway.
- **Objetivo:** Lograr un sistema funcional y seguro que demuestre la correcta comunicación entre componentes y servicios en la nube.

### Ponderación y Modalidad

| Evaluación | Tipo de situación evaluativa | Distribución de porcentajes en el ET | Modalidad |
| :--- | :--- | :--- | :--- |
| **Evaluación Parcial N°1 (16%)** | Encargo | 40% | Parejas |
| **Total** | | **100%** | |

---

### Condiciones de Desarrollo y Entrega
- **Entrega de código:** El encargo corresponde al código fuente de los componentes frontend y backend, entregado vía **GitHub**.
- **Plazo:** Se dispone de **2 semanas** para la elaboración del encargo y de la presentación.
- **Lugar de trabajo:** Se inicia en el Taller de Proyectos (TAITE 7) y se finaliza junto a la preparación de la presentación en tiempo de trabajo personal.

---

### Instrucciones Específicas
- [ ] **Entrega:** Enviar al docente los enlaces de los repositorios de código fuente (frontend y backend).
- [ ] **Calidad Backend:** El código de todos los microservicios debe compilar, seguir buenas prácticas de desarrollo y responder a pruebas básicas.
- [ ] **Calidad Frontend:** El código en React debe estar completo, modular, sin errores de compilación y con vistas funcionales.
- [ ] **Persistencia en la Nube:** La integración con la base de datos cloud debe estar correctamente configurada mediante entidades, repositorios y archivos de propiedades (`application.properties` / `application.yml`).
- [ ] **Seguridad Backend (Filtros JWT):** Incluir filtros que validen el token JWT emitido por Cognito para autorizar las solicitudes entrantes.
- [ ] **Seguridad Frontend (Login Cognito):** Implementar el flujo de autenticación con Cognito y adjuntar el token JWT en las llamadas hacia el backend.
- [ ] **Control de Versiones:** Configurar adecuadamente los archivos `.gitignore` tanto en frontend como en backend para subir exclusivamente los archivos fuente necesarios.

---

### Aspectos Formales
- **Formato:**
  - **Backend:** Varios microservicios construidos en Java con **Spring Boot**.
  - **Frontend:** Aplicación construida en **React**.
  - **Repositorios:** Enlaces públicos o compartidos a GitHub.
- **Medio de entrega:**
  - Registrar los enlaces de los repositorios GitHub en la plataforma **AVA**.
  - Enviar una copia de respaldo al correo institucional del docente dentro del plazo establecido.

### Materiales, Herramientas o Insumos
- Materiales y contenidos académicos del curso (especificaciones técnicas, análisis previos y guías de referencia).
- Estaciones de trabajo del taller de proyectos (**TAITE 7**).

---

## 2. Pauta de Evaluación

### Criterios y Niveles de Logro Generales

| Categoría | % Logro | Descripción |
| :--- | :---: | :--- |
| **Muy buen desempeño** | 100% | Demuestra un desempeño destacado, evidenciando el logro de todos los aspectos evaluados en el indicador. |
| **Buen desempeño** | 80% | Demuestra un alto desempeño del indicador, presentando pequeñas omisiones, dificultades y/o errores. |
| **Desempeño aceptable** | 60% | Demuestra un desempeño competente, evidenciando el logro de los elementos básicos del indicador, pero con omisiones, dificultades o errores. |
| **Desempeño incipiente** | 30% | Presenta importantes omisiones, dificultades o errores en el desempeño, que no permiten evidenciar los elementos básicos del logro del indicador, por lo que no puede ser considerado competente. |
| **Desempeño no logrado** | 0% | Presenta ausencia o incorrecto desempeño. |

---

### Rúbrica Detallada

#### Indicador 1: Frontend & Autenticación (60%)
> **Configura y utiliza correctamente AWS Amplify (Authenticator) en conjunto con React, de manera que el flujo de usuario funcione correctamente y permita obtener todos los tokens necesarios.**

| Nivel | % | Criterio de Evaluación |
| :--- | :---: | :--- |
| **Muy buen desempeño** | 100% | Amplify integrado y operativo. El inicio y cierre de sesión funcionan correctamente. Las rutas protegidas y el interceptor de peticiones (`apiFetch`) operan sin fallas. Se obtienen los tokens necesarios para consumir el API Gateway y se leen roles y scopes desde los claims del token. |
| **Buen desempeño** | 80% | El flujo de autenticación funciona y el sistema consume el API Gateway. Existen pequeños detalles de configuración como fallas leves en rutas protegidas o lectura parcial de roles. |
| **Desempeño aceptable** | 60% | La aplicación autentica pero presenta fallas intermitentes. A veces no adjunta el token o no renueva correctamente los accesos. |
| **Desempeño incipiente** | 30% | El login se muestra pero el token no se adjunta correctamente en las llamadas o la aplicación usa configuraciones incorrectas del IDaaS. |
| **Desempeño no logrado** | 0% | No integra Amplify o no logra autenticar en absoluto. |

---

#### Indicador 2: Backend & Validación de Tokens (40%)
> **Configura correctamente el backend para que, al igual que el API Gateway, pueda validar el token recibido con Cognito y solo permita consumir el endpoint si el token es válido.**

| Nivel | % | Criterio de Evaluación |
| :--- | :---: | :--- |
| **Muy buen desempeño** | 100% | El backend valida `issuer` y `audience` de forma correcta. Verifica la firma del token y su vigencia. Aplica autorización por rol cuando corresponde y responde con códigos de error adecuados. |
| **Buen desempeño** | 80% | La validación es consistente y funcional. Existe algún error muy menor en la autorización o en la forma de entregar mensajes de error. |
| **Desempeño aceptable** | 60% | El backend valida tokens en la mayoría de los endpoints. Algunas rutas no verifican claims críticos como expiración o audience. |
| **Desempeño incipiente** | 30% | El backend solo revisa la presencia del token. No valida firma ni claims relevantes. |
| **Desempeño no logrado** | 0% | No valida JWT o permite el acceso sin autenticación. |

---

**Ponderación Total de la Rúbrica:** `100%`