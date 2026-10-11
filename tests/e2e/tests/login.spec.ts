import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('TC-01-01 管理员登录成功进入工作台', async ({ page }) => {
  const resp = page.waitForResponse((r) => r.url().includes('/user/info'));
  await login(page);
  const userInfo = await resp;
  expect(userInfo.ok()).toBeTruthy();
  await expect(page).not.toHaveURL(/login/);
});

test('TC-01-02 错误密码登录被拒绝', async ({ page }) => {
  await page.goto('/auth/login');
  await page.fill('input[name="username"], input[type="text"]', 'admin');
  await page.fill('input[type="password"]', 'wrong-password');
  const respP = page.waitForResponse((r) => r.url().includes('/auth/login'));
  await page.getByRole('button', { name: /登录|login|sign in/i }).first().click();
  const resp = await respP;
  const body = await resp.json().catch(() => null);
  expect(body?.code).not.toBe(0);
  await expect(page).toHaveURL(/login/);
});

test('TC-01-03 未登录访问受保护路由重定向登录页', async ({ page }) => {
  await page.goto('/device/list');
  await expect(page).toHaveURL(/login/);
});
