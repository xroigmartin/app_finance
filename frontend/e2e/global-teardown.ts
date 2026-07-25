import { execSync } from 'node:child_process';
import { existsSync, readFileSync, rmSync } from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(__dirname, '..', '..');
const BACKEND_PID_FILE = path.join(ROOT, '.run', 'e2e-backend.pid');
const COMPOSE_FILE = path.join(ROOT, 'docker-compose.e2e.yml');

export default async function globalTeardown(): Promise<void> {
  if (existsSync(BACKEND_PID_FILE)) {
    const pid = Number(readFileSync(BACKEND_PID_FILE, 'utf8'));
    console.log(`[e2e] Deteniendo backend e2e (PGID ${pid})...`);
    try {
      // mvn lanza la JVM como hijo: matamos el grupo de procesos completo.
      process.kill(-pid, 'SIGTERM');
    } catch {
      // ya no existe, nada que hacer
    }
    rmSync(BACKEND_PID_FILE, { force: true });
  }

  console.log('[e2e] Parando y limpiando BD e2e...');
  execSync(`docker compose -f "${COMPOSE_FILE}" down -v`, { stdio: 'inherit' });
}
