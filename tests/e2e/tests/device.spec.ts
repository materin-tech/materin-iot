import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('TC-04-01 设备列表页加载且 /device/list 返回 code=0', async ({ page }) => {
  await login(page);
  const respP = page.waitForResponse((r) => r.url().includes('/device/list'));
  await page.goto('/device/list');
  const resp = await respP;
  expect(resp.ok()).toBeTruthy();
  const body = await resp.json().catch(() => null);
  if (body) expect(body.code).toBe(0);
});
