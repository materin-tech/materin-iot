#!/usr/bin/env bash
# compose 冒烟测试：各服务 healthy + 后端 API / 前端页面闭环
set -u
cd "$(dirname "$0")"
# 管理员密码可配置：默认开发值；正式环境在 .env（不入库）设 ADMIN_PASSWORD
[ -f .env ] && { source .env; }
ADMIN_PASSWORD="${ADMIN_PASSWORD:-123456}"
PASS=0; FAIL=0

check() {
  if eval "$2" >/dev/null 2>&1; then
    echo "PASS  $1"; PASS=$((PASS+1))
  else
    echo "FAIL  $1"; FAIL=$((FAIL+1))
  fi
}

check "mysql healthy"    "docker inspect -f '{{.State.Health.Status}}' materin-mysql | grep -q healthy"
check "redis healthy"    "docker inspect -f '{{.State.Health.Status}}' materin-redis | grep -q healthy"
check "emqx healthy"     "docker inspect -f '{{.State.Health.Status}}' materin-emqx | grep -q healthy"
check "iotdb healthy"    "docker inspect -f '{{.State.Health.Status}}' materin-iotdb | grep -q healthy"
check "backend healthy"  "docker inspect -f '{{.State.Health.Status}}' materin-backend | grep -q healthy"
check "frontend healthy" "docker inspect -f '{{.State.Health.Status}}' materin-frontend | grep -q healthy"
check "frontend http 200"  "curl -sf -o /dev/null http://localhost:3080/"
check "frontend proxy login" "curl -sf -X POST http://localhost:3080/api/v1/auth/login -H 'Content-Type: application/json' -d '{\"username\":\"admin\",\"password\":\"$ADMIN_PASSWORD\"}' | grep -q 'accessToken'"
check "backend api list"   "curl -sf http://localhost:8080/api/v1/device/list | grep -q '\"code\":0'"
check "backend create"     "curl -sf -X POST http://localhost:8080/api/v1/device -H 'Content-Type: application/json' -d '{\"deviceKey\":\"smoke-$(date +%s)\",\"name\":\"smoke\",\"status\":0}' | grep -q '\"code\":0'"
check "emqx dashboard"     "curl -sf http://localhost:18083 | grep -qi emqx"
check "iotdb cli"          "docker exec materin-iotdb /iotdb/sbin/start-cli.sh -e 'SHOW DATABASES' | grep -qi 'costs'"
check "redis ping"         "docker exec materin-redis redis-cli ping | grep -q PONG"

echo "----"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
