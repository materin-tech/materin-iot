#!/usr/bin/env bash
# JMeter 压测入口（Docker 运行，零宿主依赖）
# 用法：
#   ./run.sh                                  # 默认 10 线程 × 20 迭代
#   ./run.sh -Jthreads=50 -Jloops=100         # 透传 JMeter 属性
#   COMPOSE_NETWORK=xxx ./run.sh              # 指定 compose 网络
set -euo pipefail
cd "$(dirname "$0")"

NETWORK="${COMPOSE_NETWORK:-$(docker network ls --format '{{.Name}}' | grep -m1 '^materin' || true)}"
if [ -z "$NETWORK" ]; then
  echo "错误：未找到 compose 网络（先 docker compose -f deploy/compose/docker-compose.yml up -d）" >&2
  exit 1
fi

mkdir -p reports
# HTML 报告要求 results.jtl 与 report 目录不存在或为空
rm -rf reports/results.jtl reports/report

docker build -q -t materin-jmeter .

# plans 目录实时挂载（改 JMX 无需重建镜像）；JMeter 镜像 WORKDIR=/plans
docker run --rm --network "$NETWORK" \
  -v "$PWD/plans:/plans/plans:ro" \
  -v "$PWD/reports:/reports" \
  materin-jmeter \
  -n -t /plans/plans/materin-api.jmx \
  -l /reports/results.jtl \
  -e -o /reports/report \
  "$@"

echo "----"
echo "HTML 报告: tests/jmeter/reports/report/index.html"
