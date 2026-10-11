#!/usr/bin/env bash
# Playwright E2E 入口（Docker 运行，零宿主依赖）
# 用法：./run.sh [playwright 参数透传]，如 ./run.sh tests/login.spec.ts
set -euo pipefail
cd "$(dirname "$0")"

NETWORK="${COMPOSE_NETWORK:-$(docker network ls --format '{{.Name}}' | grep -m1 '^materin' || true)}"
if [ -z "$NETWORK" ]; then
  echo "错误：未找到 compose 网络（先 docker compose -f deploy/compose/docker-compose.yml up -d）" >&2
  exit 1
fi

docker build -q -t materin-e2e .

docker run --rm --network "$NETWORK" \
  -e ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123}" \
  -e E2E_USERNAME="${E2E_USERNAME:-admin}" \
  -v "$PWD/playwright-report:/work/playwright-report" \
  -v "$PWD/test-results:/work/test-results" \
  materin-e2e npx playwright test "$@"

echo "HTML 报告: tests/e2e/playwright-report/index.html"
