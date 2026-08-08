# Despliegue con Docker

- **Estado:** ✅ Implementado
- **Última actualización:** 2026-07-27
- **Ámbito:** infraestructura (no es un dominio funcional; complementa a los PRD)

## Propósito

Ejecutar la aplicación completa (PostgreSQL + backend + frontend) en contenedores Docker en una máquina de producción (p. ej. una Raspberry Pi 4 de 4GB), de forma que quede siempre activa: los contenedores se reinician solos si caen y se levantan al arrancar la máquina. El flujo de desarrollo local (`./app.sh`, `mvn spring-boot:run`, `ng serve`, descrito en `CLAUDE.md`) no cambia: `docker-compose.yml` sigue levantando solo la BD de desarrollo.

## Arquitectura de contenedores

```
navegador ──► :80 (o FINANCE_FRONTEND_PORT)  finance-frontend (nginx)
                    ├── estáticos Angular (dist/frontend/browser)
                    └── /api ──proxy──► finance-backend:8080 (Spring Boot)
                                              └── jdbc ──► finance-db:5432 (PostgreSQL 17)
```

| Servicio | Imagen | Puerto host | Healthcheck |
|---|---|---|---|
| `db` | `postgres:17-alpine` | interno (5432) | `pg_isready` |
| `backend` | build de `backend/Dockerfile` | ninguno (solo red interna) | `curl /actuator/health` |
| `frontend` | build de `frontend/Dockerfile` | `FINANCE_FRONTEND_PORT` (por defecto 80) | `wget /` |

- El backend **no publica puerto al host**: se accede siempre vía nginx (`http://<host>/api/...`).
- El frontend usa rutas relativas `/api`, por lo que nginx (`frontend/nginx.conf`) hace de reverse proxy hacia `backend:8080`. Además: fallback SPA a `index.html`, `client_max_body_size 20m` (importación de extractos CSV/Excel), gzip y cache larga para assets con hash.

## Imágenes

- `backend/Dockerfile`: multi-stage. Build con `maven:3.9-eclipse-temurin-25` (`mvn package -DskipTests`; los tests corren fuera porque necesitan Testcontainers), runtime con `eclipse-temurin:25-jre` + `curl` (healthcheck), usuario no root. El healthcheck usa `/actuator/health` (dependencia `spring-boot-starter-actuator`; `application.properties` expone solo `health`).
- `frontend/Dockerfile`: multi-stage. Build con `node:22-alpine` (`npm ci && npm run build`), runtime `nginx:1.27-alpine` sirviendo `dist/frontend/browser` con `frontend/nginx.conf`.

Ambas imágenes son estándar (`node`, `maven`, `eclipse-temurin`, `nginx`, `postgres` oficiales) y publican variantes `arm64`, por lo que el mismo `docker-compose.prod.yml` funciona sin cambios en una Raspberry Pi 4 (arquitectura `aarch64`).

## Comandos

```bash
docker compose -f docker-compose.prod.yml up -d --build   # construir y levantar el stack completo
docker compose -f docker-compose.prod.yml ps               # estado (espera: 3 servicios healthy)
docker compose -f docker-compose.prod.yml logs -f backend   # logs de un servicio
docker compose -f docker-compose.prod.yml down              # parar la app (el volumen bind-mounted persiste en el host)
```

## Datos y variables de entorno

`docker-compose.prod.yml` es independiente de `docker-compose.yml` (BD de desarrollo): usa sus propios contenedores, red y datos. Variables parametrizables (exportarlas o dejarlas en un `.env` en la raíz del proyecto, igual que `app.sh`):

| Variable | Por defecto | Uso |
|---|---|---|
| `FINANCE_DB_NAME` / `FINANCE_DB_USER` / `FINANCE_DB_PASSWORD` | `finance` / `finance` / `finance` | credenciales de Postgres, inyectadas también al backend |
| `FINANCE_DATA_DIR` | `./data/postgres` | carpeta del host con los datos de Postgres (bind mount, no volumen con nombre); cambiarla el día que se monte un SSD externo en la Pi |
| `FINANCE_FRONTEND_PORT` | `80` | puerto host en el que se publica la app |

`FINANCE_DATA_DIR` usa un **bind mount** (no un volumen Docker con nombre) para que los datos vivan en una ruta explícita del host — más fácil de hacer backup o de mover a un disco externo sin tocar `docker volume`. La ruta está en `.gitignore` (`data/`).

## Límites de memoria (Raspberry Pi 4 de 4GB)

Los tres servicios llevan `mem_limit` (db 512m, backend 768m, frontend 128m) para dejar margen al sistema operativo y evitar que un contenedor descontrolado haga swap/OOM al resto. El backend fija `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`: por defecto la JVM detecta el límite del contenedor pero solo usa el 25% como heap máximo, demasiado conservador con presupuestos de memoria ya ajustados. Si la Pi da problemas de memoria, bajar `mem_limit`/`MaxRAMPercentage` o el número de servicios activos a la vez.

## «Siempre activa»

- Los tres servicios llevan `restart: unless-stopped`: Docker los reinicia si el proceso muere y los vuelve a levantar al arrancar la máquina (mientras no se hayan parado explícitamente con `docker compose down`/`stop`).
- Requisito: el demonio Docker debe arrancar con el sistema. Comprobar con `systemctl is-enabled docker` (habilitar con `sudo systemctl enable docker`).
- `depends_on` con `condition: service_healthy` ordena el arranque: db → backend → frontend.

## Referencias de código

- `docker-compose.prod.yml` — orquestación de producción (healthchecks, `restart`, límites de memoria).
- `docker-compose.yml` — BD de desarrollo únicamente (sin cambios, la usa `./app.sh`).
- `backend/Dockerfile`, `backend/.dockerignore`.
- `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/.dockerignore`.
- `backend/pom.xml` — `spring-boot-starter-actuator`; `backend/src/main/resources/application.properties` — expone solo `health`.
