# Contrato HTTP — Workshop Auth Backend

Base local: `http://localhost:8080`. Los endpoints devuelven JSON cuando hay cuerpo, salvo los errores HTTP generados por el framework que se indican explícitamente. Los ejemplos usan las cuentas de laboratorio documentadas en [README.md](../README.md).

## `POST /auth/login`

### 1. Qué hace

Autentica una cuenta de usuario del workshop y, si las credenciales son válidas, entrega un JWT firmado para identificar al usuario en los servicios que confían en el mismo secreto.

### 2. Autenticación requerida

No requiere autenticación previa. No envíes un header `Authorization`. El cuerpo debe enviarse con `Content-Type: application/json`; la respuesta exitosa usa `application/json`.

### 3. Request

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `username` | string | Sí | Nombre de usuario. No puede faltar ni ser vacío o solo espacios. |
| `password` | string | Sí | Contraseña. No puede faltar ni ser vacía o solo espacios; se verifica exactamente como se envía. |
| `client` | string o `null` | No* | Identificador del cliente. No se valida. Si falta, es `null` o solo contiene espacios, se usa `auth.client.default` (por defecto `angular-workshop`) y se registra un aviso sin datos sensibles. El claim `client` del JWT siempre está presente. *Con `auth.client.required=true` (modo estricto) pasa a ser obligatorio. |

Ejemplo JSON completo:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

`username` y `password` son propiedades de [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java); las reglas de campos requeridos se aplican en [AuthService.java](../src/main/java/com/bcp/workshop/auth/AuthService.java#L26).

### 4. Response exitosa

**Estado:** `200 OK`.

| Campo | Tipo | Descripción |
|---|---|---|
| `token` | string | JWT firmado por el backend. El valor cambia en cada autenticación. |
| `expiresIn` | integer | Duración del token en segundos; actualmente `3600`. |
| `user` | object | Datos públicos del usuario autenticado. |
| `user.username` | string | Nombre de usuario. |
| `user.displayName` | string | Nombre para mostrar; se escapan caracteres HTML antes de incluirlo. |
| `user.role` | string | Rol almacenado para la cuenta, por ejemplo `STUDENT` o `TRAINER` en las cuentas de laboratorio. |
| `debug` | string | Marcador fijo `authenticated-with-legacy-flow`. |

Ejemplo JSON completo. El valor de `token` es un marcador: reemplázalo por el JWT real devuelto por el servidor.

```json
{
  "token": "<JWT firmado emitido por el servidor>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

La forma de la respuesta está definida en [LoginResponse.java](../src/main/java/com/bcp/workshop/auth/LoginResponse.java#L5) y se mapea en [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L40).

### 5. Respuestas de error

| Estado | Cuándo ocurre | Ejemplo de cuerpo |
|---|---|---|
| `400 Bad Request` | El JSON es `null`. | `{"message":"Request is null"}` |
| `400 Bad Request` | Falta `username`, es `null`, vacío o solo contiene espacios. | `{"message":"Username is required"}` |
| `400 Bad Request` | Falta `password`, es `null`, vacío o solo contiene espacios. | `{"message":"Password is required"}` |
| `400 Bad Request` | Modo estricto (`auth.client.required=true`) y falta `client`, es `null`, vacío o solo contiene espacios. No se emite token. | `{"message":"Client is required"}` |
| `401 Unauthorized` | El usuario no existe o la contraseña no coincide. | `{"message":"Invalid user student or password"}` |
| `400 Bad Request` | El cuerpo no se puede interpretar como JSON. El framework responde sin cuerpo. | *(cuerpo vacío)* |
| `415 Unsupported Media Type` | El `Content-Type` no es compatible con JSON. El framework responde sin cuerpo. | *(cuerpo vacío)* |
| `500 Internal Server Error` | Se produce una excepción durante el procesamiento del login. `message` contiene el mensaje de la excepción o `Unknown error` si no tiene mensaje; `exception` contiene su nombre de clase. | `{"message":"database failure","exception":"java.lang.IllegalStateException"}` |

Los cuerpos de `400` y `401` los construye [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L40); el cuerpo `500` también se define allí en [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L56). Los casos de validación y credenciales están caracterizados en [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L40), y la forma del error `500` en [AuthResourceUnitTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceUnitTest.java#L78). Los ejemplos de `400` por JSON malformado y `415` se observaron contra la aplicación local; ambos tuvieron cuerpo vacío.

### 6. Notas de seguridad y preguntas abiertas

- El JWT usa HS256, incluye `sub`, `role`, `client`, `iat` y `exp`, y expira realmente una hora después de su emisión. El claim `client` siempre está presente: toma el valor enviado o, si falta, `auth.client.default`. El secreto se configura con `AUTH_TOKEN_SECRET` (sin valor por defecto) y debe tener al menos 32 bytes; si falta o es más corto, el arranque falla. Véase [TokenService.java](../src/main/java/com/bcp/workshop/auth/TokenService.java#L19) y [application.properties](../src/main/resources/application.properties).
- El login no devuelve la contraseña ni su hash. Las cuentas de ejemplo son credenciales de laboratorio; no las uses en ambientes externos.
- El error `500` expone el mensaje y la clase de la excepción. Los clientes no deberían mostrar esos detalles a usuarios finales.
- **Decisión de transición:** `client` no es obligatorio (`auth.client.required=false`) y usa `auth.client.default`. Pasar al modo estricto es un cambio de configuración, no de código.
- **Pregunta abierta:** no se caracterizó el cuerpo de una solicitud vacía ni de JSON sintácticamente válido con tipos incompatibles; el recurso no define esos errores de deserialización.

## `GET /q/health`

### 1. Qué hace

Permite a un monitor u operador comprobar si el backend está disponible y conocer el estado agregado de sus comprobaciones de salud.

### 2. Autenticación requerida

No requiere autenticación. No envíes un header `Authorization`. La respuesta actual es JSON.

### 3. Request

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| — | — | No | No recibe parámetros ni cuerpo. |

Ejemplo de request HTTP completo; no tiene cuerpo JSON:

```http
GET /q/health HTTP/1.1
Host: localhost:8080
Accept: application/json
```

El endpoint lo proporciona la extensión `quarkus-smallrye-health`, incluida en [pom.xml](../pom.xml#L40).

### 4. Response exitosa

**Estado:** `200 OK` cuando el estado agregado es saludable.

| Campo | Tipo | Descripción |
|---|---|---|
| `status` | string | Estado agregado; el valor observado para esta aplicación es `UP`. |
| `checks` | array de objetos | Comprobaciones registradas. Actualmente está vacío porque el backend no declara comprobaciones propias. |

Ejemplo JSON completo observado localmente:

```json
{
  "status": "UP",
  "checks": []
}
```

### 5. Respuestas de error

La aplicación no define respuestas HTTP de error propias para este endpoint y no registra comprobaciones que puedan devolver un estado no saludable. Si el proceso no está disponible, no hay respuesta HTTP. El cuerpo y estado ante una futura comprobación fallida dependen de la extensión de salud y no están definidos por código propio en este repositorio.

**Pregunta abierta:** ¿se debe fijar y probar un contrato para el estado no saludable (código HTTP y cuerpo) antes de registrar comprobaciones de salud?

### 6. Notas de seguridad y preguntas abiertas

- El endpoint no autentica al solicitante; limita su exposición de red según el entorno.
- La ruta predeterminada de la extensión de salud es `/q/health`; la respuesta `200` y el cuerpo `{"status":"UP","checks":[]}` se verificaron ejecutando la aplicación localmente.
- No hay comprobaciones de salud específicas de SQLite o del flujo de autenticación en este backend.
