#!/usr/bin/env bash
# ============================================================================
# 格物 IoT 平台 —— EMQX 双实例一键配置脚本（幂等，可重复执行）
#
# 覆盖范围：
#   EMQX-1（内部，materin-emqx，设备接入）
#     1. password_based HTTP 认证        -> backend /api/v1/mqtt/auth
#     2. HTTP 授权                       -> backend /api/v1/mqtt/acl
#     3. connector conn_to_emqx2 + sink sink_to_emqx2   （上行桥接）
#     4. 规则 up_report/up_event/up_reply_to_emqx2      （open/ 命名空间转发）
#   EMQX-2（开发者，materin-emqx2，AK/SK 鉴权）
#     5. password_based HTTP 认证        -> backend /api/v1/mqtt/auth（AK/SK 分支）
#     6. HTTP 授权                       -> backend /api/v1/mqtt/acl（open/ 限定）
#     7. connector conn_to_emqx1 + sink sink_to_emqx1   （下行桥接）
#     8. 规则 down_cmd_to_emqx1（open/ -> materin/）
#
# 详细说明见 docs/emqx-configuration-guide.md
# ============================================================================
set -euo pipefail

E1=${EMQX1_HOST:-http://localhost:18083}
E2=${EMQX2_HOST:-http://localhost:18084}
DASH_USER=${EMQX_DASH_USER:-admin}
DASH_PASS=${EMQX_ADMIN_PASSWORD:-public}
SVC_USER=${MATERIN_MQTT_SERVICE_USERNAME:-materin-svc-emqx2-bridge}
SVC_PASS=${MATERIN_MQTT_SERVICE_PASSWORD:-materin-svc-dev-password}
BACKEND=${BACKEND_URL:-http://materin-backend:8080}

token() { curl -sf -X POST "$1/api/v5/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$DASH_USER\",\"password\":\"$DASH_PASS\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])'; }
T1=$(token "$E1"); T2=$(token "$E2")
H1="Authorization: Bearer $T1"; H2="Authorization: Bearer $T2"
api() { local m=$1 u=$2 h=$3; shift 3; curl -sf -X "$m" "$u" -H "$h" -H 'Content-Type: application/json' "$@"; }
get()  { curl -sf -o /dev/null -w '%{http_code}' "$1" -H "$2"; }
mark() { echo "  [OK] $1"; }

ensure_auth_http() { # $1=base $2=header
  if [ "$(get "$1/api/v5/authentication/password_based:http" "$2")" = "200" ]; then
    mark "HTTP 认证源已存在"; return
  fi
  api POST "$1/api/v5/authentication" "$2" -d '{
    "mechanism":"password_based","backend":"http","enable":true,
    "url":"'"$BACKEND"'/api/v1/mqtt/auth","method":"post",
    "headers":{"content-type":"application/json"},
    "body":{"username":"${username}","password":"${password}"},
    "connect_timeout":"5s","request_timeout":"5s","pool_size":8,"ssl":{"enable":false}}' >/dev/null
  mark "HTTP 认证源已创建"
}

ensure_authz_http() { # $1=base $2=header
  if [ "$(get "$1/api/v5/authorization/sources/http" "$2")" = "200" ]; then
    mark "HTTP 授权源已存在"; return
  fi
  api POST "$1/api/v5/authorization/sources" "$2" -d '{
    "type":"http","enable":true,
    "url":"'"$BACKEND"'/api/v1/mqtt/acl","method":"post",
    "headers":{"content-type":"application/json"},
    "body":{"username":"${username}","topic":"${topic}","action":"${action}","clientid":"${clientid}"},
    "connect_timeout":"5s","request_timeout":"5s","pool_size":8,"ssl":{"enable":false}}' >/dev/null
  mark "HTTP 授权源已创建"
}

ensure_connector_action() { # $1=base $2=header $3=inst(E1/E2) $4=conn名 $5=action名 $6=server $7=user $8=pass
  if [ "$(get "$1/api/v5/connectors/mqtt:$4" "$2")" = "200" ]; then
    mark "$3 connector $4 已存在"
  else
    api POST "$1/api/v5/connectors" "$2" -d '{
      "type":"mqtt","name":"'"$4"'","server":"'"$6"'","proto_ver":"v5",
      "clean_start":true,"username":'"${7:+\"$7\"}"',"password":'"${8:+\"$8\"}"'}' >/dev/null
    mark "$3 connector $4 已创建"
  fi
  if [ "$(get "$1/api/v5/actions/mqtt:$5" "$2")" = "200" ]; then
    mark "$3 sink $5 已存在"
  else
    api POST "$1/api/v5/actions" "$2" -d '{
      "type":"mqtt","name":"'"$5"'","connector":"'"$4"'",
      "parameters":{"topic":"${topic}","payload":"${payload}"}}' >/dev/null
    mark "$3 sink $5 已创建"
  fi
}

ensure_rule() { # $1=base $2=header $3=ruleId $4=sql $5=action
  if [ "$(get "$1/api/v5/rules/$3" "$2")" = "200" ]; then
    mark "规则 $3 已存在"
  else
    api POST "$1/api/v5/rules" "$2" -d '{
      "id":"'"$3"'","sql":'"$4"',"actions":["'"$5"'"]}' >/dev/null
    mark "规则 $3 已创建"
  fi
}

echo '======== EMQX-1（内部实例，设备接入）========'
ensure_auth_http "$E1" "$H1"
ensure_authz_http "$E1" "$H1"
ensure_connector_action "$E1" "$H1" "E1" "conn_to_emqx2" "sink_to_emqx2" "materin-emqx2:1883" "" ""
for KIND in report event reply; do
  ensure_rule "$E1" "$H1" "up_${KIND}_to_emqx2" \
    "\"SELECT concat('open/', topic) AS topic, payload FROM \\\"materin/+/+/${KIND}\\\"\"" \
    "mqtt:sink_to_emqx2"
done

echo '======== EMQX-2（开发者实例，AK/SK 鉴权）========'
ensure_auth_http "$E2" "$H2"
ensure_authz_http "$E2" "$H2"
ensure_connector_action "$E2" "$H2" "E2" "conn_to_emqx1" "sink_to_emqx1" "materin-emqx:1883" "$SVC_USER" "$SVC_PASS"
DOWN_SQL="\"SELECT regex_replace(topic, '^open/', '') AS topic, payload FROM \\\"open/materin/+/+/cmd\\\"\""
ensure_rule "$E2" "$H2" "down_cmd_to_emqx1" "$DOWN_SQL" "mqtt:sink_to_emqx1"

echo '======== 状态汇总 ========'
curl -sf "$E1/api/v5/actions" -H "$H1" | python3 -c 'import json,sys; [print("  E1 sink:", a["name"], a["status"]) for a in json.load(sys.stdin)]'
curl -sf "$E2/api/v5/actions" -H "$H2" | python3 -c 'import json,sys; [print("  E2 sink:", a["name"], a["status"]) for a in json.load(sys.stdin)]'
