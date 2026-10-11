import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('TC-10-01 业务请求携带 X-Trace-Id 且响应头回显同值', async ({ page }) => {
  const respP = page.waitForResponse((r) => r.url().includes('/api/'));
  await login(page);
  const resp = await respP;
  const reqHeaders = resp.request().headers();
  const sent = reqHeaders['x-trace-id'];
  const echoed = resp.headers()['x-trace-id'];
  expect(sent, '请求头应含 X-Trace-Id').toBeTruthy();
  expect(echoed, '响应头应回显 X-Trace-Id').toBeTruthy();
  expect(sent).toBe(echoed);
});
