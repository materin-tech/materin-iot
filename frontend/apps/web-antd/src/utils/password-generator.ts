/** 生成密码的总长度（等保三级要求至少 8 位，这里取 12 位增强强度） */
const PASSWORD_LENGTH = 12;

/**
 * 基于安全随机源在 [0, max) 范围内取随机整数（拒绝采样，避免取模偏差）
 */
function randomInt(max: number): number {
  const range = 256 - (256 % max);
  const buffer = new Uint8Array(1);
  let value = 0;
  do {
    crypto.getRandomValues(buffer);
    value = buffer[0]!;
  } while (value >= range);
  return value % max;
}

/**
 * Fisher-Yates 洗牌，返回新数组（不改变原数组）
 */
function shuffle<T>(list: T[]): T[] {
  const result = [...list];
  for (let i = result.length - 1; i > 0; i--) {
    const j = randomInt(i + 1);
    [result[i], result[j]] = [result[j]!, result[i]!];
  }
  return result;
}

/** 等保三级：生成符合复杂度要求的随机密码（大写+小写+数字+特殊字符） */
export function generateComplexPassword(): string {
  const sets = [
    'ABCDEFGHJKLMNPQRSTUVWXYZ',
    'abcdefghijkmnpqrstuvwxyz',
    '23456789',
    '!@#$%^&*',
  ];
  const all = sets.join('');
  // 每组字符集先各随机取 1 位，保证四类字符齐备
  const picks = sets.map((set) => set[randomInt(set.length)]!);
  // 剩余位数从全集中随机补齐
  while (picks.length < PASSWORD_LENGTH) {
    picks.push(all[randomInt(all.length)]!);
  }
  // 打乱顺序，避免四类字符固定出现在开头
  return shuffle(picks).join('');
}
