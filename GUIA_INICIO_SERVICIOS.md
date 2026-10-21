# Guía completa de inicio de PC Mania

Esta guía inicia los cuatro módulos locales sin guardar ni mostrar credenciales
en el repositorio. Los comandos están escritos para PowerShell en Windows.

## 1. Requisitos

### 1.1 Programas que deben instalarse

| Programa | Versión recomendada | Para qué se utiliza |
|---|---|---|
| Git | versión actual | Clonar y actualizar el repositorio |
| JDK | Java 17 | Ejecutar backend y mensajería |
| Node.js | 20 LTS o superior | Ejecutar los frontends React/Vite |
| npm | Incluido con Node.js | Instalar dependencias y ejecutar frontends |
| AWS CLI | Versión 2 | Validar la cuenta y consultar AWS |
| Navegador | Chrome, Edge o Firefox actualizado | Usar admin, tienda y Cognito |

Maven no necesita instalarse por separado: backend y mensajería incluyen
`mvnw.cmd`, que descarga y utiliza Maven Wrapper.

Comprueba las instalaciones:

```powershell
git --version
java -version
node --version
npm --version
aws --version
```

El JDK debe incluir `javac`, no solamente un runtime de Java.

### 1.2 Dependencias del proyecto

Las dependencias se descargan automáticamente desde los manifiestos al
ejecutar `.\mvnw.cmd spring-boot:run` o `npm install`. No se deben copiar
manualmente dentro del repositorio.

Backend y mensajería utilizan Java 17 y Spring Boot `4.1.1`. El backend
incluye Spring Web MVC, Spring Security, OAuth2 Resource Server, Validation y
Jackson. Mensajería agrega AWS SDK SNS, SQS y CloudWatch, además de las
dependencias de pruebas.

El panel admin y la tienda son aplicaciones React con TypeScript y Vite.
Utilizan React, React DOM, AWS Amplify para Cognito, TypeScript, Vite y ESLint.
Cada frontend tiene su propio `node_modules`, por lo que se ejecuta `npm
install` dentro de cada carpeta.

Versiones relevantes:

```text
Java: 17
Spring Boot: 4.1.1
AWS SDK de mensajería: 2.31.63
React: 19.2.8
AWS Amplify: 6.20.0
Vite: 8.2.2
TypeScript: 6.0.2
```

### 1.3 Servicios externos y configuración

Además de los programas locales, se necesita:

- Una cuenta AWS con acceso a SNS, SQS y CloudWatch.
- Un User Pool de Cognito.
- Un App Client para cliente y otro para administrador.
- Los scopes `pcmania-api/read` y `pcmania-api/write`.
- Usuarios Cognito creados para cliente y administrador.
- Recursos AWS de PC Mania en `us-east-1`.
- Credenciales AWS mediante perfil local o variables temporales.
- Acceso local al repositorio.

Verifica AWS sin mostrar secretos:

```powershell
aws sts get-caller-identity --region us-east-1
```

Si usas credenciales temporales, cárgalas únicamente en las terminales que
ejecuten AWS CLI o el microservicio de mensajería:

```powershell
$env:AWS_ACCESS_KEY_ID="<ACCESS_KEY_ID>"
$env:AWS_SECRET_ACCESS_KEY="<SECRET_ACCESS_KEY>"
$env:AWS_SESSION_TOKEN="<SESSION_TOKEN>"
$env:AWS_DEFAULT_REGION="us-east-1"
```

No pegues valores reales en el chat ni los guardes en código.

## 2. Terminal 1: microservicio de mensajería

Abre una terminal PowerShell y ejecuta:

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-mensajeria

$env:COGNITO_CLIENT_ISSUER="<ISSUER_CLIENTE>"
$env:COGNITO_CLIENT_ID="<CLIENT_ID_CLIENTE>"
$env:COGNITO_ADMIN_ISSUER="<ISSUER_ADMIN>"
$env:COGNITO_ADMIN_CLIENT_ID="<CLIENT_ID_ADMIN>"
$env:MESSAGING_CONSUMERS_ENABLED="true"
$env:AWS_REGION="us-east-1"

.\mvnw.cmd spring-boot:run
```

La variable `MESSAGING_CONSUMERS_ENABLED` debe definirse en esta terminal,
porque afecta al proceso de mensajería, no al backend.

Espera:

```text
Tomcat started on port 8081
Started Application
```

Comprueba el servicio desde otra terminal:

```powershell
Invoke-WebRequest http://localhost:8081/health -UseBasicParsing
```

Debe responder `200`.

## 3. Crear o verificar la topología

La topología ya existe en AWS, pero se puede consultar con AWS CLI:

```powershell
aws sns list-topics --region us-east-1
aws sqs list-queues --region us-east-1
aws cloudwatch describe-alarms --region us-east-1
```

Para administrar la topología desde el panel, inicia sesión como administrador.
El endpoint de creación es:

```text
POST http://localhost:8081/api/messaging/admin/topology
```

No es necesario ejecutarlo en cada inicio si los recursos ya existen.

## 4. Terminal 2: backend de productos

Abre otra terminal:

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-backend

$env:COGNITO_CLIENT_ISSUER="<ISSUER_CLIENTE>"
$env:COGNITO_CLIENT_ID="<CLIENT_ID_CLIENTE>"
$env:COGNITO_ADMIN_ISSUER="<ISSUER_ADMIN>"
$env:COGNITO_ADMIN_CLIENT_ID="<CLIENT_ID_ADMIN>"
$env:MESSAGING_EVENTS_ENABLED="true"
$env:MESSAGING_SERVICE_URL="http://localhost:8081"

.\mvnw.cmd spring-boot:run
```

Espera:

```text
Tomcat started on port 8080
Started Application
```

Comprueba:

```powershell
Invoke-WebRequest http://localhost:8080/health -UseBasicParsing
```

El backend no necesita credenciales AWS: publica eventos mediante HTTP al
microservicio y reenvía el token Bearer del administrador.

## 5. Terminal 3: panel administrador

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-admin
npm install
npm run dev
```

Abre:

```text
http://localhost:5174
```

El `.env` local debe contener, como mínimo:

```text
VITE_API_URL=http://localhost:8080
VITE_MESSAGING_API_URL=http://localhost:8081
VITE_REDIRECT_SIGN_IN=http://localhost:5174/
VITE_REDIRECT_SIGN_OUT=http://localhost:5174/
```

Inicia sesión con el usuario administrador. El panel necesita el scope
`pcmania-api/write`, que es la decisión actual del proyecto.

## 6. Terminal 4: tienda

```powershell
Set-Location C:\Users\Bruno\Downloads\pc-mania\pc-mania-tienda
npm install
npm run dev
```

Abre:

```text
http://localhost:5173
```

Inicia sesión con el usuario cliente. La tienda debe utilizar solamente
`pcmania-api/read`.

## 7. Orden de apagado

Presiona `Ctrl + C` en cada terminal, en cualquier orden. Para una nueva
sesión, vuelve a ejecutar las variables de entorno porque solo viven en la
terminal actual.

## 8. Problemas frecuentes

| Síntoma | Revisión |
|---|---|
| `Failed to fetch` en operaciones de colas | Mensajería iniciada y CORS aplicado; revisar `8081` |
| Producto se guarda pero no hay evento | Backend iniciado con `MESSAGING_EVENTS_ENABLED=true` |
| Mensaje queda visible | Consumidor pausado o `MESSAGING_CONSUMERS_ENABLED=false` |
| `401` | Token ausente, expirado o issuer incorrecto |
| `403` | Scope incorrecto; tienda no debe tener `write` |
| AWS no responde | Ejecutar `aws sts get-caller-identity` y revisar región |
