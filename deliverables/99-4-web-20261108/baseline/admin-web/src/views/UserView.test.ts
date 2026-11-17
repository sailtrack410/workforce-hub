import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const source = readFileSync(fileURLToPath(new URL('./UserView.vue', import.meta.url)), 'utf8');

describe('UserView', () => {
  it('keeps the org chart inside a scrollable canvas', () => {
    expect(source).toContain('overflow: auto;');
    expect(source).toContain('scrollbar-width: none;');
    expect(source).toContain('.org-chart::-webkit-scrollbar');
    expect(source).not.toContain('max-height: min(58vh, 620px);');
    expect(source).toContain('ref="orgViewportRef"');
    expect(source).toContain('@pointerdown="startOrgPan"');
    expect(source).toContain('@pointermove="moveOrgPan"');
    expect(source).toContain('@pointerup="endOrgPan"');
  });

  it('supports department create/delete and account detail fields', () => {
    expect(source).toContain('handleCreateDepartment');
    expect(source).toContain('handleDeleteDepartment');
    expect(source).toContain("label: '邮箱'");
  });

  it('opens an employee detail dialog when selecting an org-chart employee', () => {
    expect(source).toContain('const detailDialogOpen = ref(false)');
    expect(source).toContain('function closeUserDetail()');
    expect(source).toContain('async function openUserDetail(row: OrgUserSummaryResponse)');
    expect(source).toContain('await loadDetail(row)');
    expect(source).toContain('@select-user="openUserDetail"');
    expect(source).toContain('<AdminDialog v-model:visible="detailDialogOpen"');
    expect(source).not.toContain('scrollIntoView');
  });

  it('wraps top-level departments in one visible root node', () => {
    expect(source).toContain("const ORG_ROOT_ID = '__org_root__';");
    expect(source).toContain('const orgRootNodes = computed<OrgDepartmentTreeNodeResponse[]>');
    expect(source).toContain(':virtual-root-id="ORG_ROOT_ID"');
  });

  it('draws organization links with an svg layer', () => {
    expect(source).toContain('const orgLinks = ref<OrgLink[]>([])');
    expect(source).toContain('function redrawOrgLinks()');
    expect(source).toContain('class="org-link-layer"');
    expect(source).toContain('v-for="link in orgLinks"');
    expect(source).toContain("targetId: `user:${String(node.id)}:${String(user.userId)}`");
    expect(source).toContain('path: `M ${x1} ${y1} H ${midX} V ${y2} H ${x2}`');
    expect(source).not.toContain(' C ${');
  });
});
