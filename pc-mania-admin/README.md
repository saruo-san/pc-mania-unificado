# PC Mania Admin

Panel administrativo web para gestionar el inventario de PC Mania.

## Tecnologías

- React 19
- TypeScript
- Vite
- AWS Amplify y Amazon Cognito

## Funcionalidades

- Inicio y cierre de sesión con Cognito.
- Solicitud del alcance OAuth `pcmania-api/write`.
- Formulario para agregar productos con nombre, marca, categoría, precio y stock.
- Envío del token de acceso en la cabecera `Authorization`.
- Confirmación de los productos agregados durante la sesión.

## Archivos principales

- `src/App.tsx`: sesión, formulario y vista del panel.
- `src/api.ts`: tipos de producto y petición `POST /api/productos`.
- `src/config.ts`: lectura y validación de variables de entorno.
- `src/main.tsx`: configuración de Amplify e inicio de React.
- `src/App.css`: estilos de la aplicación.

## Configuración

Copia `.env.example` como `.env` y completa los datos de Cognito y del backend:

```env
VITE_COGNITO_USER_POOL_ID=
VITE_COGNITO_CLIENT_ID=
VITE_COGNITO_DOMAIN=
VITE_REDIRECT_SIGN_IN=http://localhost:5174/
VITE_REDIRECT_SIGN_OUT=http://localhost:5174/
VITE_API_URL=http://localhost:8080
```

No publiques el archivo `.env` con valores propios del entorno.

## Instalación y ejecución

```bash
npm install
npm run dev
```

La aplicación se ejecuta normalmente en `http://localhost:5174`.

Otros comandos:

```bash
npm run build
npm run lint
npm run preview
```

El backend debe estar ejecutándose en `http://localhost:8080` para poder agregar productos.
