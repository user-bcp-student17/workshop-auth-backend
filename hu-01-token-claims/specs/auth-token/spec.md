# Spec Delta

## Purpose

Define el comportamiento observable del JWT emitido por el login del backend de autenticación: claims mínimos, vigencia coherente, configuración del secreto y redacción de datos sensibles.

## ADDED Requirements

### Requirement: Client is required at login
El sistema SHALL exigir el campo `client` en el cuerpo de `POST /auth/login`. Un `client` ausente, `null`, vacío o compuesto solo por espacios SHALL devolver `400 Bad Request` con `message` igual a `Client is required`.

#### Scenario: Login without client
- **WHEN** el cliente envía credenciales válidas sin el campo `client`
- **THEN** el sistema responde `400 Bad Request` con `{"message":"Client is required"}` y no emite token

#### Scenario: Login with blank client
- **WHEN** el cliente envía `client` como `"   "`
- **THEN** el sistema responde `400 Bad Request` con `{"message":"Client is required"}` y no emite token

#### Scenario: Login with valid client
- **WHEN** el cliente envía credenciales válidas y `client` igual a `angular-workshop`
- **THEN** el sistema responde `200 OK` con un `token`

### Requirement: Token contains minimal claims
El JWT emitido en un login exitoso SHALL contener los claims `sub`, `client`, `iat` y `exp`. El claim `sub` SHALL ser el `username` autenticado y el claim `client` SHALL ser el valor enviado en la solicitud.

#### Scenario: Claims of a valid token
- **WHEN** un usuario válido inicia sesión con `client` igual a `angular-workshop`
- **THEN** al decodificar el `token` los claims `sub` es `student`, `client` es `angular-workshop`, y `iat` y `exp` están presentes

### Requirement: Token lifetime matches expiresIn
El claim `exp` SHALL ser igual a `iat` más `expiresIn`, y `expiresIn` SHALL valer `3600` segundos. El valor `expiresIn` de la respuesta SHALL coincidir con `exp - now` dentro de una tolerancia de 5 segundos.

#### Scenario: Lifetime is one hour
- **WHEN** un usuario válido inicia sesión
- **THEN** la respuesta tiene `expiresIn` igual a `3600` y el claim `exp` menos el claim `iat` es `3600`

#### Scenario: expiresIn is coherent with exp
- **WHEN** un usuario válido inicia sesión y se decodifica el `token` inmediatamente después
- **THEN** el valor absoluto de `(exp - now) - expiresIn` es menor o igual a 5 segundos

### Requirement: Expired token is rejected
Un JWT cuyo claim `exp` es anterior al momento de verificación SHALL ser rechazado por cualquier verificador que use `AUTH_TOKEN_SECRET`.

#### Scenario: Token past its expiration
- **WHEN** se verifica un token firmado con `AUTH_TOKEN_SECRET` cuyo `exp` ya pasó
- **THEN** la verificación falla y el token no se acepta

### Requirement: Invalid credentials return 401 without token
Un login con usuario inexistente o contraseña incorrecta SHALL responder `401 Unauthorized` con `message` igual a `Invalid user <username> or password` y SHALL NOT incluir `token`.

#### Scenario: Wrong password
- **WHEN** el cliente envía un usuario existente con una contraseña incorrecta y `client` válido
- **THEN** el sistema responde `401 Unauthorized` con `{"message":"Invalid user student or password"}` y sin `token`

#### Scenario: Unknown user
- **WHEN** el cliente envía un usuario inexistente con `client` válido
- **THEN** el sistema responde `401 Unauthorized` sin `token`

### Requirement: Token signing secret is configured explicitly
El sistema SHALL firmar los tokens con el valor de `AUTH_TOKEN_SECRET` de al menos 32 bytes. El secreto por defecto de laboratorio SHALL NOT usarse para firmar tokens fuera del modo laboratorio habilitado explícitamente.

#### Scenario: Default secret outside lab mode
- **WHEN** el servicio se ejecuta sin modo laboratorio y emite un token
- **THEN** el token no verifica con el secreto por defecto de laboratorio

#### Scenario: Configured secret is used
- **WHEN** `AUTH_TOKEN_SECRET` tiene un valor de al menos 32 bytes y el usuario inicia sesión
- **THEN** el token verifica con ese valor

### Requirement: Sensitive values are not logged
El sistema SHALL NOT registrar en logs tokens JWT, el valor de `AUTH_TOKEN_SECRET`, ni contraseñas, en login exitoso ni fallido.

#### Scenario: Successful login logs
- **WHEN** un usuario válido inicia sesión
- **THEN** ninguna línea de log contiene el `token`, el secreto ni la contraseña

#### Scenario: Failed login logs
- **WHEN** un login falla por contraseña incorrecta
- **THEN** ninguna línea de log contiene la contraseña enviada ni un `token`

### Requirement: Login success contract is preserved
La respuesta exitosa del login SHALL mantener los campos `token`, `expiresIn`, `user` (con `username`, `displayName` y `role`) y `debug` con valor `authenticated-with-legacy-flow`, y SHALL NOT incluir la contraseña ni su hash.

#### Scenario: Response shape
- **WHEN** un usuario válido inicia sesión con `client` válido
- **THEN** la respuesta contiene `token`, `expiresIn`, `user` y `debug`, y no contiene `password` ni hash alguno

### Requirement: Missing username or password keeps current errors
Un login sin `username` o sin `password` válidos SHALL responder `400 Bad Request` con los mensajes `Username is required` o `Password is required`, respectivamente.

#### Scenario: Missing username
- **WHEN** el cliente envía una contraseña y `client` válidos sin `username`
- **THEN** el sistema responde `400 Bad Request` con `{"message":"Username is required"}`

#### Scenario: Blank password
- **WHEN** el cliente envía `password` como `"   "` con `username` y `client` válidos
- **THEN** el sistema responde `400 Bad Request` con `{"message":"Password is required"}`
