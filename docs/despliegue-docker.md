# Despliegue con Docker

- **Estado:** ✅ Implementado
- **Última actualización:** 2026-07-05
- **Ámbito:** infraestructura (no es un dominio funcional; complementa a los PRD)

## Propósito

Ejecutar la aplicación completa (PostgreSQL + backend + frontend) en contenedores Docker, de forma que quede siempre activa: los contenedores se reinician solos si caen y se levantan al arrancar la máquina.

## Arquitectura de contenedores

```
navegador ──► :80  finance-frontend (nginx)
                    ├── estáticos Angular (dist/frontend/browser)
                    └── /api ──proxy──► finance-backend:8080 (Spring Boot)
                                              └── jdbc ──► finance-db:5432 (PostgreSQL 17)
```

| Servicio | Imagen | Puerto host | Profile | Healthcheck |
|---|---|---|---|---|
| `db` | `postgres:17-alpine` | 5432 | — (siempre) | `pg_isready` |
| `backend` | build de `backend/Dockerfile` | ninguno (solo red interna) | `app` | `curl /actuator/health` |
| `frontend` | build de `frontend/Dockerfile` | **80** | `app` | `wget /` |

- El backend **no publica puerto al host**: se accede siempre vía nginx (`http://localhost/api/...`). Así no choca con el backend de desarrollo en el 8080.
- El frontend usa rutas relativas `/api`, por lo que nginx (`frontend/nginx.conf`) hace de reverse proxy hacia `backend:8080`. Además: fallback SPA a `index.html`, `client_max_body_size 20m` (importación de extractos CSV/Excel), gzip y cache larga para assets con hash.
- Ambos servicios de la app están bajo el **profile `app`** para que el flujo de desarrollo (`./app.sh start`, que hace `docker compose up -d` para la BD) no los arranque.

## Imágenes

- `backend/Dockerfile`: multi-stage. Build con `maven:3.9-eclipse-temurin-25` (`mvn package -DskipTests`; los tests corren fuera porque necesitan Testcontainers), runtime con `eclipse-temurin:25-jre` + `curl` (healthcheck), usuario no root. El healthcheck usa `/actuator/health` (dependencia `spring-boot-starter-actuator`; solo se expone `health`).
- `frontend/Dockerfile`: multi-stage. Build con `node:22-alpine` (`npm ci && npm run build`), runtime `nginx:1.27-alpine` sirviendo `dist/frontend/browser` con `frontend/nginx.conf`.

## Comandos

```bash
docker compose --profile app up -d --build   # construir y levantar el stack completo
docker compose --profile app ps              # estado (espera: 3 servicios healthy)
docker compose --profile app logs -f backend # logs de un servicio
docker compose --profile app down            # parar la app (la BD y su volumen persisten)
docker compose up -d                         # solo la BD (flujo de desarrollo, como siempre)
```

La base de datos usa el mismo volumen `finance-data` en ambos modos: los datos son los mismos ejecutando en Docker o en desarrollo local.

## Variables de entorno

El backend en compose recibe `FINANCE_DB_HOST=db` (y el resto de `FINANCE_DB_*` con los valores por defecto finance/finance/finance). Para cambiar credenciales, ajustar a la vez el servicio `db` (`POSTGRES_*`) y el `backend` (`FINANCE_DB_*`) en `docker-compose.yml`.

## «Siempre activa»

- Los tres servicios llevan `restart: unless-stopped`: Docker los reinicia si el proceso muere y los vuelve a levantar al arrancar la máquina (mientras no se hayan parado explícitamente con `docker compose down`/`stop`).
- Requisito: el demonio Docker debe arrancar con el sistema. Comprobar con `systemctl is-enabled docker` (habilitar con `sudo systemctl enable docker`).
- `depends_on` con `condition: service_healthy` ordena el arranque: db → backend → frontend.

## Referencias de código

- `docker-compose.yml` — orquestación (profiles, healthchecks, restart).
- `backend/Dockerfile`, `backend/.dockerignore`.
- `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/.dockerignore`.
- `backend/pom.xml` — `spring-boot-starter-actuator`; `backend/src/main/resources/application.properties` — expone solo `health`.
