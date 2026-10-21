# Hallazgos - Módulo de autorización

Alcance: revisión estática del flujo `POST /auth/login`, la persistencia de usuarios y la emisión/consumo del token. Tras resolver H-03 se ejecutaron las pruebas automatizadas; CodeQL y Dependency Review quedan pendientes en CI.

## Alto

### H-01 [Resuelto]. Posible bypass de autenticación por SQL concatenado

`UserRepository.findByCredentials` insertaba directamente `username` y `password` en una consulta SQL. Una entrada manipulada podía alterar la condición del `WHERE` y permitir autenticación sin credenciales válidas. El mismo patrón también hacía frágil la consulta frente a comillas y caracteres especiales.

**Evidencia:** [UserRepository.java](../src/main/java/com/bcp/workshop/auth/UserRepository.java)

**Recomendación:** reemplazar `Statement` por `PreparedStatement` con parámetros enlazados y agregar pruebas con entradas que contengan comillas y sintaxis SQL.

**Resolución:** `findByCredentials` usa `PreparedStatement` con username y password enlazados; una prueba de inyección SQL confirma que la entrada se rechaza con `401`.

### H-02 [Resuelto]. Token modificable y secreto embebido

`TokenService` emitía contenido solo codificado en Base64 y agregaba un secreto literal; Base64 no proporciona firma ni integridad. Un consumidor podía decodificar y modificar identidad, rol o expiración. Además, el mismo secreto aparece en el verificador de la API de requisitos, por lo que cambiarlo en un solo servicio rompería la integración.

**Evidencia:** [TokenService.java](../src/main/java/com/bcp/workshop/auth/TokenService.java) y [main.py](../../workshop-requirements-api/app/main.py)

**Recomendación:** firmar tokens con HMAC y secreto externo; incluir y validar `sub`, `client`, `iat` y `exp`. Migrar el emisor y el verificador de forma coordinada, sin registrar tokens ni secretos.

**Resolución:** el emisor ahora genera JWT HS256 con `sub`, `role`, `iat`, `exp` y `client` cuando se proporciona; usa `AUTH_TOKEN_SECRET` y prueba que una alteración invalida la firma. La actualización del verificador se coordina por separado.

### H-03 [Resuelto]. Contraseñas almacenadas y comparadas en texto plano

La inicialización de la base de datos inserta contraseñas directamente y el repositorio las compara en SQL. Una copia o filtración de la base expone las credenciales reutilizables de los usuarios.

**Evidencia:** [UserRepository.java](../src/main/java/com/bcp/workshop/auth/UserRepository.java)

**Recomendación:** almacenar hashes resistentes a fuerza bruta con salt, por ejemplo BCrypt, y verificar la contraseña en una capa de servicio. Definir una migración para las cuentas existentes; no conservar contraseñas en logs ni en mensajes de error.

**Resolución:** las contraseñas se derivan con PBKDF2-HMAC-SHA-256, salt aleatorio por cuenta y comparación en tiempo constante. El repositorio migra contraseñas existentes dentro de una transacción, borra el valor legado en texto plano y almacena los nuevos usuarios directamente como hashes. Las pruebas verifican la migración, la ausencia del valor legado y la autenticación con la contraseña correcta.

## Medio

### H-04. Errores internos expuestos al cliente

`AuthResource.login` devuelve el mensaje de excepción y el nombre de su clase en respuestas HTTP 500. `UserRepository` también imprime excepciones directamente en stderr y devuelve `null`, lo que hace que un fallo de base de datos pueda presentarse como credenciales inválidas.

**Evidencia:** [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java) y [UserRepository.java](../src/main/java/com/bcp/workshop/auth/UserRepository.java)

**Recomendación:** responder con un mensaje genérico y un estado coherente con el fallo; registrar detalles únicamente en el servidor mediante logging controlado y propagar errores de persistencia en vez de convertirlos en ausencia de usuario.

### H-05. Política CORS demasiado permisiva

La configuración acepta cualquier origen mediante `quarkus.http.cors.origins=/.*/` y habilita varios métodos HTTP. Esto amplía innecesariamente los orígenes que pueden interactuar con el servicio.

**Evidencia:** [application.properties](../src/main/resources/application.properties)

**Recomendación:** restringir orígenes y métodos a los requeridos por el frontend, configurándolos por entorno cuando corresponda.

## Brechas de diseño y aceptación

### H-06 [Resuelto]. Resource mezcla HTTP, validación, negocio y presentación

`AuthResource.login` valida campos, consulta persistencia, emite el token, escapa datos y construye la respuesta. El anidamiento dificulta aislar errores y probar cada responsabilidad; además devuelve un mapa dinámico e incluye el campo `debug`.

**Evidencia:** [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java)

**Recomendación:** separar Resource, servicio, repositorio y componente de seguridad; usar DTOs de entrada/salida y constantes con nombre. Caracterizar primero estados HTTP, mensajes y campos públicos para preservar el contrato acordado de `POST /auth/login`.

**Resolución:** `AuthResource` solo adapta HTTP; `AuthService` valida y ejecuta el flujo de autenticación; `UserRepository` concentra persistencia; y `LoginResponse` mapea la entidad a DTO manteniendo la respuesta pública caracterizada.

### H-07. No se valida el cliente ni se emiten los claims requeridos

La solicitud incluye `client`, pero no se valida ni se incorpora al token. El token actual tampoco ofrece claims verificables `sub`, `client`, `iat` y `exp`, y no existe correspondencia demostrable entre `expiresIn` y una expiración firmada.

**Evidencia:** [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java), [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java) y [TokenService.java](../src/main/java/com/bcp/workshop/auth/TokenService.java)

**Recomendación:** validar `client`, emitir los claims establecidos en HU-01 y probar que `expiresIn` concuerda con `exp` dentro de una tolerancia documentada.

## Verificación

- `mvn -B verify` con JDK 21: `BUILD SUCCESS`; 31 pruebas, sin fallas ni errores. La cobertura JaCoCo por líneas es 85.2% en `UserRepository`, 93.75% en `PasswordHasher` y 100% en `AuthService`, cumpliendo la meta mínima de 85% de `REGLAS.md`.
- Pendiente en CI: ejecutar CodeQL y Dependency Review; no se deshabilitaron ni se suprimieron sus hallazgos.
- El contrato se consultó en `C:\Dia1\reqflow-workspace\workshop-auth-backend\docs\ENDPOINTS.md` por indicación del solicitante; la copia de `docs/ENDPOINTS.md` sigue ausente en este repositorio.