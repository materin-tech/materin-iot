/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { requestClient } from '#/api/request';

export namespace SystemConfigApi {
  /** 等保三级安全参数（后端 sys_config 表语义） */
  export interface SecurityConfig {
    passwordMinLength: number;
    passwordRequireUppercase: boolean;
    passwordRequireLowercase: boolean;
    passwordRequireDigit: boolean;
    passwordRequireSpecial: boolean;
    passwordExpireDays: number;
    passwordRemindDays: number;
    lockoutMaxFails: number;
    lockoutDurationMinutes: number;
  }
}

/** 查询等保三级安全参数 */
async function getSecurityConfig() {
  return requestClient.get<SystemConfigApi.SecurityConfig>(
    '/system/config/security',
  );
}

/** 更新等保三级安全参数 */
async function updateSecurityConfig(
  data: Partial<SystemConfigApi.SecurityConfig>,
) {
  return requestClient.put('/system/config/security', data);
}

export { getSecurityConfig, updateSecurityConfig };
