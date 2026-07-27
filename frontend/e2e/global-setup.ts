import { execSync, spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { E2E_BACKEND_PORT, E2E_DB_PORT, backendUrl } from './env';
import { seed } from './fixtures/seed';

const ROOT = path.resolve(__dirname, '..', '..');
const RUN_DIR = path.join(ROOT, '.run');
const BACKEND_PID_FILE = path.join(RUN_DIR, 'e2e-backend.pid');
const COMPOSE_FILE = path.join(ROOT, 'docker-compose.e2e.yml');

function sh(cmd: string): void {
  execSync(cmd, { stdio: 'inherit' });
}

async function waitForHttp(url: string, timeoutMs: number): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url);
      if (res.ok) return;
    } catch {
      // aún no levantado
    }
    await new Promise(r => setTimeout(r, 1000));
  }
  throw new Error(`${url} no respondió tras ${timeoutMs}ms`);
}

/**
 * Resetea la BD e2e (docker-compose.e2e.yml, aislada de la BD de desarrollo:
 * ver docs/testing-plan-frontend.md CP2) y arranca un backend Spring Boot
 * propio apuntando a ella (puerto E2E_BACKEND_PORT, nunca el 8080 de dev),
 * luego siembra un dataset fijo vía la API real.
 */
export default async function globalSetup(): Promise<void> {
  mkdirSync(RUN_DIR, { recursive: true });

  console.log('[e2e] Reseteando BD e2e...');
  sh(`docker compose -f "${COMPOSE_FILE}" down -v`);
  sh(`docker compose -f "${COMPOSE_FILE}" up -d --wait`);

  console.log('[e2e] Arrancando backend e2e...');
  const backend = spawn('mvn', ['-q', 'spring-boot:run'], {
    cwd: path.join(ROOT, 'backend'),
    env: {
      ...process.env,
      FINANCE_DB_PORT: String(E2E_DB_PORT),
      SERVER_PORT: String(E2E_BACKEND_PORT),
    },
    detached: true,
    stdio: 'ignore',
  });
  backend.unref();
  writeFileSync(BACKEND_PID_FILE, String(backend.pid));

  await waitForHttp(`${backendUrl}/api/dashboard/summary`, 120_000);

  console.log('[e2e] Sembrando dataset fijo...');
  await seed();
  console.log('[e2e] Listo.');
}
