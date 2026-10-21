# Hallazgos candidatos - Auth Backend

Alcance: revisión estática del contrato de token entre Auth Backend y Requirements API. Este documento registra una hipótesis para revisión humana; no crea issues ni confirma un defecto. El hallazgo H-02 de [HALLAZGOS.md](HALLAZGOS.md) describe la resolución de la emisión de tokens y deja la actualización del verificador para coordinación aparte.

### El token emitido no coincide con el formato aceptado por Requirements API
- Estado: `CANDIDATO`
- Evidencia: Auth Backend crea un JWT y lo firma con HS256 en [TokenService.java](../src/main/java/com/bcp/workshop/auth/TokenService.java#L32) y [TokenService.java](../src/main/java/com/bcp/workshop/auth/TokenService.java#L44). Requirements API todavía decodifica Base64 y espera cuatro segmentos más un secreto fijo en [main.py](../../workshop-requirements-api/app/main.py#L17) y [main.py](../../workshop-requirements-api/app/main.py#L20). El frontend envía el token de Auth como Bearer en [dashboard.component.ts](../../workshop-login-frontend/src/app/dashboard/dashboard.component.ts#L41).
- Impacto: el login puede completarse y, aun así, fallar la consulta del dashboard con `401`.
- Prioridad propuesta: `P1` (alta; afecta el flujo principal integrado).
- Contrato potencialmente afectado: formato y validación de `Authorization: Bearer` entre Auth Backend, frontend y Requirements API.
- Cómo validarlo: iniciar los servicios, obtener un token mediante `POST /auth/login` y usarlo en `GET /api/tasks`; verificar que el consumidor acepte la emisión actual y que las pruebas cubran ese intercambio integrado.
