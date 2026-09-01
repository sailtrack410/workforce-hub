import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const source = readFileSync(fileURLToPath(new URL('./DictView.vue', import.meta.url)), 'utf8');

describe('DictView', () => {
  it('switches dict types and renders the item table', () => {
    expect(source).toContain('<AdminFilterBar :show-search="false">');
    expect(source).toContain('<AdminTable :columns="columns" :rows="items"');
    expect(source).toContain('adminApi.dicts.types()');
    expect(source).toContain('adminApi.dicts.items(activeDictType.value)');
    expect(source).toContain('row-key="id"');
  });

  it('creates and edits items in a dialog with dictType fixed to the active type', () => {
    expect(source).toContain('<AdminDialog');
    expect(source).toContain("editId ? '编辑字典条目' : '新增字典条目'");
    expect(source).toContain('<input :value="activeDictType" disabled />');
    expect(source).toContain('dictType: activeDictType.value');
    expect(source).toContain('adminApi.dicts.create(request)');
    expect(source).toContain('adminApi.dicts.update(editId.value, request)');
    expect(source).toContain('status: form.status ? 1 : 0');
  });

  it('confirms deletion via AdminConfirmDialog instead of window.confirm', () => {
    expect(source).toContain('<AdminConfirmDialog');
    expect(source).toContain('adminApi.dicts.remove(row.id)');
    expect(source).not.toContain('window.confirm');
  });

  it('guards required fields and duplicate item codes', () => {
    expect(source).toContain('Boolean(form.itemCode.trim() && form.itemLabel.trim())');
    expect(source).toContain('该编码在当前字典类型下已存在');
  });

  it('styles only with design tokens and no hardcoded colors', () => {
    expect(source).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
    expect(source).toContain('var(--red-dark)');
    expect(source).toContain('var(--space-sm)');
  });
});
