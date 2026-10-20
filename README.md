# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

Antes de iniciar, define `AUTH_TOKEN_SECRET` con al menos 32 bytes aleatorios. En PowerShell, para una clave local efímera:

```powershell
$env:AUTH_TOKEN_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
mvn quarkus:dev
```

No guardes la clave en el repositorio. Para integrar con el verificador de tokens, configura el mismo secreto de forma segura y coordina la rotación entre servicios.

Servicio: `http://localhost:8080`

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.
