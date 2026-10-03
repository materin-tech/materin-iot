#!/usr/bin/env bash
# 双 EMQX 集成（EMQX 5.8 connectors + actions/sink + rules，幂等）
# 上行：EMQX-1 materin/+/+/report|event → EMQX-2 open/materin/...
# 下行：EMQX-2 open/materin/+/+/cmd → EMQX-1 materin/...
set -euo pipefail
E1=${EMQX1_HOST:-http://localhost:18083}
E2=${EMQX2_HOST:-http://localhost:18084}
DASH_USER=${EMQX_DASH_USER:-admin}
DASH_PASS=${EMQX_ADMIN_PASSWORD:-public}
SVC_USER=${MATERIN_MQTT_SERVICE_USERNAME:-materin-svc-emqx2-bridge}
SVC_PASS=${MATERIN_MQTT_SERVICE_PASSWORD:-materin-svc-dev-password}

token() { curl -sf -X POST "$1/api/v5/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$DASH_USER\",\"password\":\"$DASH_PASS\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])'; }
T1=$(token "$E1"); T2=$(token "$E2")
H1="Authorization: Bearer $T1"; H2="Authorization: Bearer $T2"
api() { local m=$1 u=$2 h=$3; shift 3; curl -sf -X "$m" "$u" -H "$h" -H 'Content-Type: application/json' "$@"; }
get()  { curl -sf -o /dev/null -w '%{http_code}' "$1" -H "$2"; }

echo '== EMQX-1: connector + sink → EMQX-2 =='
if [ "$(get "$E1/api/v5/connectors/mqtt:conn_to_emqx2" "$H1")" = "200" ]; then
  echo "connector conn_to_emqx2 已存在"
else
  api POST "$E1/api/v5/connectors" "$H1" -d '{"type":"mqtt","name":"conn_to_emqx2","server":"materin-emqx2:1883","proto_ver":"v5","clean_start":true,"username":"","password":""}' >/dev/null
  echo "connector conn_to_emqx2 已创建"
fi
if [ "$(get "$E1/api/v5/actions/mqtt:sink_to_emqx2" "$H1")" = "200" ]; then
  echo "sink sink_to_emqx2 已存在"
else
  api POST "$E1/api/v5/actions" "$H1" -d '{"type":"mqtt","name":"sink_to_emqx2","connector":"conn_to_emqx2","parameters":{"topic":"${topic}","payload":"${payload}"}}' >/dev/null
  echo "sink sink_to_emqx2 已创建"
fi

echo '== EMQX-1: 上行规则 =='
for KIND in report event reply; do
  RULE_ID="up_${KIND}_to_emqx2"
  if [ "$(get "$E1/api/v5/rules/$RULE_ID" "$H1")" = "200" ]; then
    echo "rule $RULE_ID 已存在"
  else
    api POST "$E1/api/v5/rules" "$H1" -d '{"id":"'"$RULE_ID"'","sql":"SELECT concat('"'"'open/'"'"', topic) AS topic, payload FROM \"materin/+/+/'"$KIND"'\"","actions":["mqtt:sink_to_emqx2"]}' >/dev/null
    echo "rule $RULE_ID 已创建"
  fi
done

echo '== EMQX-2: connector + sink → EMQX-1 =='
if [ "$(get "$E2/api/v5/connectors/mqtt:conn_to_emqx1" "$H2")" = "200" ]; then
  echo "connector conn_to_emqx1 已存在"
else
  api POST "$E2/api/v5/connectors" "$H2" -d '{"type":"mqtt","name":"conn_to_emqx1","server":"materin-emqx:1883","proto_ver":"v5","clean_start":true,"username":"'"$SVC_USER"'","password":"'"$SVC_PASS"'"}' >/dev/null
  echo "connector conn_to_emqx1 已创建"
fi
if [ "$(get "$E2/api/v5/actions/mqtt:sink_to_emqx1" "$H2")" = "200" ]; then
  echo "sink sink_to_emqx1 已存在"
else
  api POST "$E2/api/v5/actions" "$H2" -d '{"type":"mqtt","name":"sink_to_emqx1","connector":"conn_to_emqx1","parameters":{"topic":"${topic}","payload":"${payload}"}}' >/dev/null
  echo "sink sink_to_emqx1 已创建"
fi

echo '== EMQX-2: 下行规则 =='
if [ "$(get "$E2/api/v5/rules/down_cmd_to_emqx1" "$H2")" = "200" ]; then
  echo "rule down_cmd_to_emqx1 已存在"
else
  api POST "$E2/api/v5/rules" "$H2" -d '{"id":"down_cmd_to_emqx1","sql":"SELECT regex_replace(topic, '"'"'\^open/'"'"', '"'"''"'"') AS topic, payload FROM \"open/materin/+/+/cmd\"","actions":["mqtt:sink_to_emqx1"]}' >/dev/null
  echo "rule down_cmd_to_emqx1 已创建"
fi

echo '== EMQX-2: AK/SK 鉴权（HTTP 认证+授权回调 backend）=='
if [ "$(get "$E2/api/v5/authentication/password_based:http" "$H2")" != "200" ]; then
  api POST "$E2/api/v5/authentication" "$H2" -d '{"mechanism":"password_based","backend":"http","enable":true,"url":"http://materin-backend:8080/api/v1/mqtt/auth","method":"post","headers":{"content-type":"application/json"},"body":{"username":"${username}","password":"${password}"},"connect_timeout":"5s","request_timeout":"5s","pool_size":8,"ssl":{"enable":false}}' >/dev/null
  echo "emqx2 authentication 已创建"
else
  echo "emqx2 authentication 已存在"
fi
if [ "$(get "$E2/api/v5/authorization/sources/http" "$H2")" != "200" ]; then
  api POST "$E2/api/v5/authorization/sources" "$H2" -d '{"type":"http","enable":true,"url":"http://materin-backend:8080/api/v1/mqtt/acl","method":"post","headers":{"content-type":"application/json"},"body":{"username":"${username}","topic":"${topic}","action":"${action}","clientid":"${clientid}"},"connect_timeout":"5s","request_timeout":"5s","pool_size":8,"ssl":{"enable":false}}' >/dev/null
  echo "emqx2 authorization 已创建"
else
  echo "emqx2 authorization 已存在"
fi

echo '== 状态汇总 =='
curl -sf "$E1/api/v5/actions" -H "$H1" | python3 -c 'import json,sys; [print("E1 sink:", a["name"], a["status"]) for a in json.load(sys.stdin)]'
curl -sf "$E2/api/v5/actions" -H "$H2" | python3 -c 'import json,sys; [print("E2 sink:", a["name"], a["status"]) for a in json.load(sys.stdin)]'
curl -sf "$E1/api/v5/rules" -H "$H1" | python3 -c 'import json,sys; d=json.load(sys.stdin); rs=d if isinstance(d,list) else d.get("data",d.get("rules",[])); [print("E1 rule:", r["id"]) for r in rs if str(r.get("id","")).startswith("up_")]'
curl -sf "$E2/api/v5/rules" -H "$H2" | python3 -c 'import json,sys; d=json.load(sys.stdin); rs=d if isinstance(d,list) else d.get("data",d.get("rules",[])); [print("E2 rule:", r["id"]) for r in rs if str(r.get("id","")).startswith("down_")]'
