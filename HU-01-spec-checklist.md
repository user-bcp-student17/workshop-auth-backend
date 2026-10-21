# Spec Checklist: HU-01 - Token firmado con expiración y claims mínimos

## 1. Summary
- Historia: el servicio de autenticación (`POST /auth/login`) devuelve `token`, `expiresIn` y `user`. La implementación actual firma con HMAC y lee `AUTH_TOKEN_SECRET`.
- Objetivo: que el token incluya claims mínimos verificables (`sub`, `client`, `iat`, `exp`), que `expiresIn` sea coherente con `exp`, y que no se filtren tokens ni secretos.
- Veredicto de la HU: **usable con ajustes**. Los ajustes ya están listados en la HU y se recogen abajo.

## 2. Scope
**Dentro del alcance**
- Exigir los claims `sub`, `client`, `iat` y `exp` en el token emitido.
- Obtener `client` de la solicitud, validarlo y reflejarlo en el token.
- Verificar que `expiresIn` coincide con `exp - now` dentro de una tolerancia documentada.
- Eliminar el secreto por defecto o documentarlo como exclusivo de laboratorio.
- Evitar logs de tokens y secretos.
- Documentar la decisión entre mantener HMAC o adoptar una librería JWT.
- Pruebas de login válido, credenciales inválidas y token expirado.

**Fuera del alcance**
- No mencionado en la HU: refresh tokens, revocación, rotación de secretos, roles/permisos, cambios en el campo `user`.

## 3. Supuestos
- La implementación de HMAC y la lectura de `AUTH_TOKEN_SECRET` ya existen, según indica la HU.
- El endpoint `POST /auth/login` ya existe y devuelve `token`, `expiresIn` y `user`.
- Las pruebas se ejecutan con el framework de pruebas que ya use el proyecto (no especificado en la HU).

## 4. Decisiones técnicas por tomar
| Decisión | Estado |
|---|---|
| Conservar HMAC artesanal frente a adoptar una librería JWT | **Pendiente** (la HU exige documentarla) |
| Eliminar el secreto por defecto frente a documentarlo como solo laboratorio | **Pendiente** |
| Origen y validación de `client` | **Pendiente** (ver sección 9) |
| Tolerancia para la comparación `expiresIn` vs `exp - now` | **Pendiente** (valor no definido) |

## 5. Riesgos y mitigaciones
1. **Uso del secreto por defecto fuera de laboratorio.** Mitigación: fallar al arrancar si `AUTH_TOKEN_SECRET` no está definido en entornos que no sean laboratorio; documentar la configuración.
2. **Filtración de tokens o secretos en logs.** Mitigación: redactar valores sensibles; prueba que verifique que los logs capturados no contienen el token ni el secreto.
3. **`client` aceptado sin validar, lo que permite suplantar un cliente.** Mitigación: validarlo contra una fuente definida y probar un caso inválido.
4. **Desalineación entre `expiresIn` y `exp`.** Mitigación: tolerancia documentada y pruebas con reloj controlado.
5. **HMAC propio implementado incorrectamente.** Mitigación: decisión documentada HMAC vs librería JWT, con revisión de Cyber antes de cerrar la HU.

## 6. Criterios de aceptación (medibles)
- **AC-1:** El 100 % de los tokens emitidos por un login válido contiene los claims `sub`, `client`, `iat` y `exp`, verificado por pruebas automatizadas.
- **AC-2:** Para cada respuesta de login válido, `|expiresIn - (exp - now)| ≤ T` segundos, donde **T está pendiente de definir** (sección 9).
- **AC-3:** Existen pruebas que pasan para: login válido, credenciales inválidas y token expirado.
- **AC-4:** En la ejecución de la suite de pruebas, la búsqueda en los logs capturados devuelve 0 coincidencias del token emitido y del valor de `AUTH_TOKEN_SECRET`.
- **AC-5:** No existe ningún valor de secreto por defecto en el código fuera de laboratorio (búsqueda en el repositorio sin coincidencias).
- **AC-6:** Existe un documento en el repositorio que registra la decisión HMAC vs librería JWT.

## 7. Plan de validación
**Pruebas unitarias**
- Generación del token con los claims `sub`, `client`, `iat` y `exp`.
- Cálculo de `expiresIn` frente a `exp - now` con reloj controlado.
- Rechazo de un token expirado.

**Pruebas funcionales**
- `POST /auth/login` con credenciales válidas: respuesta con `token`, `expiresIn` y `user`.
- `POST /auth/login` con credenciales inválidas: rechazo (código HTTP pendiente de definir).
- `POST /auth/login` sin `client` o con `client` inválido: rechazo (comportamiento pendiente de definir).

**Seguridad (comprobaciones mínimas)**
- El arranque falla o se advierte si `AUTH_TOKEN_SECRET` falta fuera de laboratorio.
- Los logs capturados no contienen el token ni el secreto.
- La decisión HMAC vs JWT está documentada y revisada por Cyber.

**Evidencia requerida**
- Salida de la suite de pruebas (unitarias y funcionales) con resultado aprobado.
- Lista de casos de prueba de claims, expiración y errores de autenticación.
- Documento de configuración de `AUTH_TOKEN_SECRET` (sin valores reales).
- Muestra de logs redactados.
- Documento de decisión HMAC vs JWT, versionado en el repositorio.

## 8. Tareas sugeridas por rol
**Chapter/PO**
- Confirmar el valor de la tolerancia T y el TTL de expiración.
- Definir el origen de `client` y aprobar la lista de elementos fuera del alcance.

**Tech Lead**
- Decidir HMAC frente a librería JWT y registrar la decisión.
- Definir la política de arranque cuando falta `AUTH_TOKEN_SECRET`.
- Definir la forma de validar `client` y los códigos HTTP de error.

**Backend**
- Añadir los claims `sub`, `client`, `iat` y `exp` al token.
- Validar `client` y reflejarlo en el token.
- Eliminar o condicionar el secreto por defecto y redactar logs.

**Frontend**
- No aplica según la HU. Confirmar si el cliente web consume `expiresIn` o `user` y requiere cambios.

**QA**
- Escribir casos de prueba para AC-1 a AC-4.
- Ejecutar la suite con reloj controlado para verificar `expiresIn`.
- Recopilar la evidencia de la sección 7.

**Cyber**
- Revisar la decisión HMAC vs JWT.
- Revisar la política de secretos y la redacción de logs.
- Verificar que no hay secretos por defecto en el código fuera de laboratorio.

## 9. Preguntas abiertas
1. ¿Cuál es la tolerancia T (en segundos) para comparar `expiresIn` con `exp - now`?
2. ¿Cuál es el TTL de expiración del token?
3. ¿De dónde llega `client` (cuerpo de la solicitud, cabecera u otra fuente) y contra qué lista se valida?
4. ¿Qué código HTTP debe devolverse con credenciales inválidas, token expirado y `client` inválido o ausente?
5. ¿El secreto por defecto se elimina o se mantiene documentado como solo laboratorio?
6. ¿Cuál es el criterio de decisión HMAC vs librería JWT, y quién la aprueba?
7. ¿Qué campos y niveles de log deben revisarse para la redacción?
8. ¿Qué algoritmo HMAC concreto se usa actualmente?
9. ¿Qué debe contener el claim `sub` (identificador de usuario) y el campo `user` de la respuesta?
10. ¿Qué queda fuera del alcance: refresh tokens, revocación, rotación de secretos, roles?
11. ¿Cuál es el impacto esperado o la métrica de negocio de la HU?
12. ¿Cuál es la Definition of Done del equipo?
13. ¿Qué framework de pruebas y herramientas del stack deben usarse?
