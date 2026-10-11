import { defineConfig } from '@playwright/test';

// 被测地址：容器网络内服务名（run.sh 已将容器加入 compose 网络）
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  retries: 1,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://frontend:80',
    trace: 'retain-on-failure',
  },
  reporter: [['list'], ['html', { open: 'never' }]],
});
