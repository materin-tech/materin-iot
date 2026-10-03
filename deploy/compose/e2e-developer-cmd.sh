#!/usr/bin/env bash
# 闭环测试：开发者(EMQX-2) 下发指令 → 集成管道 → 模拟设备(EMQX-1) 执行 → reply 回传开发者
set -u
E1_PORT=${E1_PORT:-1883}
SECRET=$(docker exec materin-mysql mysql -uroot -proot materin -N -e \
  "SELECT secret FROM device WHERE device_key='th-001'" 2>/dev/null | tr -d ' ')
PK=$(docker exec materin-mysql mysql -uroot -proot materin -N -e \
  "SELECT product_key FROM product WHERE id=(SELECT product_id FROM device WHERE device_key='th-001')" 2>/dev/null | tr -d ' ')
DK=th-001
MOSQ="docker.m.daocloud.io/library/eclipse-mosquitto:2.0"
NET=materin_default
PASS=0; FAIL=0
ok() { echo "PASS  $1"; PASS=$((PASS+1)); }
bad() { echo "FAIL  $1"; FAIL=$((FAIL+1)); }

# ---------- 启动模拟设备 ----------
# 订阅 cmd；收到后提取 requestId、执行"动作"（echo 日志）、回 reply（原样回带 requestId）
start_device() {
  docker run --rm --name e2e-fake-device --network $NET $MOSQ sh -c '
    SECRET='"$SECRET"'; PK='"$PK"'; DK='"$DK"'
    mosquitto_sub -h materin-emqx -p 1883 -u "$DK" -P "$SECRET" \
      -t "materin/$PK/$DK/cmd" -W 25 -F "%p" | while read -r payload; do
      rid=$(printf "%s" "$payload" | sed "s/.*\"requestId\":\"\([^\"]*\)\".*/\1/")
      [ "$rid" = "$payload" ] && rid=""   # 无 requestId 时不回带
      echo "[设备] 收到指令: $payload (requestId=$rid)" >&2
      sleep 0.3
      mosquitto_pub -h materin-emqx -p 1883 -u "$DK" -P "$SECRET" \
        -t "materin/$PK/$DK/reply" \
        -m "{\"requestId\":\"$rid\",\"code\":0,\"result\":\"action-done\",\"echo\":$payload}" >&2
    done
  ' &
}
# ---------- 开发者会话（EMQX-2）----------
developer_flow() {
  local rid=$1
  docker run --rm --network $NET $MOSQ sh -c '
    PK='"$PK"'; DK='"$DK"'; RID='"$rid"'
    mosquitto_sub -h materin-emqx2 -p 1883 \
      -t "open/materin/$PK/$DK/reply" -C 1 -W 10 > /tmp/dev_reply.json 2>/dev/null &
    SUBPID=$!
    sleep 1
    mosquitto_pub -h materin-emqx2 -p 1883 \
      -t "open/materin/$PK/$DK/cmd" \
      -m "{\"requestId\":\"$RID\",\"action\":\"open-valve\",\"channel\":1}" -q 1
    wait $SUBPID
    cat /tmp/dev_reply.json
  '
}
echo '== 阶段1: 设备上线（report 刷在线态）=='
docker run --rm --network $NET $MOSQ mosquitto_pub -h materin-emqx -p 1883 -u $DK -P "$SECRET" \
  -t "materin/$PK/$DK/report" -m '{"online-probe":1}' >/dev/null 2>&1
sleep 1
echo '== 阶段2: 闭环——开发者下发 → 设备执行 → reply 回传 =='
start_device
sleep 2
REPLY=$(developer_flow dev-e2e-001)
wait 2>/dev/null
echo "开发者收到的 reply: $REPLY"
echo "$REPLY" | grep -q '"requestId":"dev-e2e-001"' && ok "requestId 闭环关联" || bad "requestId 闭环关联"
echo "$REPLY" | grep -q '"result":"action-done"' && ok "设备执行结果回传" || bad "设备执行结果回传"
echo "$REPLY" | grep -q '"action":"open-valve"' && ok "原始指令 echo 回传" || bad "原始指令 echo 回传
"
echo '== 阶段3: 平台内部链路回归（HTTP /device/4/cmd，同一模拟设备）=='
start_device
sleep 2
INNER=$(curl -s -m 15 -X POST http://localhost:8080/api/v1/device/4/cmd -H 'Content-Type: application/json' -d '{"action":"status"}')
echo "平台链路回执: $INNER"
echo "$INNER" | grep -q 'action-done' && ok "平台 HTTP 链路回归" || bad "平台 HTTP 链路回归"
echo '== 阶段4: 设备数据对开发者可见（report 转发）=='
REPORT=$(docker run --rm --network $NET $MOSQ sh -c '
  mosquitto_sub -h materin-emqx2 -p 1883 -t "open/materin/'$PK'/'$DK'/report" -C 1 -W 6 > /tmp/dev_report.txt 2>/dev/null &
  sleep 1
  mosquitto_pub -h materin-emqx -p 1883 -u '$DK' -P '"$SECRET"' -t "materin/'$PK'/'$DK'/report" -m "{\"temperature\":31.2}"
  wait
  cat /tmp/dev_report.txt
')
echo "开发者收到的 report: $REPORT"
echo "$REPORT" | grep -q '31.2' && ok "report 对开发者透传" || bad "report 对开发者透传"
echo '----'
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
