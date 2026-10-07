#!/bin/sh
# ============================================================================
# 格物 IoT 平台 —— EMQX 双实例一键配置脚本（幂等，可重复执行）
#
# 同时用于：宿主机手工执行（bash/sh 均可）+ compose emqx-init 服务自动执行
# （挂载进 curlimages/curl 容器，POSIX sh + curl 即可，无 python3/jq 依赖）。
#
# 覆盖范围：
#   EMQX-1（内部，materin-emqx，设备接入）
#     1. password_based HTTP 认证        -> /api/v1/mqtt/auth（服务账号/设备三分流）
#     2. HTTP 授权                       -> /api/v1/mqtt/acl（设备限自身 namespace）
#     3. connector conn_to_emqx2 + sink sink_to_emqx2   （上行桥接，服务账号）
#     4. 规则 up_report/up_event/up_reply_to_emqx2      （open/ 命名空间转发）
#   EMQX-2（open broker，materin-emqx2，第三方 AK/SK）
#     5. password_based HTTP 认证        -> /api/v1/mqtt/open/auth（仅服务账号+AK/SK）
#     6. HTTP 授权                       -> /api/v1/mqtt/open/acl（AK 限 open/）
#     7. connector conn_to_emqx1 + sink sink_to_emqx1   （下行桥接，服务账号）
#     8. 规则 down_cmd_to_emqx1（open/ -> materin/）
#
# 环境变量（均可覆盖）：EMQX1_HOST / EMQX2_HOST / EMQX_DASH_USER /
#   EMQX_ADMIN_PASSWORD / EMQX2_ADMIN_PASSWORD / MATERIN_MQTT_SERVICE_USERNAME /
#   MATERIN_MQTT_SERVICE_PASSWORD / BACKEND_URL
#
# 详细说明见 docs/emqx-configuration-guide.md
# ============================================================================
set -eu

E1=${EMQX1_HOST:-http://localhost:18083}
E2=${EMQX2_HOST:-http://localhost:18084}
DASH_USER=${EMQX_DASH_USER:-admin}
DASH_PASS=${EMQX_ADMIN_PASSWORD:-public}
E2_DASH_PASS=${EMQX2_ADMIN_PASSWORD:-$DASH_PASS}
SVC_USER=${MATERIN_MQTT_SERVICE_USERNAME:-materin-svc-emqx2-bridge}
SVC_PASS=${MATERIN_MQTT_SERVICE_PASSWORD:-materin-svc-dev-password}
BACKEND=${BACKEND_URL:-http://materin-backend:8080}

# token()：登录 Dashboard 取 JWT；带重试（compose 启动时序兜底）
token() { # $1=base $2=password
  i=0
  while [ "$i" -lt 30 ]; do
    T=$(curl -sf -X POST "$1/api/v5/login" -H 'Content-Type: application/json' \
      -d "{\"username\":\"$DASH_USER\",\"password\":\"$2\"}" \
      | sed -n 's/.*"token":"\([^"]*\)".*/\1/p' || true)
    [ -n "$T" ] && { echo "$T"; return 0; }
    i=$((i + 1)); sleep 2
  done
  echo "[FATAL] Dashboard 登录失败（重试 30 次超时）: $1" >&2
  return 1
}

T1=$(token "$E1" "$DASH_PASS")
T2=$(token "$E2" "$E2_DASH_PASS")
H1="Authorization: Bearer $T1"
H2="Authorization: Bearer $T2"
api() { local m=$1 u=$2 h=$3; shift 3; curl -sf -X "$m" "$u" -H "$h" -H 'Content-Type: application/json' "$@"; }
get() { curl -s -o /dev/null -w '%{http_code}' "$1" -H "$2"; }
mark() { echo "  [OK] $1"; }

# $1=base $2=header $3=backendURL
ensure_auth_http() {
  if [ "$(get "$1/api/v5/authentication/password_based:http" "$2")" = "200" ]; then
    mark "HTTP 认证源已存在"; return
  fi
  api POST "$1/api/v5/authentication" "$2" -d '{
    "mechanism":"password_based","backend":"http","enable":true,
    "url":"'"$3"'/api/v1/mqtt/auth","method":"post",
    "headers":{"content-type":"application/json"},
    "body":{"username":"${username}","password":"${password}"},
    "connect_timeout":"5s","request_timeout":"5s","pool_size":8,"ssl":{"enable":false}}' >/dev/null
  mark "HTTP 认证源已创建"
}

# $1=base $2=header $3=backendURL
ensure_authz_http() {
  if [ "$(get "$1/api/v5/authorization/sources/http" "$2")" = "200" ]; then
    mark "HTTP 授权源已存在"; return
  fi
  api POST "$1/api/v5/authorization/sources" "$2" -d '{
    "type":"http","enable":true,
    "url":"'"$3"'/api/v1/mqtt/acl","method":"post",
    "headers":{"content-type":"application/json"},
    "body":{"username":"${username}","topic":"${topic}","action":"${action}","clientid":"${clientid}"},
    "connect_timeout":"5s","request_timeout":"5s","pool_size":8,"ssl":{"enable":false}}' >/dev/null
  mark "HTTP 授权源已创建"
}

# $1=base $2=header $3=backendURL —— open broker 专用：仅服务账号 + AK/SK，设备凭证拒绝
ensure_auth_http_open() {
  if [ "$(get "$1/api/v5/authentication/password_based:http" "$2")" = "200" ]; then
    mark "open HTTP 认证源已存在"; return
  fi
  api POST "$1/api/v5/authentication" "$2" -d '{
    "mechanism":"password_based","backend":"http","enable":true,
    "url":"'"$3"'/api/v1/mqtt/open/auth","method":"post",
    "headers":{"content-type":"application/json"},
    "body":{"username":"${username}","password":"${password}"},
    "connect_timeout":"5s","request_timeout":"5s","pool_size":8,"ssl":{"enable":false}}' >/dev/null
  mark "open HTTP 认证源已创建"
}

# $1=base $2=header $3=backendURL —— open broker 专用：AK 仅限 open/ 命名空间
ensure_authz_http_open() {
  if [ "$(get "$1/api/v5/authorization/sources/http" "$2")" = "200" ]; then
    mark "open HTTP 授权源已存在"; return
  fi
  api POST "$1/api/v5/authorization/sources" "$2" -d '{
    "type":"http","enable":true,
    "url":"'"$3"'/api/v1/mqtt/open/acl","method":"post",
    "headers":{"content-type":"application/json"},
    "body":{"username":"${username}","topic":"${topic}","action":"${action}"},
    "connect_timeout":"5s","request_timeout":"5s","pool_size":8,"ssl":{"enable":false}}' >/dev/null
  mark "open HTTP 授权源已创建"
}

ensure_connector_action() { # $1=base $2=header $3=inst(E1/E2) $4=conn名 $5=action名 $6=server $7=user $8=pass
  if [ "$(get "$1/api/v5/connectors/mqtt:$4" "$2")" = "200" ]; then
    mark "$3 connector $4 已存在"
  else
    # 空凭证（匿名）时省略 username/password 字段，避免拼出非法 JSON
    auth_fields=""
    [ -n "$7" ] && auth_fields=",\"username\":\"$7\",\"password\":\"$8\""
    api POST "$1/api/v5/connectors" "$2" -d "{
      \"type\":\"mqtt\",\"name\":\"$4\",\"server\":\"$6\",\"proto_ver\":\"v5\",
      \"clean_start\":true${auth_fields}}" >/dev/null
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

sink_status() { # $1=base $2=header $3=标签 $4=sink名
  # 无 jq：对已知 sink 逐个查详情，sed 提取 status 字段（首个是动作级状态）
  S=$(curl -sf "$1/api/v5/actions/mqtt:$4" -H "$2" \
    | sed -n 's/.*"status":"\([^"]*\)".*/\1/p' | tail -1)
  echo "  $3 sink $4: ${S:-unknown}"
}

echo '======== EMQX-1（内部实例，设备接入）========'
ensure_auth_http "$E1" "$H1" "$BACKEND"
ensure_authz_http "$E1" "$H1" "$BACKEND"
# 等保改造后后端拒绝匿名 MQTT 连接（MqttAccessPolicy.authenticate 对空 username 返回 false），
# 双向桥接统一使用服务账号 materin-svc-emqx2-bridge（ACL 全放行）。
ensure_connector_action "$E1" "$H1" "E1" "conn_to_emqx2" "sink_to_emqx2" "materin-emqx2:1883" "$SVC_USER" "$SVC_PASS"
for KIND in report event reply; do
  ensure_rule "$E1" "$H1" "up_${KIND}_to_emqx2" \
    "\"SELECT concat('open/', topic) AS topic, payload FROM \\\"materin/+/+/${KIND}\\\"\"" \
    "mqtt:sink_to_emqx2"
done

echo '======== EMQX-2（open broker，第三方 AK/SK）========'
ensure_auth_http_open "$E2" "$H2" "$BACKEND"
ensure_authz_http_open "$E2" "$H2" "$BACKEND"
ensure_connector_action "$E2" "$H2" "E2" "conn_to_emqx1" "sink_to_emqx1" "materin-emqx:1883" "$SVC_USER" "$SVC_PASS"
DOWN_SQL="\"SELECT regex_replace(topic, '^open/', '') AS topic, payload FROM \\\"open/materin/+/+/cmd\\\"\""
ensure_rule "$E2" "$H2" "down_cmd_to_emqx1" "$DOWN_SQL" "mqtt:sink_to_emqx1"

echo '======== 状态汇总 ========'
sink_status "$E1" "$H1" "E1" "sink_to_emqx2"
sink_status "$E2" "$H2" "E2" "sink_to_emqx1"
echo "======== 完成 ========"
