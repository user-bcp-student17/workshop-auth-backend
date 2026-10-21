# Contrato para agentes

Ámbito: trabaja únicamente en este repositorio `workshop-auth-backend/`.
Ejecuta Git y guarda los artefactos dentro de él. Coordina dependencias con
otros componentes sin leerlos ni modificarlos.

1. Analiza el flujo completo de autenticación antes de cambiarlo: request HTTP,
   validación en `AuthService`, consulta SQLite en `UserRepository`,
   verificación en `PasswordHasher`, emisión en `TokenService` y respuesta HTTP.
2. Conserva el contrato público de `POST /auth/login` y `GET /q/health`.
   Contrasta el comportamiento con [docs/ENDPOINTS.md](docs/ENDPOINTS.md) y
   actualiza esa documentación ante cambios acordados del contrato; no
   documentes como implementado un comportamiento que el código no ofrece.
3. Crea pruebas de caracterización antes de refactorizar persistencia,
   verificación de contraseñas o emisión de tokens. Cubre respuestas exitosas,
   errores y campos públicos del login, además de la migración de credenciales
   existentes si se modifica su almacenamiento.
4. No silencies ni reduzcas CodeQL, Dependency Review o JaCoCo. No excluyas
   código ni suprimas hallazgos para hacer pasar los controles. Valida los
   cambios de código con las pruebas pertinentes y `mvn -B verify` con JDK 21.
5. No agregues secretos reales; usa variables de entorno y valores de prueba aislados.
   No registres contraseñas, hashes, tokens ni secretos.
6. Separa API HTTP (`AuthResource`), negocio (`AuthService`), persistencia
   (`UserRepository`), seguridad (`PasswordHasher` y `TokenService`) y DTOs de
   entrada/salida en cambios revisables.
7. Meta de cobertura por líneas del código modificado: 85% o superior,
   comprobada mediante el reporte JaCoCo. No reduzcas la meta ni las pruebas
   existentes para cumplirla.

## Clean code

8. Cada método hace una sola cosa. La API adapta HTTP, el servicio coordina
   autenticación, el repositorio accede a datos y los componentes de seguridad
   verifican contraseñas o firman tokens. No mezcles esas responsabilidades.
9. Nombres que expliquen el propósito, no el tipo (evitar `data`, `temp`,
   `result2`, `handleStuff`).
10. Sin duplicación: un bloque de código repetido en dos lugares se extrae a
    un método reutilizable.
11. Evitar anidamiento profundo (`if` dentro de `if` dentro de `if`); usar
    guard clauses / retorno anticipado en su lugar.
12. Sin números o strings "mágicos" sueltos en el código (por ejemplo `3600`
    para la expiración del token); usar constantes con nombre.
