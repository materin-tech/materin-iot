import type { Page } from '@playwright/test';

/** UI 登录助手：admin + ADMIN_PASSWORD（容器 env 注入，默认开发值） */
export async function login(page: Page) {
  const username = process.env.E2E_USERNAME ?? 'admin';
  const password = process.env.ADMIN_PASSWORD ?? 'Admin@123';
  await page.goto('/');
  await page.goto('/auth/login');
  await page.fill('input[name="username"], input[type="text"]', username);
  await page.fill('input[type="password"]', password);
  await page.getByRole('button', { name: /登录|login|sign in/i }).first().click();
}
