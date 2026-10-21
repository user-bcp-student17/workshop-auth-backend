# Proposal

Referencia: HU-01 ([HU-01.md](../../../HU-01.md), "Token firmado con expiracion y claims minimos"). No existe un Issue de GitHub con ese título en `user-bcp-student17/workshop-auth-backend`; el Issue #9 ("MCP local - write permission test") no es fuente de este cambio.

## Why

El contrato documentado en [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md) define que `POST /auth/login` devuelve un JWT firmado con `sub`, `role`, `iat` y `exp`, pero trata `client` como opcional y sin validar. HU-01 exige que el token identifique al usuario y al cliente, que su vigencia sea coherente con `expiresIn`, y que no se registren tokens ni secretos. Sin estos requisitos verificables, un token puede emitirse sin cliente, con una vigencia inconsistente o firmado con el secreto por defecto.

## What Changes

- `client` se acepta opcional durante una fase de transición: si falta, es `null` o está en blanco, se usa `auth.client.default` (por defecto `angular-workshop`) y se registra un aviso sin datos sensibles. Así no cambia el contrato para los clientes actuales.
- `auth.client.required=true` activa el modo estricto: `client` debe venir, no ser `null`, ni vacío ni solo espacios, y la respuesta es `400` con `Client is required`.
- El JWT incluye siempre el claim `client` con el valor resuelto, junto a `sub`, `iat` y `exp`.
- `expiresIn` debe coincidir con `exp - now` dentro de una tolerancia documentada de 5 segundos (supuesto a confirmar).
- El secreto por defecto no se usa fuera del modo laboratorio, que debe activarse de forma explícita y documentada.
- Ningún log contiene tokens, secretos ni contraseñas.
- Se añaden pruebas de login válido, credenciales inválidas y token expirado.

## Capabilities

### New Capabilities
- `auth-token`: emisión de JWT en el login, con claims mínimos, vigencia coherente con `expiresIn`, configuración del secreto y redacción de datos sensibles en logs.

### Modified Capabilities
- Ninguna. `openspec/specs/` no contiene specs principales todavía.

## Comportamiento que debe preservarse

Según [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md), el cambio no altera:

- Mensajes y códigos de error `400` para `Request is null`, `Username is required` y `Password is required`.
- Error `401` con `Invalid user <username> or password` para credenciales inválidas.
- Error `500` con `message` y `exception`.
- Forma de la respuesta `200`: `token`, `expiresIn` (3600), `user` (`username`, `displayName`, `role`) y `debug` (`authenticated-with-legacy-flow`).
- Algoritmo HS256 y `AUTH_TOKEN_SECRET` de al menos 32 bytes.
- La contraseña y su hash nunca aparecen en la respuesta.
- Respuestas de `GET /q/health` (fuera de alcance).

## Fuera de alcance

- Decidir entre HMAC y una librería JWT. Se documentará en `design.md` cuando se cree; no cambia el comportamiento observable.
- Validar `client` contra una lista de clientes registrados. HU-01 no define esa lista.
- Refresh tokens, revocación, rotación de secretos y autorización de otros endpoints.
- Cambios en `GET /q/health`.
- Cambios en los mensajes `400`, `401`, `415` y `500` existentes, salvo el nuevo error por `client` ausente o vacío.

## Preguntas abiertas

- Mensaje exacto del error `400` por `client` en modo estricto (propuesto: `Client is required`).
- Forma de verificar la expiración: el backend no tiene un endpoint que consuma el token; la prueba de expiración usará un verificador con el mismo secreto.
- Cómo se activa el modo laboratorio que permite el secreto por defecto.

## Impact

- Código: `AuthResource`, `AuthService`, `TokenService`, `LoginRequest` y la configuración de `AUTH_TOKEN_SECRET` en `application.properties`.
- Pruebas: `AuthResourceTest`, `AuthResourceUnitTest` y `TokenServiceTest`.
- Documentación: [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md) debe actualizarse para reflejar `client` obligatorio.
- Clientes del login: no requieren cambios en la fase de transición. Deben enviar `client` antes de activar `auth.client.required=true`.
