# HU-01 - Token firmado con expiración y claims mínimos

## Fuente
- Historia de usuario: [HU-01.md](./HU-01.md). Veredicto: usable con ajustes.
- No hay issue ni hallazgo aprobado adicional.

## Contexto actual
- `POST /auth/login` devuelve `token`, `expiresIn` y `user`.
- La implementación firma con HMAC y lee `AUTH_TOKEN_SECRET`.

## Problema confirmado
- El token debe incluir los claims mínimos `sub`, `client`, `iat` y `exp`.
- `expiresIn` debe ser coherente con `exp - now` dentro de una tolerancia documentada.
- Hay un secreto por defecto y riesgo de registrar tokens o secretos en logs.

## Comportamiento a preservar
- La forma de la respuesta de login: `token`, `expiresIn` y `user`.
- Las credenciales válidas deben seguir autenticando.

## Alcance
- Claims `sub`, `client`, `iat` y `exp` en el token.
- Origen y validación de `client`, reflejado en el token.
- Comprobación de `expiresIn` frente a `exp - now`.
- Tratamiento del secreto por defecto.
- Redacción de logs de token y secreto.
- Pruebas de login válido, credenciales inválidas y token expirado.
- Documento de decisión HMAC frente a librería JWT.

## Fuera de alcance
- Refresh tokens, revocación, rotación de secretos y roles (no mencionados en la HU).

## Criterios de aceptación
- Un login válido produce un token con `sub`, `client`, `iat` y `exp`.
- `|expiresIn - (exp - now)|` está dentro de la tolerancia T, pendiente de definir.
- Hay pruebas que pasan para login válido, credenciales inválidas y token expirado.
- Los logs capturados no contienen el token ni el valor de `AUTH_TOKEN_SECRET`.
- La decisión HMAC frente a librería JWT queda documentada.

## Contratos afectados/protegidos
- `POST /auth/login`: forma de la respuesta (`token`, `expiresIn`, `user`).

## Riesgos, supuestos y preguntas abiertas
- **Supuestos:** HMAC y `AUTH_TOKEN_SECRET` ya existen; el endpoint ya devuelve los tres campos.
- **Riesgos:** secreto por defecto fuera de laboratorio; logs con datos sensibles; `client` no validado; desalineación entre `expiresIn` y `exp`.
- **Preguntas abiertas:** ver [HU-01-spec-checklist.md](./HU-01-spec-checklist.md), sección 9.
