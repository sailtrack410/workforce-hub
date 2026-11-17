export function trimFormValue(value: unknown) {
  return value === undefined || value === null ? '' : String(value).trim();
}
