#!/usr/bin/env bash
# 一键全量：冒烟 → JMeter 冒烟档 → Playwright E2E
set -euo pipefail
cd "$(dirname "$0")"
echo "== [1/3] compose 冒烟 =="
../deploy/compose/smoke-test.sh
echo "== [2/3] JMeter 功能回归 =="
jmeter/run.sh -Jthreads=1 -Jloops=1
echo "== [3/3] Playwright E2E =="
e2e/run.sh
echo "ALL PASSED"
