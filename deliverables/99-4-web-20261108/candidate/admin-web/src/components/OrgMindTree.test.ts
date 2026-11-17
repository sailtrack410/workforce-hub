import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import OrgMindTree from './OrgMindTree.vue';

const source = readFileSync(fileURLToPath(new URL('./OrgMindTree.vue', import.meta.url)), 'utf8');

describe('OrgMindTree', () => {
  it('is a recursive organization tree component', () => {
    expect(OrgMindTree).toBeTruthy();
    expect((OrgMindTree as { __name?: string }).__name).toBe('OrgMindTree');
  });

  it('collapses child departments until their parent is expanded', () => {
    expect(source).toContain('function hasVisibleChildren(node: OrgDepartmentTreeNodeResponse)');
    expect(source).toContain('return isExpanded(node.id) && Boolean(node.children?.length);');
    expect(source).toContain('v-if="hasVisibleChildren(node)"');
  });

  it('exposes department add and delete actions', () => {
    expect(source).toContain('add-department');
    expect(source).toContain('delete-department');
  });

  it('renders employees as linked nodes and exposes link ports for branches', () => {
    expect(source).toContain('class="org-branch"');
    expect(source).toContain('class="org-children"');
    expect(source).toContain('class="org-people-branch"');
    expect(source).toContain('data-org-node-id');
    expect(source).toContain('data-org-user-id');
    expect(source).toContain('employeeNodeId(node.id, user.userId)');
    expect(source).toContain('class="org-link-port left"');
    expect(source).toContain('class="org-link-port right"');
    expect(source).not.toContain('max-height: 238px');
    expect(source).not.toContain('.org-branch.has-children::before');
    expect(source).not.toContain('.org-children::before');
  });
});
