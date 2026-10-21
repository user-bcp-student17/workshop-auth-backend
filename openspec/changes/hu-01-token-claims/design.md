# Design: hu-01-token-claims

## Contexto técnico

- `POST /auth/login` pasa por `AuthResource` → `AuthService.login` → `TokenService.create`. Las validaciones de entrada están en `AuthService.validate` y los errores HTTP se mapean en `AuthResource.toResponse`.
- `TokenService` firma HS256 con `nimbus-jose-jwt` (`MACSigner`). Hoy añade `client` solo si no es `null` ([TokenService.java](../../../src/main/java/com/bcp/workshop/auth/TokenService.java)).
- `TokenService` falla al construirse si el secreto tiene menos de 32 bytes. `auth.token.secret=${AUTH_TOKEN_SECRET}` en [application.properties](../../../src/main/resources/application.properties) no define valor por defecto.
- `UserRepository.findByUsername` captura `SQLException`, imprime la traza con `printStackTrace()` y devuelve `null`. Un fallo de base de datos se ve como credenciales inválidas (`401`), no como `500`.
- Pruebas existentes relevantes: `TokenServiceTest` (firma, manipulación, longitud mínima, `exp - iat = 3600`), `AuthServiceTest`, `AuthResourceTest` (integración), `AuthResourceUnitTest`.

### Hallazgo que afecta la spec

El requisito "Token signing secret is configured explicitly" de la spec asume un secreto por defecto de laboratorio. En el código no existe tal valor: ni `application.properties` ni `src/test/resources` lo definen. Lo que ocurre hoy es que la inyección de `auth.token.secret` falla si `AUTH_TOKEN_SECRET` no está definido; hay que confirmarlo con una prueba de arranque antes de tomar decisiones. La spec debe corregirse en consecuencia (ver decisión D5).

## Goals

- `client` siempre presente como claim `client` en el JWT, sin romper a los clientes que no lo envían todavía.
- Verificar con pruebas que `expiresIn` coincide con `exp - now` dentro de 5 segundos.
- Verificar que los tokens expirados se rechazan y que no se registran tokens, secretos ni contraseñas.
- Mantener sin cambios el resto del contrato documentado en [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md).

## Non-goals

- Migrar a una librería JWT de alto nivel o a SmallRye JWT.
- Validar `client` contra una lista de clientes registrados.
- Cambiar el algoritmo, la duración de 3600 segundos ni el formato de `user`/`debug`.
- Cambiar `GET /q/health`, refresh tokens o revocación.
- Modificar el verificador externo (ver riesgos).

## Contratos protegidos

| Contrato | Fuente | Tratamiento |
|---|---|---|
| `200` con `token`, `expiresIn=3600`, `user`, `debug` | ENDPOINTS §4 | Preservar; prueba de forma existente |
| `400` `Request is null`, `Username is required`, `Password is required` | ENDPOINTS §5 | Preservar; mismo orden de validación |
| `401` `Invalid user <u> or password` | ENDPOINTS §5 | Preservar |
| `500` con `message` y `exception` | ENDPOINTS §5 | Preservar |
| `400`/`415` sin cuerpo por JSON malformado o `Content-Type` incorrecto | ENDPOINTS §5 | Preservar; caracterizar |
| HS256 y secreto de 32 bytes mínimo | ENDPOINTS §6, `TokenServiceTest` | Preservar |
| `client` opcional en login | ENDPOINTS §3 | Preservar en la fase de transición con valor por defecto; estricto solo con `auth.client.required=true` |

## Componentes afectados

- `TokenService`: el claim `client` se emite siempre; la rama `if (client != null)` desaparece.
- `AuthService`: resuelve `client` (solicitud o `auth.client.default`) y, con `auth.client.required=true`, rechaza con `CLIENT_REQUIRED`.
- `AuthResource`: mapea `CLIENT_REQUIRED` → `400` con `Client is required`, alcanzable solo en modo estricto.
- `UserRepository`: sin cambio de comportamiento en esta fase; solo se caracteriza.
- Tests: `TokenServiceTest.omitsOptionalClientClaimWhenAbsent` se reemplaza por una prueba de claim siempre presente; se añaden casos en `AuthServiceTest`, `AuthResourceUnitTest` y `AuthResourceTest` para ambos modos.
- Documentación: [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md) §3, §5 y §6.

## Decisiones técnicas y alternativas

**D1. Mantener HMAC HS256 con `nimbus-jose-jwt`.**
Se cambia solo el contenido de los claims, así que no hace falta una dependencia nueva ni cambiar el formato. Alternativa descartada: adoptar SmallRye JWT de Quarkus; reescribe la emisión y puede cambiar el formato de error y los claims sin beneficio para este alcance.

**D2. Compatibilidad: `client` opcional con valor por defecto y modo estricto configurable.**
`auth.client.default` (por defecto `angular-workshop`) se aplica cuando `client` falta, es `null` o está en blanco, y se registra un aviso sin datos sensibles. `auth.client.required=true` activa el `400 Client is required`. Así se despliega sin romper clientes y el paso a estricto es una configuración, no un cambio de código. Alternativa descartada: `client` obligatorio desde el inicio; rompe a los clientes que no lo envían.

**D3. `TokenService.create` recibe `client` ya resuelto y no nulo.**
Elimina la rama opcional. La resolución del valor por defecto queda en `AuthService`, así que `TokenService` no conoce la configuración de compatibilidad.

**D4. Tolerancia de 5 segundos para `expiresIn` frente a `exp - now`.**
Se verifica en prueba, no en código: `expiresIn` ya es la constante `EXPIRES_IN_SECONDS`. La tolerancia cubre la diferencia entre `iat` y el momento de la lectura. Alternativa: comparar exactamente; se descarta porque hace la prueba frágil en CI lenta.

**D5. No introducir secreto por defecto.**
HU-01 pide "eliminar el secreto por defecto fuera del laboratorio", pero en el código no hay tal valor. Añadir un default `%lab` sería introducir un secreto nuevo. Recomendación: mantener el fallo de arranque sin `AUTH_TOKEN_SECRET`, probarlo y reescribir el requisito de la spec. Esta decisión requiere confirmación del usuario.

**D6. Verificación de expiración con un verificador de prueba.**
El backend no tiene endpoint que consuma el token. Se verifica con `MACVerifier` y un token firmado con `exp` en el pasado; para ello `TokenService` necesita un reloj inyectable o un token firmado en la prueba. Alternativa: `Clock` inyectado en `TokenService`; se elige si la prueba necesita avanzar el tiempo, y se evita si basta con firmar a mano un JWT con `exp` pasado.

## Estrategia incremental

1. **Caracterización (sin cambios de código funcional).** Escribir las pruebas de la sección siguiente y ejecutarlas en verde sobre el comportamiento actual.
2. **Claims en `TokenService`.** Emitir `client` siempre y reemplazar la prueba de claim opcional.
3. **Compatibilidad en `AuthService` y mapeo en `AuthResource`.** Añadir `auth.client.default`, el aviso sin datos sensibles, `auth.client.required` y `CLIENT_REQUIRED`, con pruebas para ambos modos.
4. **Verificación de expiración y tolerancia.** Añadir las pruebas de `expiresIn` y de token expirado.
5. **Logs.** Añadir la prueba que captura la salida y verifica que no aparecen token, secreto ni contraseña. Esta prueba también detecta el `printStackTrace` si incluye valores sensibles.
6. **Secreto.** Probar el arranque sin `AUTH_TOKEN_SECRET` y actualizar la spec según D5.
7. **Documentación.** Actualizar [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md).

Cada paso debe dejar `mvn test` en verde antes de pasar al siguiente.

## Pruebas de caracterización previas

Antes de cambiar el comportamiento crítico:

- **C1. Login válido:** `200` con la forma de `LoginResponse` (existe en `AuthResourceTest.logsInWithWorkshopUser`).
- **C2. Errores `400` y `401`:** mensajes exactos para `null`, usuario vacío, contraseña en blanco y credenciales inválidas (existen en `AuthResourceUnitTest` y `AuthResourceTest`).
- **C3. `500`:** forma `{message, exception}` (existe en `AuthResourceUnitTest`).
- **C4. JSON malformado y `Content-Type` incorrecto:** `400` y `415` sin cuerpo. Falta; se añade como prueba de integración.
- **C5. `client` opcional hoy:** login sin `client` responde `200` y el token no tiene claim `client`. Tras el cambio debe seguir en `200` y el claim toma `auth.client.default`.
- **C6. Fallo de base de datos en `findByUsername`:** hoy responde `401`. Falta; se caracteriza tal cual, y se anota como riesgo.
- **C7. Firma y manipulación:** ya cubierta por `TokenServiceTest.signsClaimsAndRejectsTamperedToken`.

## Riesgos y trade-offs

- **Rotura de clientes (mitigada):** los clientes que no envían `client` siguen funcionando gracias al valor por defecto. Riesgo residual: esos tokens llevan `auth.client.default` como `client` aunque el cliente no lo haya enviado; un verificador que dependa del claim debe saberlo. Activar `auth.client.required=true` solo cuando los avisos de logs dejen de aparecer.
- **Verificador externo:** [HALLAZGOS.md](../../../HALLAZGOS.md) indica que la actualización del verificador se coordina por separado. Un verificador que espere `client` opcional podría fallar o ignorar el claim.
- **Fallo de BD como `401`:** `findByUsername` oculta errores de SQL. Corregirlo cambiaría el contrato de errores; se deja fuera de alcance y se registra.
- **Traza en logs:** `printStackTrace()` puede imprimir detalles de la base; no contiene tokens hoy, pero la prueba de logs debe cubrirlo.
- **Tolerancia de 5 segundos:** es un supuesto que requiere confirmación.

## Rollback

- Revertir el commit del paso correspondiente. El cambio no toca datos persistidos ni el esquema de SQLite.
- Los tokens ya emitidos siguen siendo válidos: la firma y el formato no cambian, solo la presencia de `client`.
- Rollback de configuración: `auth.client.required=false` devuelve el comportamiento de compatibilidad sin desplegar código.
- Rollback de código: revertir el commit del paso correspondiente; los tokens emitidos siguen válidos.
