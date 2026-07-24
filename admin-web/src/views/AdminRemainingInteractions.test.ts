import { describe, expect, it } from 'vitest';
import permissionSource from './PermissionView.vue?raw';
import auditSource from './AuditLogView.vue?raw';
import userSource from './UserView.vue?raw';

describe('admin remaining interaction contracts', () => {
  it('keeps permission assignment in role configuration', () => {
    expect(permissionSource).toContain("router.push('/roles')");
    expect(permissionSource).toContain('roleNames(row.permissionId)');
    expect(permissionSource).not.toContain('createPermission');
  });

  it('supports audit pagination and detail inspection', () => {
    expect(auditSource).toContain('<AdminPagination');
    expect(auditSource).toContain('detailVisible.value = true');
    expect(auditSource).toContain('prettyJson(selected.detailJson)');
  });

  it('shows field-level employee validation', () => {
    expect(userSource).toContain('userCreateErrors');
    expect(userSource).toContain('aria-invalid');
    expect(userSource).toContain('userCreateInvalid');
  });
});
