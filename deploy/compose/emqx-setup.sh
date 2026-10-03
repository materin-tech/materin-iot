#!/usr/bin/env bash
# 配置 EMQX：HTTP 认证 + HTTP 授权（回调 backend /api/v1/mqtt/auth|acl）
set -euo pipefail
EMQX_HOST=${EMQX_HOST:-http://localhost:18083}
BACKEND_URL=${BACKEND_URL:-http://materin-backend:8080}
DASH_USER=${EMQX_DASH_USER:-admin}
DASH_PASS=${EMQX_ADMIN_PASSWORD:-public}

TOKEN=$(curl -sf -X POST "$EMQX_HOST/api/v5/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$DASH_USER\",\"password\":\"$DASH_PASS\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
AUTH="Authorization: Bearer $TOKEN"

# 1) password_based HTTP 认证（幂等：先查再建）
if ! curl -sf "$EMQX_HOST/api/v5/authentication" -H "$AUTH" | grep -q 'http'; then
  curl -sf -X POST "$EMQX_HOST/api/v5/authentication" -H "$AUTH" -H 'Content-Type: application/json' -d '{
    "mechanism": "password_based",
    "backend": "http",
    "enable": true,
    "url": "'"$BACKEND_URL"'/api/v1/mqtt/auth",
    "method": "post",
    "headers": {"content-type": "application/json"},
    "body": {"username": "${username}", "password": "${password}"},
    "connect_timeout": "5s",
    "request_timeout": "5s",
    "pool_size": 8,
    "ssl": {"enable": false}
  }' >/dev/null
  echo "authentication 已创建"
else
  echo "authentication 已存在"
fi

# 2) HTTP 授权源（追加到链尾）
if ! curl -sf "$EMQX_HOST/api/v5/authorization/sources" -H "$AUTH" | grep -q 'http'; then
  curl -sf -X POST "$EMQX_HOST/api/v5/authorization/sources" -H "$AUTH" -H 'Content-Type: application/json' -d '{
    "type": "http",
    "enable": true,
    "url": "'"$BACKEND_URL"'/api/v1/mqtt/acl",
    "method": "post",
    "headers": {"content-type": "application/json"},
    "body": {"username": "${username}", "topic": "${topic}", "action": "${action}", "clientid": "${clientid}"},
    "connect_timeout": "5s",
    "request_timeout": "5s",
    "pool_size": 8,
    "ssl": {"enable": false}
  }' >/dev/null
  echo "authorization 已创建"
else
  echo "authorization 已存在"
fi
