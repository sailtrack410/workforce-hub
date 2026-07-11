import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const roleSource = readSource('./RoleView.vue');
const workflowSource = readSource('./WorkflowGovernanceView.vue');
const tableSource = readSource('../components/AdminTable.vue');
const dialogSource = readSource('../components/AdminDialog.vue');

describe('admin P0 interactions', () => {
  it('protects the built-in super admin role and confirms destructive changes', () => {
    expect(roleSource).toContain("selectedRole.value?.roleCode === 'SUPER_ADMIN'");
    expect(roleSource).toContain('if (isBuiltInRole.value || saving.value) return');
    expect(roleSource).toContain('v-model:visible="deleteConfirmVisible"');
    expect(roleSource).toContain('affectedAdminUsers.length');
    expect(roleSource).toContain('v-model:visible="assignConfirmVisible"');
    expect(roleSource).toContain('addedAdminRoles');
    expect(roleSource).toContain('removedAdminRoles');
  });

  it('requires impact confirmation before publishing a workflow template', () => {
    expect(workflowSource).toContain('function requestPublishTemplateVersion');
    expect(workflowSource).toContain('publishConfirmVisible.value = true');
    expect(workflowSource).toContain('v-model:visible="publishConfirmVisible"');
    expect(workflowSource).toContain('formConfigChanged');
    expect(workflowSource).toContain('ruleConfigChanged');
    expect(workflowSource).toContain('@confirm="publishTemplateVersion"');
  });

  it('keeps interactive tables and dialogs keyboard accessible', () => {
    expect(tableSource).toContain(':tabindex="interactive ? 0 : undefined"');
    expect(tableSource).toContain('@keydown.enter.prevent="interactive && handleRowClick(row)"');
    expect(tableSource).toContain('@keydown.space.prevent="interactive && handleRowClick(row)"');
    expect(tableSource).toContain('<button v-if="col.sortable"');
    expect(dialogSource).toContain("if (event.key === 'Escape')");
    expect(dialogSource).toContain("if (event.key !== 'Tab') return");
    expect(dialogSource).toContain("document.querySelectorAll<HTMLElement>('.admin-dialog')");
    expect(dialogSource).toContain('previousFocus?.focus()');
  });
});

function readSource(path: string) {
  return readFileSync(fileURLToPath(new URL(path, import.meta.url)), 'utf8');
}
