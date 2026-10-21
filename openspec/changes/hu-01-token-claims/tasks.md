# Tasks: hu-01-token-claims

Alcance: [HU-01.md](../../../HU-01.md). No existe GitHub Issue con ese título; este checklist sigue la historia local. Cada tarea es pequeña y verificable. Las pruebas de caracterización van antes que cualquier cambio funcional.

## 1. Preparación

- [ ] 1.1 Confirmar que la rama de trabajo es `hu-01-token-claims` y que el árbol está limpio (`git status`), sin crear ni cambiar de rama.
- [x] 1.2 Ejecutar `mvn test` sobre el estado actual y registrar el resultado como línea base.

## 2. Pruebas de caracterización (sin cambio funcional)

- [x] 2.1 Confirmar en `AuthResourceTest` que un login válido responde `200` con `token`, `expiresIn`, `user` y `debug`.
- [x] 2.2 Confirmar mensajes exactos `400` (`Request is null`, `Username is required`, `Password is required`) y `401` (`Invalid user <u> or password`).
- [x] 2.3 Confirmar que el `500` responde con `message` y `exception`.
- [x] 2.4 Añadir prueba de integración: JSON malformado y `Content-Type` incorrecto responden `400` y `415` sin cuerpo.
- [x] 2.5 Añadir prueba: login sin `client` responde `200` y el token no contiene claim `client` (comportamiento actual). Superada por la decisión D2: sustituida en 4.x por la prueba de valor por defecto `usesDefaultClientAndClaimWhenAbsent`.
- [x] 2.6 Añadir prueba: fallo de base de datos en `findByUsername` responde `401` (comportamiento actual, registrado como riesgo).
- [x] 2.7 Confirmar que `TokenServiceTest` sigue en verde (firma, manipulación, longitud mínima de 32 bytes).
- [x] 2.8 Ejecutar `mvn test`; todo en verde antes de la sección 3.

## 3. Claims del token

- [x] 3.1 Escribir prueba en rojo: el token siempre contiene `client`. Sustituye `omitsOptionalClientClaimWhenAbsent`.
- [x] 3.2 Cambiar `TokenService.create` para recibir `client` no nulo y eliminar la rama `if (client != null)`.
- [x] 3.3 Verificar que el token contiene `sub`, `client`, `iat` y `exp`.
- [x] 3.4 Ejecutar `TokenServiceTest` en verde.

## 4. Compatibilidad del claim `client`

- [x] 4.1 Añadir `auth.client.default=angular-workshop` y `auth.client.required=false` en [application.properties](../../../src/main/resources/application.properties).
- [x] 4.2 Escribir pruebas en `AuthServiceTest`: `client` ausente, `null` o en blanco usa el valor por defecto; un valor enviado se usa tal cual.
- [x] 4.3 Implementar la resolución del valor por defecto en `AuthService`.
- [x] 4.4 Añadir aviso de log sin datos sensibles cuando se usa el valor por defecto; probar que no incluye token ni contraseña.
- [x] 4.5 Escribir pruebas del modo estricto (`auth.client.required=true`): `client` ausente o en blanco responde `400` con `Client is required` y no emite token.
- [x] 4.6 Añadir el `Failure` `CLIENT_REQUIRED` y su mapeo a `400` en `AuthResource`.
- [x] 4.7 Ejecutar `AuthServiceTest`, `AuthResourceUnitTest` y `AuthResourceTest` en verde.

## 5. Vigencia y expiración

- [x] 5.1 Prueba: `expiresIn` es `3600` y `exp - iat` es `3600`.
- [x] 5.2 Prueba: `|(exp - now) - expiresIn| <= 5` segundos tras emitir el token.
- [x] 5.3 Prueba: un token firmado con `AUTH_TOKEN_SECRET` y `exp` ya pasado es rechazado por un verificador con el mismo secreto.
- [x] 5.4 Cambiar `TokenService` solo si alguna prueba de esta sección lo exige.

## 6. Secreto y configuración

- [x] 6.1 Confirmar con el usuario y reescribir el requisito "Token signing secret is configured explicitly" de la spec: el código no tiene secreto por defecto (decisión D5). Confirmado por el usuario.
- [ ] 6.2 Prueba o verificación manual: arrancar sin `AUTH_TOKEN_SECRET` falla; un secreto de menos de 32 bytes falla. **Pendiente de verificación de arranque.**
- [x] 6.3 Documentar `AUTH_TOKEN_SECRET` y las nuevas propiedades `auth.client.*` en [README.md](../../../README.md), sin valores reales.

## 7. Logs

- [ ] 7.1 Prueba que captura la salida y verifica que no aparecen token, secreto ni contraseña en logins válidos, inválidos y expirados. **Parcial: cubre aviso de cliente por defecto (4.4).**
- [ ] 7.2 Revisar `UserRepository.findByUsername` (`printStackTrace`) con la prueba de logs; ajustar solo si la prueba falla.

## 8. Documentación y cierre

- [x] 8.1 Actualizar [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md) §3, §5 y §6 (`client`, errores y HS256/secreto).
- [x] 8.2 Ejecutar `mvn test` completo y `openspec validate "hu-01-token-claims" --strict`.
- [x] 8.3 Revisar `git diff --stat` y confirmar que no hay cambios fuera del alcance.
