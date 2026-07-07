# Despliegue con Docker

- **Estado:** ✅ Implementado
- **Última actualización:** 2026-07-07
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
| `db` | `postgres:17-alpine` | 5432 | `app` | `pg_isready` |
| `db-dev` | `postgres:17-alpine` | 5433 | — (por defecto) | `pg_isready` |
| `backend` | build de `backend/Dockerfile` | ninguno (solo red interna) | `app` | `curl /actuator/health` |
| `frontend` | build de `frontend/Dockerfile` | **80** | `app` | `wget /` |

- El backend **no publica puerto al host**: se accede siempre vía nginx (`http://localhost/api/...`). Así no choca con el backend de desarrollo en el 8080.
- El frontend usa rutas relativas `/api`, por lo que nginx (`frontend/nginx.conf`) hace de reverse proxy hacia `backend:8080`. Además: fallback SPA a `index.html`, `client_max_body_size 20m` (importación de extractos CSV/Excel), gzip y cache larga para assets con hash.
- Los tres servicios de producción (db, backend, frontend) están bajo el **profile `app`** para que el flujo de desarrollo (`./app.sh start`, que hace `docker compose up -d`) no los arranque: sin profile solo se levanta `db-dev`.

## Imágenes

- `backend/Dockerfile`: multi-stage. Build con `maven:3.9-eclipse-temurin-25` (`mvn package -DskipTests`; los tests corren fuera porque necesitan Testcontainers), runtime con `eclipse-temurin:25-jre` + `curl` (healthcheck), usuario no root. El healthcheck usa `/actuator/health` (dependencia `spring-boot-starter-actuator`; solo se expone `health`).
- `frontend/Dockerfile`: multi-stage. Build con `node:22-alpine` (`npm ci && npm run build`), runtime `nginx:1.27-alpine` sirviendo `dist/frontend/browser` con `frontend/nginx.conf`.

## Comandos

```bash
docker compose --profile app up -d --build   # construir y levantar el stack completo
docker compose --profile app ps              # estado (espera: 3 servicios healthy)
docker compose --profile app logs -f backend # logs de un servicio
docker compose --profile app down            # parar la app (los volúmenes persisten)
docker compose up -d                         # solo la BD de desarrollo db-dev (flujo de desarrollo)
```

## Bases de datos separadas: producción y desarrollo

Producción (`db`, puerto 5432, volumen `finance-data`) y desarrollo (`db-dev`, puerto 5433, volumen `finance-data-dev`) son **instancias PostgreSQL independientes con datos propios**: las pruebas locales (`./app.sh` o `docker-compose.dev.yml`) nunca tocan los datos de producción, y una migración Flyway de una rama en desarrollo no altera el esquema de producción.

- El backend apunta por defecto a la de desarrollo (`application.properties`: `FINANCE_DB_PORT:5433`); el compose de producción inyecta `FINANCE_DB_HOST=db`/`FINANCE_DB_PORT=5432` explícitamente.
- `./app.sh stop` hace `docker compose stop db-dev` (nunca `down`): no elimina contenedores ni la red compartida con el stack de producción.
- En el primer arranque `db-dev` está vacía y el backend siembra las categorías por defecto. Para clonar los datos de producción en desarrollo (con ambas BD arrancadas):

```bash
docker exec finance-db pg_dump -U finance --clean --if-exists finance | docker exec -i finance-db-dev psql -U finance finance
```

- Resetear la BD de desarrollo: `./app.sh stop && docker compose rm -sf db-dev && docker volume rm app_finance_finance-data-dev`. **No usar `docker compose down -v`**: borraría también el volumen de producción si su contenedor está parado.

## Desarrollo en contenedores (`docker-compose.dev.yml`)

Alternativa al flujo local (`./app.sh start`) para desarrollar **sin JDK ni Node instalados en el host**: los contenedores ejecutan las herramientas de desarrollo directamente sobre el código fuente montado (no hay build de imágenes propias).

```bash
docker compose -f docker-compose.dev.yml up -d                    # BD + backend dev (:8080) + frontend dev (:4200)
docker compose -f docker-compose.dev.yml logs -f backend-dev      # logs
docker compose -f docker-compose.dev.yml restart backend-dev      # recompilar tras cambios en el backend
docker compose -f docker-compose.dev.yml rm -sf backend-dev frontend-dev   # parar solo la app (la BD sigue)
```

| Servicio | Imagen | Comando | Puerto host |
|---|---|---|---|
| `db-dev` | `extends` del `db-dev` de `docker-compose.yml` | — | 5433 |
| `backend-dev` | `maven:3.9-eclipse-temurin-25` | `mvn spring-boot:run` | 8080 |
| `frontend-dev` | `node:22-alpine` | `npm ci` (solo 1.ª vez) `+ ng serve --poll` | 4200 |

- El servicio `db-dev` es **el mismo** que el del compose principal (mismo contenedor `finance-db-dev` y mismo volumen `finance-data-dev`): los datos de desarrollo se comparten entre este modo y el flujo local de `./app.sh`, pero son independientes de la BD de producción.
- El frontend usa `frontend/proxy.conf.docker.json` (target `http://backend-dev:8080`, la red interna de compose) en vez de `proxy.conf.json` (localhost). `ng serve` recarga en caliente al editar; `--poll 2000` garantiza que detecta cambios a través del bind mount.
- El backend compila al arrancar; tras editar código hay que reiniciar `backend-dev` (no hay devtools).
- Volúmenes con nombre para `~/.m2` (caché Maven entre arranques) y para `target/`, `node_modules/` y `.angular/`: los contenedores corren como root y así no dejan ficheros de root en el working copy del host.
- `npm ci` se ejecuta solo si el volumen de `node_modules` está vacío y **nunca reescribe `package-lock.json`** (el npm del contenedor puede diferir del host y generaría ruido en git). Tras cambiar `package.json`: `docker compose -f docker-compose.dev.yml exec frontend-dev npm ci` (o borrar el volumen y volver a levantar).
- **Conflictos de puertos**: usa 8080 y 4200, los mismos que el backend/frontend locales de `./app.sh` — no ejecutar ambos flujos a la vez. Sí puede convivir con el stack de producción (profile `app`, puerto 80): los nombres de servicio/contenedor son distintos.
- Sin `restart` ni healthchecks: es un entorno de desarrollo efímero, no el despliegue «siempre activo».

## Variables de entorno

El backend en compose recibe `FINANCE_DB_HOST=db` (y el resto de `FINANCE_DB_*` con los valores por defecto finance/finance/finance). Para cambiar credenciales, ajustar a la vez el servicio `db` (`POSTGRES_*`) y el `backend` (`FINANCE_DB_*`) en `docker-compose.yml`.

## «Siempre activa»

- Los tres servicios llevan `restart: unless-stopped`: Docker los reinicia si el proceso muere y los vuelve a levantar al arrancar la máquina (mientras no se hayan parado explícitamente con `docker compose down`/`stop`).
- Requisito: el demonio Docker debe arrancar con el sistema. Comprobar con `systemctl is-enabled docker` (habilitar con `sudo systemctl enable docker`).
- `depends_on` con `condition: service_healthy` ordena el arranque: db → backend → frontend.

## Referencias de código

- `docker-compose.yml` — orquestación (profiles, healthchecks, restart).
- `docker-compose.dev.yml` — stack de desarrollo (código montado, hot reload); `frontend/proxy.conf.docker.json` — proxy de `ng serve` hacia `backend-dev`.
- `app.sh` — flujo de desarrollo local: arranca `db-dev` (`up -d --wait`) y la detiene con `stop` (nunca `down`, para no afectar al stack de producción).
- `backend/Dockerfile`, `backend/.dockerignore`.
- `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/.dockerignore`.
- `backend/pom.xml` — `spring-boot-starter-actuator`; `backend/src/main/resources/application.properties` — expone solo `health`.
