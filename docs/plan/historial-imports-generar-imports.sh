#!/usr/bin/env bash
# Ayuda para MT-11 (docs/plan/historial-imports.md): genera N registros de historial de
# import repitiendo la subida del fixture Flex contra una cartera ya existente.
# Uso: ./historial-imports-generar-imports.sh <portfolioId> [veces=26] [baseUrl=http://localhost:8080]
set -euo pipefail

PORTFOLIO_ID="${1:?Uso: $0 <portfolioId> [veces=26] [baseUrl=http://localhost:8080]}"
VECES="${2:-26}"
BASE_URL="${3:-http://localhost:8080}"
FIXTURE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/frontend/e2e/fixtures/flex-sample.xml"

if [[ ! -f "$FIXTURE" ]]; then
  echo "No se encuentra el fixture: $FIXTURE" >&2
  exit 1
fi

for i in $(seq 1 "$VECES"); do
  echo "Import $i/$VECES..."
  curl -sS -X POST "$BASE_URL/api/investments/portfolios/$PORTFOLIO_ID/import" \
    -F "file=@$FIXTURE" -o /dev/null -w "  HTTP %{http_code}\n"
done
