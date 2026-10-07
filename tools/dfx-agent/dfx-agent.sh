#!/usr/bin/env bash
# 格物 DFX 参考采集器（docs/dfx-monitoring-design.md §1.3/§4）
# Linux 主机 CPU/内存/负载/磁盘采集，支持 MQTT / HTTP 双模式上报。
# 用法:
#   MODE=mqtt HOST=127.0.0.1 DEVICE_KEY=th-001 DEVICE_SECRET=<secret> ./dfx-agent.sh
#   MODE=http URL=http://platform:8080/api/v1/device/dfx/report DEVICE_KEY=th-001 DEVICE_SECRET=... ./dfx-agent.sh
# 依赖: mosquitto_pub(MQTT 模式) 或 curl(HTTP 模式) + free/df/top/awk
set -euo pipefail
MODE=${MODE:-mqtt}
INTERVAL=${INTERVAL:-60}
# /proc/stat 两次采样计算 CPU 使用率（比解析 top 更可靠）
CPU_A=$(awk '/^cpu / {print $2+$3+$4+$6+$7+$8, $5}' /proc/stat)
sleep 0.5
CPU_B=$(awk '/^cpu / {print $2+$3+$4+$6+$7+$8, $5}' /proc/stat)
read -r MEM_TOTAL MEM_AVAIL <<<"$(free -k | awk '/^Mem:/ {print $2, $7}')"
LOAD1=$(awk '{print $1}' /proc/loadavg)
DISK_PCT=$(df -P / | awk 'NR==2 {gsub("%","",$5); print $5}')
UPTIME_S=$(awk '{print int($1)}' /proc/uptime)
CPU_PCT=$(awk -v a="$CPU_A" -v b="$CPU_B" 'BEGIN{
  split(a, A, " "); split(b, B, " ")
  d = (B[1]-A[1]); i = (B[2]-A[2])
  u = (d+i>0)? (1 - i/(d+i))*100 : 0
  printf "%.1f", (u<0?0:u>100?100:u)
}')
MEM_PCT=$(awk -v t="$MEM_TOTAL" -v a="$MEM_AVAIL" 'BEGIN{printf "%.1f", (t>0)?(t-a)/t*100:0}')
PAYLOAD=$(printf '{"cpu_usage_pct":%s,"mem_usage_pct":%s,"disk_usage_pct":%s,"load_1m":%s,"uptime_s":%s}' \
  "$CPU_PCT" "$MEM_PCT" "$DISK_PCT" "$LOAD1" "$UPTIME_S")

if [ "$MODE" = "http" ]; then
  URL=${URL:-http://127.0.0.1:8080/api/v1/device/dfx/report}
  while :; do
    curl -sf -X POST "$URL" -H 'content-type: application/json' \
      -d "{\"deviceKey\":\"$DEVICE_KEY\",\"secret\":\"$DEVICE_SECRET\",\"metrics\":$PAYLOAD}" >/dev/null \
      || echo "$(date '+%F %T') HTTP 上报失败" >&2
    sleep "$INTERVAL"
  done
else
  PRODUCT_KEY=${PRODUCT_KEY:?PRODUCT_KEY required for mqtt mode}
  TOPIC=${TOPIC:-materin/$PRODUCT_KEY/$DEVICE_KEY/dfx}
  while :; do
    mosquitto_pub -h "$HOST" -p "${PORT:-1883}" -u "$DEVICE_KEY" -P "$DEVICE_SECRET" \
      -t "$TOPIC" -m "$PAYLOAD" \
      || echo "$(date '+%F %T') MQTT 上报失败" >&2
    sleep "$INTERVAL"
  done
fi
