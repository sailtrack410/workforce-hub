import { describe, expect, it } from 'vitest';
import { contactsTreeNodesForView } from './contact-category';
import type { ContactRecord, OrgDepartmentTreeNodeResponse } from '@/stores/shell';

describe('contactsTreeNodesForView', () => {
  it('keeps only current employee department path and appends department members', () => {
    const leaf: OrgDepartmentTreeNodeResponse = {
      id: 3,
      parentId: 2,
      deptName: '演示员工组',
      children: [],
    };
    const center: OrgDepartmentTreeNodeResponse = {
      id: 2,
      parentId: 1,
      deptName: '研发中心',
      children: [leaf],
    };
    const root: OrgDepartmentTreeNodeResponse = {
      id: 1,
      deptName: '总部',
      children: [center],
    };
    const contacts = [
      contact('user-1', '张三', 3),
      contact('user-2', '李四', 3),
      contact('user-3', '王五', 2),
    ];

    const tree = contactsTreeNodesForView([root], contacts, 3);

    expect(tree).toHaveLength(1);
    expect(tree[0].children[0].children[0].children.map((node) => node.label)).toEqual(['张三', '李四']);
  });

  it('marks same department members as sibling contact nodes', () => {
    const leaf: OrgDepartmentTreeNodeResponse = {
      id: 3,
      parentId: 2,
      deptName: '演示员工组',
      children: [],
    };
    const root: OrgDepartmentTreeNodeResponse = {
      id: 2,
      deptName: '研发中心',
      children: [leaf],
    };

    const tree = contactsTreeNodesForView([root], [
      contact('user-1', '张三', 3),
      contact('user-2', '李四', 3),
    ], 3);

    const members = tree[0].children[0].children;
    expect(members.every((node) => node.kind === 'contact')).toBe(true);
    expect(members.map((node) => node.label)).toEqual(['张三', '李四']);
  });
});

function contact(id: string, name: string, primaryDeptId: number): ContactRecord {
  return {
    id,
    name,
    role: '工程师',
    department: '演示员工组',
    primaryDeptId,
    relation: '同部门',
    source: '组织通讯录',
  };
}
