import type { ContactRecord, OrgDepartmentTreeNodeResponse } from '@/stores/shell';

export interface ContactCategoryItem {
  id: string;
  type: 'category' | 'dept' | 'ai' | 'service';
  label: string;
  icon?: string;
  deptId?: string | number;
  deptName?: string;
  children?: ContactCategoryItem[];
  badge?: number;
}

export interface ContactTreeNode {
  id: string;
  kind: 'dept' | 'contact';
  label: string;
  subLabel?: string;
  dept?: OrgDepartmentTreeNodeResponse;
  contact?: ContactRecord;
  children: ContactTreeNode[];
}

// 从部门树构建通讯录分类项：递归转换为前端树形结构
export function buildCategoryItems(deptTree: OrgDepartmentTreeNodeResponse[]): ContactCategoryItem[] {
  return deptTree.map((node) => ({
    id: `dept-${node.id}`,
    type: 'dept' as const,
    label: node.deptName,
    icon: 'folder',
    deptId: node.id,
    deptName: node.deptName,
    children: node.children?.length ? buildCategoryItems(node.children) : undefined,
  }));
}

// 构建通讯录视图树：定位当前部门路径，将该部门的联系人挂到树节点下
export function contactsTreeNodesForView(
  deptTree: OrgDepartmentTreeNodeResponse[],
  contacts: ContactRecord[],
  currentDeptId?: string | number | null,
): ContactTreeNode[] {
  const deptId = currentDeptId == null ? contacts.find((contact) => contact.relation === '本人')?.primaryDeptId : currentDeptId;
  if (deptId == null) return deptTree.map(deptTreeNode);
  const path = findDeptPath(String(deptId), deptTree);
  if (!path.length) return deptTree.map(deptTreeNode);
  let childNodes: ContactTreeNode[] = contacts
    .filter((contact) => String(contact.primaryDeptId) === String(deptId))
    .map((contact) => ({
      id: `contact-${contact.id}`,
      kind: 'contact' as const,
      label: contact.name,
      subLabel: contact.role || contact.relation,
      contact,
      children: [],
    }));
  for (let i = path.length - 1; i >= 0; i--) {
    childNodes = [{
      ...deptTreeNode(path[i]),
      children: childNodes,
    }];
  }
  return childNodes;
}

function deptTreeNode(dept: OrgDepartmentTreeNodeResponse): ContactTreeNode {
  return {
    id: `dept-${dept.id}`,
    kind: 'dept',
    label: dept.deptName,
    subLabel: dept.leaderName ? `负责人：${dept.leaderName}` : undefined,
    dept,
    children: dept.children?.map(deptTreeNode) ?? [],
  };
}

function findDeptPath(targetId: string, nodes: OrgDepartmentTreeNodeResponse[]): OrgDepartmentTreeNodeResponse[] {
  for (const node of nodes) {
    if (String(node.id) === targetId) return [node];
    const childPath = findDeptPath(targetId, node.children ?? []);
    if (childPath.length) return [node, ...childPath];
  }
  return [];
}
