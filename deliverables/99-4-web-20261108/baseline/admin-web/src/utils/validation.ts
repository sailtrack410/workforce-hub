export function requiredText(value: string, label: string) {
  return value.trim() ? '' : `请输入${label}`;
}

export function maxLength(value: string, max: number, label: string) {
  return value.trim().length <= max ? '' : `${label}不能超过 ${max} 个字符`;
}

export function codeFormat(value: string, label: string) {
  return /^[A-Z][A-Z0-9_]*$/.test(value.trim()) ? '' : `${label}仅支持大写字母、数字和下划线，且需以字母开头`;
}

export function mobileFormat(value: string) {
  const mobile = value.trim();
  if (!mobile) return '';
  return /^\+?[0-9][0-9 -]{5,30}[0-9]$/.test(mobile) ? '' : '请输入有效手机号，仅支持数字、空格、短横线和国际区号';
}
