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
