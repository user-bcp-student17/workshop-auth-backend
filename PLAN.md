# Plan de corrección — H-01, H-02 y H-06

## 1. Summary

Propuesta acotada a parametrizar la consulta de autenticación (H-01), sustituir el token manipulable por uno firmado con secreto externo (H-02) y separar responsabilidades del endpoint de login (H-06). No incluye H-03 ni cambios de código en esta etapa.

## 2. Scope

- **Dentro:** cambios en el backend de este repositorio y pruebas de caracterización previas a cada refactor.
- **Fuera:** modificar el contrato HTTP, resolver H-03, o cambiar el verificador del token del componente externo. La compatibilidad con ese verificador se coordinará con su responsable, sin leer ni modificar ese componente.

## 3. Supuestos y prerrequisito

- `docs/ENDPOINTS.md` está presente en este repositorio y describe el contrato de `POST /auth/login`. Los cambios en estados, mensajes, campos JSON y semántica de `client` se contrastan con ese documento; sus preguntas abiertas se resuelven antes de modificar el contrato.
- `docs/GOOD_PRACTICES.md` y `REGLAS.md` rigen el diseño: capas separadas, DTOs, secretos externos, métodos con una responsabilidad, sin duplicación, anidamiento profundo ni valores mágicos.

## 4. Propuesta por hallazgo

### H-01 — Posible bypass por SQL concatenado (alto) — Resuelto

- **Problema:** `UserRepository.findByCredentials` concatenaba usuario y contraseña en SQL; entradas con sintaxis SQL podían alterar el `WHERE`.
- **Cambio aplicado:** `PreparedStatement` con ambos valores enlazados; mantener la consulta y el mapeo de `UserRecord` en la capa de persistencia. No se agregaron dependencias.
- **Archivos tocados:** `src/main/java/com/bcp/workshop/auth/UserRepository.java`; `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
- **Prueba antes de tocar producción:** añadir una prueba HTTP con usuario de sintaxis SQL y contraseña incorrecta. Ejecutarla primero para registrar que hoy puede autenticar; después de la corrección, exigir `401`. Añadir también el caso de credenciales válidas con caracteres especiales, si aplica, para verificar que no se rompa el login legítimo.

### H-02 — Token manipulable y secreto embebido (alto) — Resuelto

- **Problema:** el emisor de token solo aplicaba Base64 y agregaba un secreto literal; no verificaba integridad y no usaba configuración compartida con el consumidor.
- **Estado:** `TokenService` emite JWT HS256 con `sub`, `role`, `iat`, `exp` y `client` opcional; el secreto se lee de `AUTH_TOKEN_SECRET`. La actualización del verificador externo se coordina por separado.
- **Cambio propuesto:** emitir token firmado con HMAC y claims explícitos `sub`, `client`, `iat` y `exp`; obtener el secreto de configuración externa (`application.properties` respaldado por variable de entorno), nunca de una constante Java. Mantener sin cambios el formato HTTP de la respuesta y la relación entre `expiresIn` y la expiración firmada, según confirme el contrato. Acordar algoritmo/formato y despliegue coordinado con el responsable del verificador externo. Añadir una dependencia JWT solo si la interoperabilidad acordada la requiere y tras revisar su mantenimiento y vulnerabilidades; no implementar un formato propietario.
- **Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java` (o el componente de seguridad definido en H-06), `src/main/resources/application.properties`, pruebas en `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y/o una nueva prueba unitaria del emisor. `pom.xml` solo si la decisión de interoperabilidad justifica una dependencia.
- **Prueba antes de tocar producción:** caracterizar el login exitoso y todos los campos JSON observables; añadir pruebas del token esperado con el formato/verificador acordado. Tras el cambio, comprobar firma válida, rechazo de token alterado, claims requeridos y expiración coherente con `expiresIn`, usando únicamente secreto aislado de prueba y sin imprimirlo ni registrar tokens.

### H-06 — Resource mezcla HTTP, negocio y presentación — Resuelto

- **Estado:** `AuthResource` solo adapta HTTP; `AuthService` ejecuta el flujo, `UserRepository` concentra la persistencia y `LoginResponse` mapea la respuesta.

- **Problema:** `AuthResource.login` valida, consulta la base, genera token y arma la respuesta en un método anidado; devuelve un mapa dinámico e incluye `debug`.
- **Cambio propuesto:** dejar en `AuthResource` la adaptación HTTP; mover el flujo de autenticación a `AuthService`, la persistencia a un repositorio y la emisión a un componente de seguridad. Usar DTOs de salida y validación con retornos anticipados; centralizar constantes y mapeos para evitar duplicación. Preservar estados, mensajes y campos públicos acordados. No quitar ni cambiar `debug` hasta confirmar si forma parte del contrato; si el contrato lo descarta, documentar esa decisión antes de implementarla.
- **Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/AuthResource.java`, `LegacyDatabase.java` (extraer o renombrar como repositorio), `LegacyTokenUtil.java` (integrar con el servicio de seguridad), nuevos `AuthService.java` y DTO(s) de respuesta; `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y pruebas unitarias del servicio según las responsabilidades extraídas.
- **Prueba antes de tocar producción:** ampliar `AuthResourceTest` para caracterizar `400` con request nulo, `400` por username/password ausente, `401` con credenciales inválidas y `200` exitoso; fijar mensajes y campos JSON observados, incluyendo `user`, `token`, `expiresIn` y `debug`, y compararlos con `docs/ENDPOINTS.md` antes de tratarlos como contrato.

## 5. Decisiones técnicas pendientes

- Confirmar si `docs/ENDPOINTS.md` está aprobado y si `debug` y los mensajes actuales son parte del contrato.
- Acordar con el consumidor externo el estándar, algoritmo, claims, nombre/rotación del secreto y secuencia de despliegue del token.
- Resolver en implementación el orden de H-01 y H-06 para evitar duplicar cambios en `LegacyDatabase`; la prueba antiinyección debe quedar antes de ambos.

## 6. Riesgos y mitigaciones

- **Contrato no disponible:** una refactorización podría cambiar accidentalmente la API. **Mitigación:** recuperar el documento y aprobar los casos de caracterización antes de implementar.
- **Emisor y verificador incompatibles:** un token firmado podría romper integración. **Mitigación:** acordar formato y despliegue coordinado; no desplegar emisor incompatible de forma aislada.
- **Secreto faltante o expuesto:** configuración incorrecta podría impedir autenticación o filtrar el secreto. **Mitigación:** configuración externa obligatoria, valor de prueba aislado y verificación de que no aparece en logs/respuestas.
- **Refactor grande y difícil de revisar:** cambios acoplados pueden ocultar regresiones. **Mitigación:** separar correcciones en cambios revisables, con pruebas de caracterización previas y cada método con responsabilidad única.

## 7. Criterios de aceptación medibles

- El payload SQL de prueba obtiene `401` después de H-01; las credenciales válidas siguen obteniendo `200`.
- El token válido verifica su firma; una modificación del token no verifica. Incluye `sub`, `client`, `iat` y `exp`, y `exp` concuerda con `expiresIn` según el contrato aprobado.
- Los casos HTTP caracterizados antes del refactor conservan estado, mensaje y forma JSON acordados; `AuthResource` queda sin acceso directo a persistencia ni emisión de tokens.
- Las pruebas pasan con `mvn test`; la cobertura JaCoCo del código modificado alcanza al menos 85%, conforme a REGLAS.md.

## 8. Plan de validación

- **Pruebas unitarias:** repositorio con entradas SQL; servicio con credenciales válidas/inválidas; token con firma, claims, expiración y alteración.
- **Pruebas funcionales:** ejecutar casos HTTP de caracterización y verificar el contrato aprobado antes y después de cada cambio.
- **Seguridad:** confirmar que SQL usa parámetros, que el secreto no está en Java/logs/respuestas, que un token alterado se rechaza y que no se deshabilitan análisis SAST/SCA.
- **Evidencia:** resultados de `mvn test`, reporte JaCoCo, casos de caracterización, acuerdo de interoperabilidad con el verificador y contrato actualizado/recuperado.

## 9. Tareas sugeridas por rol y preguntas abiertas

- **Tech Lead:** recuperar el contrato; definir orden de cambios; acordar formato y despliegue del token con el consumidor.
- **Backend:** escribir primero las pruebas de caracterización; parametrizar SQL; extraer servicio/repositorio/seguridad y mantener DTOs y configuración externa.
- **QA:** validar estados, mensajes y estructura de respuesta; ejecutar pruebas de inyección y token manipulado; adjuntar resultados y cobertura.
- **Preguntas abiertas:** ¿está aprobada la versión de `docs/ENDPOINTS.md` presente en el repositorio? ¿`debug` es contractual? ¿qué estándar/algoritmo y proceso de rotación acepta el verificador externo?

APROBADO POR GLADYS PATIÑO LT DEL PROYECTO el 18/10/26

## Selección y estado

- H-01 y H-02 se seleccionan por riesgo alto; H-06, por alcance acotado.
- `docs/HALLAZGOS.md` marca H-01, H-02, H-03 y H-06 como `Resuelto`; H-04, H-05 y H-07 no tienen estado indicado. La selección es la confirmada por el solicitante.
- Los archivos `LegacyDatabase.java` y `LegacyTokenUtil.java` citados en este plan ya no existen; su equivalente actual es `UserRepository.java` y `TokenService.java`.
- `docs/ENDPOINTS.md` está presente y describe `POST /auth/login` y `GET /q/health`. Sus preguntas abiertas (`client` obligatorio, errores de deserialización y estado no saludable) siguen pendientes.
AUTORIZADO por Gladys Patiño el 18/10/2026
