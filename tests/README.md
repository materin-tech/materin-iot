# 自动化测试工具集（全 Docker 运行，零宿主依赖）

> 测试设计：[docs/testing/test-design.md](../docs/testing/test-design.md)
> 用例矩阵：[docs/testing/test-cases.md](../docs/testing/test-cases.md)

## 0. 前置：起全栈（唯一依赖）

```bash
docker compose -f deploy/compose/docker-compose.yml up -d
./deploy/compose/smoke-test.sh   # 等 PASS=全部 FAIL=0
```

## 1. 后端 JMeter（API 回归 + 压测）

```bash
cd tests/jmeter
./run.sh                              # 默认 10 线程 × 20 迭代
./run.sh -Jthreads=50 -Jloops=100     # 压测档位
./run.sh -Jthreads=1 -Jloops=1        # 冒烟档位（功能回归）
```

- 计划：`plans/materin-api.jmx` — 登录(Once Only)→ 设备/产品列表读、设备创建写
- 断言：统一包络 `"code":0` + 响应头 `X-Trace-Id` 回显（全链路追踪）
- 每请求自动带 `X-Trace-Id: ${__UUID()}`，后端日志按 traceId 检索
- 报告：`reports/report/index.html`（Aggregate/Summary/APDEX）

## 2. 前端 Playwright E2E

```bash
cd tests/e2e
./run.sh                       # 全部 spec
./run.sh tests/login.spec.ts   # 单个 spec
E2E_BASE_URL=http://host.docker.internal:3080 COMPOSE_NETWORK=bridge ./run.sh
```

- specs：`login.spec.ts`（TC-01-01/02/03）、`device.spec.ts`（TC-04-01）、`trace.spec.ts`（TC-10-01）
- 报告：`playwright-report/index.html`

## 3. 后端单测（已有）

```bash
(cd backend && mvn test)   # 或 Maven 容器运行
(cd gateway && mvn test)
```

## 4. 一键全量

```bash
./tests/run-all.sh
```
