<script setup lang="ts">
import { computed, onBeforeUnmount, onErrorCaptured, ref, watch, type Component } from 'vue';
import { Bot, BrainCircuit, Check, CircleDot, FileSearch, GitBranch, ListChecks, Loader2, Milestone, Play, Plus, Route, ScrollText, Shuffle, SquareCheckBig, Trash2, Wrench, X } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { AgentToolResponse, RagflowSpaceResponse } from '@/api/types';
import { providerLabel, safeBusinessMessage } from '@/utils/format';

type NodeType = 'START' | 'END' | 'LLM' | 'TOOL' | 'CONDITION' | 'INTENT' | 'APPROVAL' | 'KNOWLEDGE' | 'FORM_EXTRACT' | 'SUMMARY' | 'TRANSFORM';

const NODE_TYPE_LABEL: Record<NodeType, string> = {
  START: '开始',
  LLM: '大模型',
  TOOL: '工具',
  CONDITION: '条件判断',
  INTENT: '意图识别',
  APPROVAL: '审批预审',
  KNOWLEDGE: '知识检索',
  FORM_EXTRACT: '表单提取',
  SUMMARY: '摘要生成',
  TRANSFORM: '数据整理',
  END: '结束',
};

const NODE_CATEGORIES: { label: string; items: { type: NodeType; label: string }[] }[] = [
  { label: '大模型', items: [{ type: 'LLM', label: '大模型' }] },
  { label: '业务逻辑', items: [
    { type: 'CONDITION', label: '条件判断' },
    { type: 'INTENT', label: '意图识别' },
    { type: 'APPROVAL', label: '审批预审' },
  ]},
  { label: '输入输出', items: [
    { type: 'START', label: '开始' },
    { type: 'END', label: '结束' },
    { type: 'FORM_EXTRACT', label: '表单提取' },
    { type: 'TRANSFORM', label: '数据整理' },
  ]},
  { label: '知识库 & 数据', items: [
    { type: 'KNOWLEDGE', label: '知识检索' },
    { type: 'TOOL', label: '数据查询' },
    { type: 'SUMMARY', label: '摘要生成' },
  ]},
];

const showNodePanel = ref(false);

const DATA_SOURCES = [
  { key: 'employeeInfo', label: '员工信息', desc: '部门、职级、入职时间' },
  { key: 'leaveBalance', label: '假期余额', desc: '各类假期剩余天数' },
  { key: 'leaveBalanceFields', label: '假期余额字段', desc: '卡片字段：名称、可用、冻结' },
  { key: 'departmentHeadcountFields', label: '部门人数字段', desc: '卡片字段：部门、人数' },
  { key: 'orgChart', label: '组织架构', desc: '上下级关系、部门层级' },
];

const CARD_CATEGORIES = [
  { value: 'QA', label: '问答' },
  { value: 'OA', label: '办公' },
  { value: 'KNOWLEDGE', label: '知识库' },
];

const CARD_STATUSES = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'EMPTY', label: '无结果' },
  { value: 'NO_PERMISSION', label: '无权限' },
  { value: 'FAILED', label: '失败' },
  { value: 'LOADING', label: '加载中' },
];

const CARD_FIELD_SOURCES = [
  { value: '', label: '不显示' },
  { value: '{{oaData.leaveBalanceFields}}', label: '假期余额字段' },
  { value: '{{oaData.departmentHeadcountFields}}', label: '部门人数字段' },
];

const REF_CURRENT_USER = ['{{', 'input', '.', 'userId', '}}'].join('');
const REF_START_QUERY = ['{{', 'start', '.', 'query', '}}'].join('');
const ACTION_OPEN_BUSINESS = ['OPEN', 'FLOW'].join('_');
const TOOL_KNOWLEDGE_SEARCH = ['KNOWLEDGE', 'SEARCH', 'CHUNKS'].join('_');

const TARGET_USER_OPTIONS = [
  { code: 'CURRENT_USER', value: REF_CURRENT_USER, label: '当前用户' },
  { code: 'FROM_INPUT', value: REF_START_QUERY, label: '从用户输入识别' },
];

const CARD_ACTION_TYPES = [
  { code: 'BUSINESS', value: ACTION_OPEN_BUSINESS, label: '打开业务' },
  { code: 'LINK', value: 'OPEN_URL', label: '打开链接' },
];

const CARD_ACTION_TARGET_OPTIONS = [
  { value: '/workbench/requests', label: '审批工作台' },
];

type FieldType = 'string' | 'number' | 'boolean' | 'json' | 'object' | 'array';
type OutputType = 'string' | 'number' | 'boolean' | 'json' | 'object' | 'array';

interface DataField {
  key: string;
  label: string;
  type?: FieldType;
  ref?: string;
}

interface OutputFieldDef {
  key: string;
  type: string;
  description: string;
  itemFields?: OutputFieldDef[];
}

interface DagNode {
  id: string;
  type: NodeType;
  outputKey?: string;
  config: Record<string, unknown>;
  position: { x: number; y: number };
  inputFields?: DataField[];
  outputFields?: DataField[];
  outputFormat?: OutputFieldDef[];
}

interface DagEdge {
  from: string;
  to: string;
  fromPort?: number;
}

interface WorkflowConfig {
  nodes: DagNode[];
  edges: DagEdge[];
}

interface ConditionBranch {
  match: string;
  next: string;
}

const NODE_W = 236;
const NODE_H = 118;
const PORT_OUT_X = NODE_W + 6;
const PORT_OUT_Y = 59;
const PORT_IN_X = -6;
const PORT_IN_Y = 59;

export interface LlmModel {
  modelId: string;
  displayName: string;
  provider: string;
}

const props = withDefaults(defineProps<{
  modelValue: string;
  tools: AgentToolResponse[];
  models?: LlmModel[];
  knowledgeSpaces?: RagflowSpaceResponse[];
  readonly?: boolean;
  testable?: boolean;
  versionId?: string | number;
  systemPrompt?: string;
  modelConfigJson?: string;
}>(), { models: () => [], knowledgeSpaces: () => [], readonly: false, testable: true, systemPrompt: '', modelConfigJson: '' });

const emit = defineEmits<{
  'update:modelValue': [value: string];
  'update:systemPrompt': [value: string];
  'update:modelConfigJson': [value: string];
  'workflowTestResult': [value: { success: boolean; finalReply?: string; errorMessage?: string }];
}>();

onErrorCaptured((err) => {
  console.error('[AgentDagEditor]', err);
  return false;
});

const nodes = ref<DagNode[]>([]);
const edges = ref<DagEdge[]>([]);
const selectedNodeId = ref('');
const parseError = ref('');
const branchText = ref('');
const replyJsonText = ref('');
const showGlobalConfig = ref(false);
const modelConfigForm = ref({ temperature: 0.2, maxTokens: 1024 });
let syncingFromModel = false;

const canvasRef = ref<HTMLElement | null>(null);
const panOffset = ref({ x: 0, y: 0 });
const scale = ref(1);

const nodeDrag = ref<{ nodeId: string; pointerId: number; offsetX: number; offsetY: number } | null>(null);
const panDrag = ref<{ pointerId: number; startX: number; startY: number; startPanX: number; startPanY: number } | null>(null);
const edgeDrag = ref<{ fromNodeId: string; fromPort: number; fromX: number; fromY: number; toX: number; toY: number } | null>(null);
const hoveredPortIn = ref<string | null>(null);
const spaceHeld = ref(false);

const portMenu = ref<{ nodeId: string; portIdx: number; x: number; y: number } | null>(null);
const nodeContextMenu = ref<{ nodeId: string; x: number; y: number } | null>(null);

const trialMode = ref(false);
const trialNodeId = ref('');
const trialInput = ref('');
const trialOutput = ref('');
const trialRunning = ref(false);
const trialStatus = ref<'idle' | 'running' | 'success' | 'error'>('idle');
const trialStatusNodeId = ref('');

const workflowTestMode = ref(false);
const workflowTestInput = ref('');
const workflowTestResult = ref<{ success: boolean; finalReply?: string; steps?: { nodeId: string; type: string; status: string; output?: string; durationMs: number }[]; errorMessage?: string; durationMs?: number } | null>(null);
const workflowTestRunning = ref(false);

const selectedNode = computed(() => nodes.value.find((n) => n.id === selectedNodeId.value) ?? null);
const selectableTargets = computed(() => nodes.value.filter((n) => n.type !== 'START'));
const hasStart = computed(() => nodes.value.some((n) => n.type === 'START'));
const scalePercent = computed(() => `${Math.round(scale.value * 100)}%`);
const canRunTest = computed(() => props.testable);

function nodeDisplayName(id?: string | null) {
  const index = nodes.value.findIndex((n) => n.id === id);
  const node = index >= 0 ? nodes.value[index] : null;
  return node ? `${NODE_TYPE_LABEL[node.type]} ${index + 1}` : '节点';
}

function nodeLabel(node: DagNode) {
  return nodeDisplayName(node.id);
}

function nodeIndexLabel(node: DagNode) {
  const index = nodes.value.findIndex((item) => item.id === node.id);
  return index >= 0 ? `#${index + 1}` : '#';
}

const nodeContextMenuNode = computed(() =>
  nodeContextMenu.value ? nodes.value.find((node) => node.id === nodeContextMenu.value?.nodeId) ?? null : null,
);

function nodeIcon(type: NodeType): Component {
  const icons: Record<NodeType, Component> = {
    START: CircleDot,
    LLM: BrainCircuit,
    TOOL: Wrench,
    CONDITION: GitBranch,
    INTENT: Route,
    APPROVAL: SquareCheckBig,
    KNOWLEDGE: FileSearch,
    FORM_EXTRACT: ScrollText,
    SUMMARY: ListChecks,
    TRANSFORM: Shuffle,
    END: Milestone,
  };
  return icons[type] ?? Bot;
}

function modelName(modelId: unknown) {
  const id = String(modelId ?? '');
  if (!id) return '';
  return props.models.find((model) => String(model.modelId) === id)?.displayName ?? '已选模型';
}

function dataSourceNames(node: DagNode) {
  const raw = node.config.dataSources;
  if (!Array.isArray(raw)) return [];
  return raw
    .map((key) => DATA_SOURCES.find((source) => source.key === key)?.label ?? String(key))
    .filter(Boolean);
}

function knowledgeToolCall(node: DagNode): Record<string, unknown> | null {
  const calls = node.config.toolCalls;
  if (!Array.isArray(calls)) return null;
  const first = calls[0];
  return first && typeof first === 'object' && !Array.isArray(first) ? first as Record<string, unknown> : null;
}

function knowledgeArgs(node: DagNode): Record<string, unknown> {
  const args = knowledgeToolCall(node)?.args;
  return args && typeof args === 'object' && !Array.isArray(args) ? args as Record<string, unknown> : {};
}

function knowledgeSpaceId(node: DagNode) {
  return String(knowledgeArgs(node).spaceId ?? '');
}

function knowledgeTopK(node: DagNode) {
  const topK = Number(knowledgeArgs(node).topK ?? 5);
  return Number.isFinite(topK) && topK > 0 ? topK : 5;
}

function knowledgeSpaceName(node: DagNode) {
  const id = knowledgeSpaceId(node);
  if (!id) return '';
  return props.knowledgeSpaces.find((space) => String(space.spaceId) === id)?.spaceName ?? '已选知识库';
}

function nodeMetaText(node: DagNode) {
  if (node.type === 'START') return '用户输入入口';
  if (node.type === 'END') return '回复出口';
  if (node.type === 'KNOWLEDGE') {
    const space = knowledgeSpaceName(node);
    return space ? `${space} · Top ${knowledgeTopK(node)}` : '未选择知识库';
  }
  if (node.type === 'TOOL') {
    const sources = dataSourceNames(node);
    return sources.length ? sources.slice(0, 2).join(' / ') : '未选择工具';
  }
  if (node.type === 'CONDITION') return `${getConditionBranches(node).length} 条分支`;
  if (node.type === 'INTENT') return `${getIntents(node).length} 个意图`;
  const model = modelName(node.config.modelId);
  return model || (supportsPrompt(node.type) ? '未选择模型' : '');
}

function workflowRunStatusLabel(value?: string | null) {
  if (value === 'SUCCESS') return '成功';
  if (value === 'FAILED') return '失败';
  if (value === 'SKIPPED') return '已跳过';
  return value ? '已执行' : '-';
}

function trialDisplayText(value?: string | null) {
  if (!value) return '运行完成';
  try {
    const parsed = JSON.parse(value);
    if (parsed && typeof parsed === 'object') return '运行完成，结果已生成';
  } catch { /* ignore */ }
  return safeBusinessMessage(value, '运行完成，结果已生成');
}

function workflowFinalReplyText(value?: string | null) {
  if (!value) return '';
  try {
    const parsed = JSON.parse(value);
    return JSON.stringify(parsed, null, 2);
  } catch { /* ignore */ }
  return safeBusinessMessage(value, '已生成最终输出');
}

function getIntents(node: DagNode | undefined | null): string[] {
  if (!node || node.type !== 'INTENT') return [];
  const raw = node.config?.intents;
  return Array.isArray(raw) ? raw as string[] : [];
}

function getConditionBranches(node: DagNode | undefined | null): ConditionBranch[] {
  if (!node || node.type !== 'CONDITION') return [];
  const raw = node.config?.branches;
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((item): item is Record<string, unknown> => item !== null && typeof item === 'object')
    .map((item) => ({
      match: String(item.match ?? ''),
      next: String(item.next ?? ''),
    }));
}

function getBranchPortLabels(node: DagNode | undefined | null): string[] {
  if (!node) return [];
  if (node.type === 'INTENT') return [...getIntents(node), '其他'];
  if (node.type === 'CONDITION') {
    const branches = getConditionBranches(node).map((branch) => branch.match || '未命名分支');
    return [...branches, '默认'];
  }
  return [];
}

function nodePortCount(node: DagNode | undefined | null): number {
  const labels = getBranchPortLabels(node);
  return labels.length > 0 ? labels.length : 1;
}

function nodeVisualHeight(node: DagNode): number {
  if (!['INTENT', 'CONDITION'].includes(node.type)) return NODE_H;
  const labels = getBranchPortLabels(node);
  if (labels.length === 0) return NODE_H;
  return Math.max(NODE_H, 76 + labels.length * 24);
}

function getUpstreamNodes(nodeId: string): DagNode[] {
  if (!nodeId) return [];
  const visited = new Set<string>();
  const result: DagNode[] = [];
  function walk(id: string) {
    if (visited.has(id)) return;
    visited.add(id);
    const incoming = visibleEdges.value.filter((e) => e.to === id);
    for (const edge of incoming) {
      const fromNode = nodes.value.find((n) => n.id === edge.from);
      if (fromNode) {
        result.push(fromNode);
        walk(fromNode.id);
      }
    }
  }
  walk(nodeId);
  return result;
}

function getNodeOutputFields(node: DagNode | undefined): string[] {
  if (!node) return [];
  if (node.outputFields && node.outputFields.length > 0) {
    return node.outputFields.map((f) => f.key).filter(Boolean);
  }
  if (node.outputKey) return [node.outputKey];
  return [];
}

function getUpstreamVariableOptions(nodeId: string): { value: string; label: string }[] {
  const upstream = getUpstreamNodes(nodeId);
  const vars: { value: string; label: string }[] = [];
  for (const node of upstream) {
    const fields = node.outputFields && node.outputFields.length > 0
      ? node.outputFields
      : node.outputKey
        ? [{ key: node.outputKey, label: node.outputKey }]
        : [];
    for (const field of fields) {
      vars.push({
        value: `{{${node.id}.${field.key}}}`,
        label: `${nodeLabel(node)} · ${field.label || '输出内容'}`,
      });
    }
  }
  return vars;
}

function conditionFieldOptions(nodeId: string) {
  return getUpstreamVariableOptions(nodeId).map((item) => ({
    value: item.value,
    label: item.label,
  }));
}

function getPortOutY(node: DagNode | undefined | null, portIdx: number): number {
  if (!node) return PORT_OUT_Y;
  const totalPorts = nodePortCount(node);
  if (totalPorts <= 1) return PORT_OUT_Y;
  const nodeHeight = nodeVisualHeight(node);
  return 54 + portIdx * (nodeHeight - 54) / (totalPorts + 1);
}

function getPortOutX(): number {
  return PORT_OUT_X;
}

const edgePaths = computed(() => visibleEdges.value.map((edge) => {
  const from = nodes.value.find((n) => n.id === edge.from);
  const to = nodes.value.find((n) => n.id === edge.to);
  if (!from || !to) return null;
  const x1 = from.position.x + getPortOutX();
  const y1 = from.position.y + getPortOutY(from, edge.fromPort ?? 0);
  const x2 = to.position.x + PORT_IN_X;
  const y2 = to.position.y + PORT_IN_Y;
  const dx = Math.max(Math.abs(x2 - x1) * 0.5, 50);
  const cx1 = x1 + dx;
  const cy1 = y1;
  const cx2 = x2 - dx;
  const cy2 = y2;
  const t = 0.5;
  const mt = 1 - t;
  const mx = mt * mt * mt * x1 + 3 * mt * mt * t * cx1 + 3 * mt * t * t * cx2 + t * t * t * x2;
  const my = mt * mt * mt * y1 + 3 * mt * mt * t * cy1 + 3 * mt * t * t * cy2 + t * t * t * y2;
  return { ...edge, d: `M${x1},${y1} C${cx1},${cy1} ${cx2},${cy2} ${x2},${y2}`, mx, my };
}).filter((e): e is NonNullable<typeof e> => e !== null));

const tempEdgePath = computed(() => {
  if (!edgeDrag.value) return '';
  return bezier(edgeDrag.value.fromX, edgeDrag.value.fromY, edgeDrag.value.toX, edgeDrag.value.toY);
});

const selectedNodeEdges = computed(() => {
  const id = selectedNodeId.value;
  if (!id) return [];
  return visibleEdges.value.filter((e) => e.from === id || e.to === id);
});

const visibleEdges = computed(() => normalizeVisibleEdges(nodes.value, edges.value));

function bezier(x1: number, y1: number, x2: number, y2: number) {
  const dx = Math.max(Math.abs(x2 - x1) * 0.5, 50);
  return `M${x1},${y1} C${x1 + dx},${y1} ${x2 - dx},${y2} ${x2},${y2}`;
}

watch(() => props.modelValue, (val) => {
  syncingFromModel = true;
  loadWorkflow(val);
  syncingFromModel = false;
}, { immediate: true });

watch(() => props.modelConfigJson, (val) => {
  try {
    const parsed = val ? JSON.parse(val) as Record<string, unknown> : {};
    modelConfigForm.value = {
      temperature: Number(parsed.temperature ?? 0.2),
      maxTokens: Number(parsed.maxTokens ?? 1024),
    };
  } catch {
    modelConfigForm.value = { temperature: 0.2, maxTokens: 1024 };
  }
}, { immediate: true });

watch(selectedNode, (node) => {
  branchText.value = node?.type === 'CONDITION' ? strJson(node.config.branches, '[\n  { "match": "YES", "next": "end" }\n]') : '';
  replyJsonText.value = node?.type === 'END' ? strJson(node.config.replyJson, '{\n  "text": "{{answer}}"\n}') : '';
});

function loadWorkflow(value: string) {
  parseError.value = '';
  try {
    const parsed = value ? JSON.parse(value) as WorkflowConfig : defaultWorkflow();
    const newNodes = Array.isArray(parsed.nodes) ? parsed.nodes.map((n, i) => normNode(n, i)) : defaultWorkflow().nodes;
    const newEdges = Array.isArray(parsed.edges) ? parsed.edges.map(normEdge).filter((e) => e.from && e.to) : [];
    nodes.value = newNodes;
    edges.value = newEdges;
    if (!selectedNodeId.value && newNodes.length > 0) {
      selectedNodeId.value = newNodes[0].id;
    }
  } catch (e) {
    parseError.value = e instanceof Error ? e.message : 'JSON 解析失败';
    const fb = defaultWorkflow();
    nodes.value = fb.nodes;
    edges.value = fb.edges;
    if (!selectedNodeId.value) {
      selectedNodeId.value = fb.nodes[0].id;
    }
  }
}

function addNode(type: NodeType, afterNodeId?: string, fromPort?: number) {
  if (props.readonly) return;
  nodeContextMenu.value = null;
  if (type === 'START' && hasStart.value) return;
  const id = uniqueId(type.toLowerCase());
  const afterNode = afterNodeId ? nodes.value.find((n) => n.id === afterNodeId) : null;
  const pos = afterNode
    ? { x: afterNode.position.x + 280, y: afterNode.position.y }
    : nextPos();
  nodes.value.push(makeNode(type, id, pos));
  if (afterNode) {
    setPortTarget(afterNode.id, fromPort ?? 0, id);
  }
  selectedNodeId.value = id;
  sync();
}

function showPortMenu(event: PointerEvent, nodeId: string, portIdx = 0) {
  if (props.readonly) return;
  event.stopPropagation();
  nodeContextMenu.value = null;
  const rect = canvasRef.value?.getBoundingClientRect();
  if (!rect) return;
  portMenu.value = {
    nodeId,
    portIdx,
    x: event.clientX - rect.left,
    y: event.clientY - rect.top,
  };
  window.addEventListener('pointerdown', dismissPortMenu, { once: true });
}

function dismissPortMenu() {
  portMenu.value = null;
}

function onPortMenuSelect(type: NodeType) {
  if (!portMenu.value) return;
  const { nodeId, portIdx } = portMenu.value;
  addNode(type, nodeId, portIdx);
  portMenu.value = null;
}

function showNodeContextMenu(event: MouseEvent, node: DagNode) {
  event.preventDefault();
  event.stopPropagation();
  selectedNodeId.value = node.id;
  portMenu.value = null;
  const rect = canvasRef.value?.getBoundingClientRect();
  if (!rect) return;
  nodeContextMenu.value = {
    nodeId: node.id,
    x: event.clientX - rect.left,
    y: event.clientY - rect.top,
  };
  window.addEventListener('pointerdown', dismissNodeContextMenu, { once: true });
}

function dismissNodeContextMenu() {
  nodeContextMenu.value = null;
}

function addNodeFromContext(type: NodeType) {
  if (!nodeContextMenu.value) return;
  addNode(type, nodeContextMenu.value.nodeId, 0);
  nodeContextMenu.value = null;
}

function enterTrialModeFromContext(nodeId: string) {
  enterTrialMode(nodeId);
  nodeContextMenu.value = null;
}

function addConditionBranchFromContext() {
  addConditionBranch();
  nodeContextMenu.value = null;
}

function addIntentFromContext() {
  addIntent();
  nodeContextMenu.value = null;
}

function duplicateNode(nodeId: string) {
  if (props.readonly) return;
  const node = nodes.value.find((item) => item.id === nodeId);
  if (!node || node.type === 'START') return;
  const id = uniqueId(node.type.toLowerCase());
  const clone: DagNode = {
    ...JSON.parse(JSON.stringify(node)),
    id,
    position: { x: node.position.x + 280, y: node.position.y + 40 },
  };
  nodes.value.push(clone);
  selectedNodeId.value = id;
  nodeContextMenu.value = null;
  sync();
}

function disconnectNode(nodeId: string) {
  if (props.readonly) return;
  const node = nodes.value.find((item) => item.id === nodeId);
  if (!node) return;
  edges.value = edges.value.filter((edge) => edge.from !== nodeId && edge.to !== nodeId);
  for (const item of nodes.value) {
    if (item.type !== 'CONDITION') continue;
    const branches = getConditionBranches(item).map((branch) => ({
      ...branch,
      next: item.id === nodeId || branch.next === nodeId ? '' : branch.next,
    }));
    const next: Record<string, unknown> = { ...item.config, branches };
    if (item.id === nodeId || next.default === nodeId) next.default = '';
    item.config = next;
  }
  nodeContextMenu.value = null;
  sync();
}

function startNodeDrag(event: PointerEvent, node: DagNode) {
  selectedNodeId.value = node.id;
  portMenu.value = null;
  nodeContextMenu.value = null;
  if (props.readonly) return;
  const rect = canvasRef.value?.getBoundingClientRect();
  if (!rect) return;
  (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
  nodeDrag.value = {
    nodeId: node.id,
    pointerId: event.pointerId,
    offsetX: (event.clientX - rect.left) / scale.value - panOffset.value.x - node.position.x,
    offsetY: (event.clientY - rect.top) / scale.value - panOffset.value.y - node.position.y,
  };
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', onUp);
  window.addEventListener('pointercancel', onUp);
}

function startPan(event: PointerEvent) {
  showNodePanel.value = false;
  nodeContextMenu.value = null;
  if (event.button === 1 || spaceHeld.value) {
    event.preventDefault();
    panDrag.value = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      startPanX: panOffset.value.x,
      startPanY: panOffset.value.y,
    };
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    window.addEventListener('pointercancel', onUp);
  } else {
    selectedNodeId.value = '';
    portMenu.value = null;
    nodeContextMenu.value = null;
  }
}

function startEdgeDrag(event: PointerEvent, nodeId: string, portIdx = 0) {
  if (props.readonly) return;
  event.stopPropagation();
  const node = nodes.value.find((n) => n.id === nodeId);
  if (!node) return;
  const rect = canvasRef.value?.getBoundingClientRect();
  if (!rect) return;
  edgeDrag.value = {
    fromNodeId: nodeId,
    fromPort: portIdx,
    fromX: node.position.x + getPortOutX(),
    fromY: node.position.y + getPortOutY(node, portIdx),
    toX: (event.clientX - rect.left) / scale.value - panOffset.value.x,
    toY: (event.clientY - rect.top) / scale.value - panOffset.value.y,
  };
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', onUp);
  window.addEventListener('pointercancel', onUp);
}

function onMove(event: PointerEvent) {
  if (nodeDrag.value) {
    const d = nodeDrag.value;
    const rect = canvasRef.value?.getBoundingClientRect();
    if (!rect) return;
    const node = nodes.value.find((n) => n.id === d.nodeId);
    if (node) {
      node.position = {
        x: Math.max(0, (event.clientX - rect.left) / scale.value - panOffset.value.x - d.offsetX),
        y: Math.max(0, (event.clientY - rect.top) / scale.value - panOffset.value.y - d.offsetY),
      };
      sync();
    }
  }
  if (panDrag.value) {
    const p = panDrag.value;
    panOffset.value = {
      x: p.startPanX + (event.clientX - p.startX) / scale.value,
      y: p.startPanY + (event.clientY - p.startY) / scale.value,
    };
  }
  if (edgeDrag.value) {
    const rect = canvasRef.value?.getBoundingClientRect();
    if (!rect) return;
    edgeDrag.value.toX = (event.clientX - rect.left) / scale.value - panOffset.value.x;
    edgeDrag.value.toY = (event.clientY - rect.top) / scale.value - panOffset.value.y;
    hoveredPortIn.value = hitTestIn(edgeDrag.value.toX, edgeDrag.value.toY, edgeDrag.value.fromNodeId);
  }
}

function onUp() {
  if (edgeDrag.value && hoveredPortIn.value) {
    const { fromNodeId: from, fromPort, toX, toY } = edgeDrag.value;
    const to = hoveredPortIn.value;
    const port = fromPort ?? 0;
    if (from !== to && !visibleEdges.value.some((e) => e.from === from && e.to === to && (e.fromPort ?? 0) === port)) {
      setPortTarget(from, port, to);
      sync();
    }
  }
  nodeDrag.value = null;
  panDrag.value = null;
  edgeDrag.value = null;
  hoveredPortIn.value = null;
  window.removeEventListener('pointermove', onMove);
  window.removeEventListener('pointerup', onUp);
  window.removeEventListener('pointercancel', onUp);
}

function hitTestIn(x: number, y: number, exclude: string): string | null {
  for (const node of nodes.value) {
    if (node.id === exclude || node.type === 'START') continue;
    const px = node.position.x + PORT_IN_X;
    const py = node.position.y + PORT_IN_Y;
    if (Math.hypot(x - px, y - py) < 20) return node.id;
  }
  return null;
}

function onKeyDown(e: KeyboardEvent) {
  if (e.code === 'Space' && !e.repeat) spaceHeld.value = true;
  if ((e.code === 'Backspace' || e.code === 'Delete') && !e.repeat) {
    const tag = (e.target as HTMLElement)?.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
    const node = selectedNode.value;
    if (node && node.type !== 'START' && !props.readonly) {
      e.preventDefault();
      removeNode(node.id);
    }
  }
}
function onKeyUp(e: KeyboardEvent) {
  if (e.code === 'Space') spaceHeld.value = false;
}
function onWheel(e: WheelEvent) {
  if (e.ctrlKey || e.metaKey) {
    e.preventDefault();
    scale.value = Math.min(2, Math.max(0.3, scale.value + (e.deltaY > 0 ? -0.1 : 0.1)));
  }
}

function removeNode(nodeId: string) {
  if (props.readonly) return;
  nodeContextMenu.value = null;
  const node = nodes.value.find((n) => n.id === nodeId);
  if (!node || node.type === 'START') return;
  nodes.value = nodes.value.filter((n) => n.id !== nodeId);
  edges.value = edges.value.filter((e) => e.from !== nodeId && e.to !== nodeId);
  for (const item of nodes.value) {
    if (item.type === 'CONDITION') {
      const branches = getConditionBranches(item)
        .map((branch) => ({ ...branch, next: branch.next === nodeId ? '' : branch.next }));
      const next: Record<string, unknown> = { ...item.config, branches };
      if (next.default === nodeId) next.default = '';
      item.config = next;
    }
  }
  selectedNodeId.value = nodes.value[0]?.id ?? '';
  sync();
}

function removeEdge(from: string, to: string, fromPort?: number) {
  if (props.readonly) return;
  const node = nodes.value.find((n) => n.id === from);
  if (node?.type === 'CONDITION') {
    clearConditionTarget(node, to, fromPort);
    sync();
    return;
  }
  edges.value = edges.value.filter((e) => {
    if (fromPort !== undefined) return !(e.from === from && e.to === to && (e.fromPort ?? 0) === fromPort);
    return !(e.from === from && e.to === to);
  });
  sync();
}

function layoutWorkflow() {
  if (props.readonly || nodes.value.length === 0) return;
  const nodeMap = new Map(nodes.value.map((node) => [node.id, node]));
  const originalOrder = new Map(nodes.value.map((node, index) => [node.id, index]));
  const outgoing = new Map<string, DagEdge[]>();
  const incoming = new Map<string, DagEdge[]>();
  for (const node of nodes.value) {
    outgoing.set(node.id, []);
    incoming.set(node.id, []);
  }
  for (const edge of visibleEdges.value) {
    if (!nodeMap.has(edge.from) || !nodeMap.has(edge.to) || edge.from === edge.to) continue;
    const next = { from: edge.from, to: edge.to, fromPort: edge.fromPort };
    outgoing.get(edge.from)?.push(next);
    incoming.get(edge.to)?.push(next);
  }
  for (const list of outgoing.values()) {
    list.sort((a, b) =>
      (a.fromPort ?? 0) - (b.fromPort ?? 0)
      || (originalOrder.get(a.to) ?? 0) - (originalOrder.get(b.to) ?? 0),
    );
  }

  const layers = new Map<string, number>();
  const visitFrom = (rootId: string, rootLayer: number) => {
    if (layers.has(rootId)) return;
    layers.set(rootId, rootLayer);
    const queue = [rootId];
    for (let cursor = 0; cursor < queue.length; cursor++) {
      const from = queue[cursor];
      const fromLayer = layers.get(from) ?? rootLayer;
      for (const edge of outgoing.get(from) ?? []) {
        if (layers.has(edge.to)) continue;
        layers.set(edge.to, fromLayer + 1);
        queue.push(edge.to);
      }
    }
  };
  const startNode = nodes.value.find((node) => node.type === 'START') ?? nodes.value[0];
  if (startNode) visitFrom(startNode.id, 0);
  for (const node of nodes.value) {
    if ((incoming.get(node.id)?.length ?? 0) === 0) visitFrom(node.id, 0);
  }
  for (const node of nodes.value) {
    visitFrom(node.id, layers.size > 0 ? 1 : 0);
  }

  const layerGroups = new Map<number, DagNode[]>();
  for (const node of nodes.value) {
    const layer = layers.get(node.id) ?? 0;
    layerGroups.set(layer, [...(layerGroups.get(layer) ?? []), node]);
  }
  const sortedLayers = [...layerGroups.keys()].sort((a, b) => a - b);
  const layerOrder = new Map<string, number>();
  for (const layer of sortedLayers) {
    const group = layerGroups.get(layer) ?? [];
    group.sort((a, b) => {
      const scoreA = layoutSortScore(a.id, incoming, layers, layerOrder, originalOrder);
      const scoreB = layoutSortScore(b.id, incoming, layers, layerOrder, originalOrder);
      return scoreA.parentLayer - scoreB.parentLayer
        || scoreA.parentOrder - scoreB.parentOrder
        || scoreA.port - scoreB.port
        || (originalOrder.get(a.id) ?? 0) - (originalOrder.get(b.id) ?? 0);
    });
    group.forEach((node, index) => layerOrder.set(node.id, index));
  }

  const left = 90;
  const top = 90;
  const columnGap = 340;
  const rowGap = 64;
  const columnHeights = sortedLayers.map((layer) => {
    const group = layerGroups.get(layer) ?? [];
    return group.reduce((sum, node, index) => sum + nodeVisualHeight(node) + (index > 0 ? rowGap : 0), 0);
  });
  const maxColumnHeight = Math.max(...columnHeights, 0);
  sortedLayers.forEach((layer, columnIndex) => {
    const group = layerGroups.get(layer) ?? [];
    let y = top + Math.max(0, Math.round((maxColumnHeight - columnHeights[columnIndex]) / 2));
    for (const node of group) {
      node.position = { x: left + columnIndex * columnGap, y };
      y += nodeVisualHeight(node) + rowGap;
    }
  });
  showNodePanel.value = false;
  portMenu.value = null;
  nodeContextMenu.value = null;
  sync();
}

function layoutSortScore(
  nodeId: string,
  incoming: Map<string, DagEdge[]>,
  layers: Map<string, number>,
  layerOrder: Map<string, number>,
  originalOrder: Map<string, number>,
) {
  const candidates = (incoming.get(nodeId) ?? [])
    .map((edge) => ({
      parentLayer: layers.get(edge.from) ?? 0,
      parentOrder: layerOrder.get(edge.from) ?? originalOrder.get(edge.from) ?? 0,
      port: edge.fromPort ?? 0,
    }))
    .sort((a, b) =>
      a.parentLayer - b.parentLayer
      || a.parentOrder - b.parentOrder
      || a.port - b.port,
    );
  return candidates[0] ?? { parentLayer: 0, parentOrder: originalOrder.get(nodeId) ?? 0, port: 0 };
}

function updateConfig(key: string, value: unknown) {
  const node = selectedNode.value;
  if (!node || props.readonly) return;
  node.config = { ...node.config, [key]: value };
  sync();
}

function updateKnowledgeToolArgs(patch: Record<string, unknown>) {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'KNOWLEDGE') return;
  const rawCalls = Array.isArray(node.config.toolCalls) ? node.config.toolCalls : [];
  const current = knowledgeToolCall(node) ?? {};
  const args = { ...knowledgeArgs(node), ...patch };
  const nextCall = {
    ...current,
    toolCode: TOOL_KNOWLEDGE_SEARCH,
    args,
    outputKey: String(current.outputKey ?? 'knowledge'),
  };
  node.config = { ...node.config, toolCalls: [nextCall, ...rawCalls.slice(1)] };
  sync();
}

function updateKnowledgeSpace(value: string) {
  updateKnowledgeToolArgs({ spaceId: value || '' });
}

function updateKnowledgeTopK(value: number) {
  updateKnowledgeToolArgs({ topK: Math.max(1, Math.min(20, Number.isFinite(value) ? value : 5)) });
}

function updateTool(value: string) {
  updateConfig(['tool', 'Code'].join(''), value);
}

function updateOutputKey(value: string) {
  const node = selectedNode.value;
  if (!node || props.readonly) return;
  node.outputKey = value.trim() || undefined;
  sync();
}

function applyBranches() {
  try {
    updateConfig('branches', normalizeBranches(branchText.value.trim() ? JSON.parse(branchText.value) : []));
    parseError.value = '';
  } catch (e) {
    parseError.value = e instanceof Error ? e.message : 'branches 不是合法 JSON';
  }
}

function applyReplyJson() {
  try {
    updateConfig('replyJson', replyJsonText.value.trim() ? JSON.parse(replyJsonText.value) : {});
    parseError.value = '';
  } catch (e) {
    parseError.value = e instanceof Error ? e.message : 'replyJson 不是合法 JSON';
  }
}

function endReplyMode(node: DagNode): 'text' | 'json' {
  return node.config.replyJson === undefined ? 'text' : 'json';
}

function setEndReplyMode(value: 'text' | 'json') {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'END') return;
  if (value === 'json') {
    const next: Record<string, unknown> = { ...node.config, replyJson: node.config.replyJson ?? { text: '{{answer}}' } };
    delete next.reply;
    node.config = next;
    replyJsonText.value = strJson(node.config.replyJson, '{\n  "text": "{{answer}}"\n}');
  } else {
    const next: Record<string, unknown> = { ...node.config, reply: String(node.config.reply ?? '{{answer}}') };
    delete next.replyJson;
    node.config = next;
  }
  sync();
}

function endReplyJson(node: DagNode): Record<string, unknown> {
  const value = node.config.replyJson;
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function endReplyData(node: DagNode): Record<string, unknown> {
  const data = endReplyJson(node).data;
  return data && typeof data === 'object' && !Array.isArray(data) ? data as Record<string, unknown> : {};
}

function endReplyActions(node: DagNode): Record<string, unknown>[] {
  const actions = endReplyJson(node).actions;
  return Array.isArray(actions) ? actions.filter((item): item is Record<string, unknown> => item !== null && typeof item === 'object') : [];
}

function ensureAgentCard(node: DagNode): Record<string, unknown> {
  const current = endReplyJson(node);
  return {
    cardType: 'agent_result',
    schemaVersion: current.schemaVersion ?? 1,
    category: current.category ?? 'QA',
    status: current.status ?? 'SUCCESS',
    title: current.title ?? '',
    answer: current.answer ?? '',
    reason: current.reason ?? '',
    confidence: current.confidence ?? '',
    data: current.data ?? { fields: null, records: null, flow: null },
    actions: Array.isArray(current.actions) ? current.actions : [],
    citations: current.citations ?? null,
    error: current.error ?? null,
  };
}

function updateEndCard(key: string, value: unknown) {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'END') return;
  const replyJson = ensureAgentCard(node);
  replyJson[key] = value === '' && ['reason', 'confidence'].includes(key) ? null : value;
  node.config = { ...node.config, replyJson };
  replyJsonText.value = strJson(replyJson, '{}');
  sync();
}

function updateEndCardData(key: string, value: unknown) {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'END') return;
  const replyJson = ensureAgentCard(node);
  const data = endReplyData(node);
  data[key] = value || null;
  replyJson.data = {
    fields: data.fields ?? null,
    records: data.records ?? null,
    flow: data.flow ?? null,
  };
  node.config = { ...node.config, replyJson };
  replyJsonText.value = strJson(replyJson, '{}');
  sync();
}

function updateEndCardAction(index: number, key: string, value: unknown) {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'END') return;
  const replyJson = ensureAgentCard(node);
  const actions = endReplyActions(node);
  actions[index] = { ...actions[index], [key]: value };
  replyJson.actions = actions;
  node.config = { ...node.config, replyJson };
  replyJsonText.value = strJson(replyJson, '{}');
  sync();
}

function addEndCardAction() {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'END') return;
  const replyJson = ensureAgentCard(node);
  replyJson.actions = [...endReplyActions(node), { type: ACTION_OPEN_BUSINESS, label: '打开申请', target: '/workbench/requests' }];
  node.config = { ...node.config, replyJson };
  replyJsonText.value = strJson(replyJson, '{}');
  sync();
}

function removeEndCardAction(index: number) {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'END') return;
  const replyJson = ensureAgentCard(node);
  const actions = endReplyActions(node);
  actions.splice(index, 1);
  replyJson.actions = actions;
  node.config = { ...node.config, replyJson };
  replyJsonText.value = strJson(replyJson, '{}');
  sync();
}

function sync() {
  if (!syncingFromModel) emit('update:modelValue', serializeWorkflow());
}

function serializeWorkflow() {
  const normalizedEdges = normalizeVisibleEdges(nodes.value, edges.value);
  return JSON.stringify({
    nodes: nodes.value.map((n) => {
      const p: Record<string, unknown> = { id: n.id, type: n.type, position: stablePosition(n.position) };
      if (n.config && Object.keys(n.config).length > 0) p.config = n.config;
      if (n.outputKey && n.type !== 'END' && n.type !== 'START') p.outputKey = n.outputKey;
      if (n.inputFields && n.inputFields.length > 0) p.inputFields = n.inputFields;
      if (n.outputFields && n.outputFields.length > 0) p.outputFields = n.outputFields;
      if (n.outputFormat && n.outputFormat.length > 0) p.outputFormat = normalizeOutputFormat(n.outputFormat);
      return p;
    }),
    edges: normalizedEdges,
  }, null, 2);
}

function defaultWorkflow(): WorkflowConfig {
  return {
    nodes: [
      makeNode('START', 'start', { x: 100, y: 250 }),
      makeNode('LLM', 'answer', { x: 450, y: 250 }),
      makeNode('END', 'end', { x: 800, y: 250 }),
    ],
    edges: [{ from: 'start', to: 'answer' }, { from: 'answer', to: 'end' }],
  };
}

function makeNode(type: NodeType, id: string, pos: { x: number; y: number }): DagNode {
  if (type === 'START') return {
    id, type, position: pos, config: {},
    inputFields: [],
    outputFields: [{ key: 'query', label: '用户输入', ref: '' }],
  };
  if (type === 'TOOL') return {
    id, type, position: pos, outputKey: id,
    config: { dataSources: ['employeeInfo'] as string[], targetUserId: REF_CURRENT_USER },
    inputFields: [{ key: 'userId', label: '目标用户', ref: REF_CURRENT_USER }],
    outputFields: [{ key: 'employeeInfo', label: '员工信息', ref: '' }],
  };
  if (type === 'CONDITION') return {
    id, type, position: pos,
    config: { field: '', branches: [], default: '' },
    inputFields: [{ key: 'value', label: '判断值', ref: '{{start.query}}' }],
    outputFields: [],
  };
  if (type === 'INTENT') return {
    id, type, position: pos, outputKey: id,
    config: { modelId: '', intents: ['售前处理', '售后处理'] as string[] },
    inputFields: [{ key: 'query', label: '用户输入', type: 'string' as FieldType, ref: '{{start.query}}' }],
    outputFields: [{ key: 'intent', label: '意图结果', type: 'string' as FieldType, ref: '' }],
    outputFormat: [{ key: 'intent', type: 'string', description: '意图名称' }],
  };
  if (type === 'KNOWLEDGE') return {
    id, type, position: pos, outputKey: id,
    config: {
      prompt: '请基于知识检索结果回答用户问题：{{knowledge.result}}',
      modelId: '',
      toolCalls: [
        { toolCode: TOOL_KNOWLEDGE_SEARCH, args: { spaceId: '', query: REF_START_QUERY, topK: 5 }, outputKey: 'knowledge' },
      ],
    },
    inputFields: [{ key: 'query', label: '用户问题', ref: '{{start.query}}' }],
    outputFields: [
      { key: 'answer', label: '答案', ref: '' },
      { key: 'citations', label: '引用', ref: '' },
    ],
  };
  if (type === 'FORM_EXTRACT') return {
    id, type, position: pos, outputKey: id,
    config: { prompt: '请从用户输入中提取业务表单字段，返回 JSON。用户输入：{{start.query}}', modelId: '', toolCalls: [] },
    inputFields: [{ key: 'query', label: '用户输入', ref: '{{start.query}}' }],
    outputFields: [{ key: 'fields', label: '表单字段', ref: '' }],
  };
  if (type === 'SUMMARY') return {
    id, type, position: pos, outputKey: id,
    config: { prompt: '请总结以下内容：{{start.query}}', modelId: '', toolCalls: [] },
    inputFields: [{ key: 'content', label: '内容', ref: '{{start.query}}' }],
    outputFields: [{ key: 'summary', label: '摘要', ref: '' }],
  };
  if (type === 'TRANSFORM') return {
    id, type, position: pos, outputKey: id,
    config: { prompt: '请将输入整理成下游节点可使用的 JSON：{{start.query}}', modelId: '', toolCalls: [] },
    inputFields: [{ key: 'input', label: '输入', ref: '{{start.query}}' }],
    outputFields: [{ key: 'data', label: '数据', ref: '' }],
  };
  if (type === 'APPROVAL') return {
    id, type, position: pos, outputKey: id,
    config: {
      modelId: '',
      dataSources: ['employeeInfo', 'leaveBalance'] as string[],
      prompt: '请根据公司政策和员工信息，审核该申请是否合规。请输出“通过”“驳回”或“转人工”，并说明理由。',
      confidenceThreshold: 0.7,
    },
    inputFields: [
      { key: 'request', label: '审批申请', ref: '{{start.query}}' },
      { key: 'applicantId', label: '申请人', ref: '' },
    ],
    outputFields: [
      { key: 'decision', label: '审批决定', ref: '' },
      { key: 'reason', label: '审批理由', ref: '' },
      { key: 'confidence', label: '置信度', ref: '' },
    ],
  };
  if (type === 'END') return {
    id, type, position: pos,
    config: { reply: '{{answer}}' },
    inputFields: [{ key: 'answer', label: '回复内容', ref: '{{answer}}' }],
    outputFields: [],
  };
  return {
    id, type, position: pos, outputKey: id,
    config: { prompt: '{{input.query}}', modelId: '' },
    inputFields: [{ key: 'query', label: '输入', ref: '{{start.query}}' }],
    outputFields: [{ key: 'answer', label: '回复', ref: '' }],
  };
}

function normNode(n: DagNode, i: number): DagNode {
  const id = sanitize(String(n.id || uniqueId('node')));
  const type = normType(n.type);
  const config = n.config && typeof n.config === 'object' ? { ...n.config } : {};
  if (type === 'INTENT' && !Array.isArray(config.intents)) {
    config.intents = ['售前处理', '售后处理'];
  }
  return {
    id,
    type,
    outputKey: typeof n.outputKey === 'string' ? n.outputKey : undefined,
    config,
    position: normPos((n as Partial<DagNode>).position, i),
    inputFields: Array.isArray(n.inputFields) ? n.inputFields : undefined,
    outputFields: type === 'INTENT'
      ? (Array.isArray(n.outputFields) && n.outputFields.length > 0 ? n.outputFields : [{ key: 'intent', label: '意图结果', type: 'string' as FieldType, ref: '' }])
      : (Array.isArray(n.outputFields) ? n.outputFields : undefined),
    outputFormat: Array.isArray((n as Partial<DagNode>).outputFormat) ? (n as Partial<DagNode>).outputFormat : undefined,
  };
}

function normEdge(e: DagEdge): DagEdge { return { from: String(e.from ?? ''), to: String(e.to ?? ''), fromPort: typeof e.fromPort === 'number' ? e.fromPort : undefined }; }
function normType(v: unknown): NodeType {
  return (['START', 'END', 'LLM', 'TOOL', 'CONDITION', 'INTENT', 'APPROVAL', 'KNOWLEDGE', 'FORM_EXTRACT', 'SUMMARY', 'TRANSFORM'] as const).includes(v as NodeType) ? v as NodeType : 'LLM';
}
function sanitize(v: string) { return v.trim().replace(/[^A-Za-z0-9_-]/g, '_'); }
function uniqueId(prefix: string) {
  let i = nodes.value.length + 1;
  let id = sanitize(`${prefix}_${i}`);
  while (nodes.value.some((n) => n.id === id)) { i++; id = sanitize(`${prefix}_${i}`); }
  return id;
}
function normPos(v: unknown, i: number): { x: number; y: number } {
  if (v && typeof v === 'object') {
    const x = Number((v as Record<string, unknown>).x);
    const y = Number((v as Record<string, unknown>).y);
    if (Number.isFinite(x) && Number.isFinite(y)) return stablePosition({ x, y });
  }
  return { x: 100 + (i % 4) * 300, y: 150 + Math.floor(i / 4) * 180 };
}
function stablePosition(pos: { x: number; y: number }): { x: number; y: number } {
  return {
    x: Math.max(0, Math.round(pos.x)),
    y: Math.max(0, Math.round(pos.y)),
  };
}
function nextPos() { return normPos(null, nodes.value.length); }
function strJson(v: unknown, fb: string) { return v === undefined || v === null ? fb : JSON.stringify(v, null, 2); }

function addInputField() {
  const node = selectedNode.value;
  if (!node || props.readonly) return;
  if (!node.inputFields) node.inputFields = [];
  node.inputFields.push({ key: '', label: '', type: 'string', ref: '' });
  sync();
}

function removeInputField(index: number) {
  const node = selectedNode.value;
  if (!node || props.readonly || !node.inputFields) return;
  node.inputFields.splice(index, 1);
  sync();
}

function updateInputField(index: number, field: Partial<DataField>) {
  const node = selectedNode.value;
  if (!node || props.readonly || !node.inputFields) return;
  Object.assign(node.inputFields[index], field);
  sync();
}

function addOutputField() {
  const node = selectedNode.value;
  if (!node || props.readonly) return;
  if (!node.outputFields) node.outputFields = [];
  node.outputFields.push({ key: '', label: '', type: 'string', ref: '' });
  sync();
}

function removeOutputField(index: number) {
  const node = selectedNode.value;
  if (!node || props.readonly || !node.outputFields) return;
  node.outputFields.splice(index, 1);
  sync();
}

function updateOutputField(index: number, field: Partial<DataField>) {
  const node = selectedNode.value;
  if (!node || props.readonly || !node.outputFields) return;
  Object.assign(node.outputFields[index], field);
  sync();
}

function addOutputFormat() {
  const node = selectedNode.value;
  if (!node || props.readonly) return;
  if (!node.outputFormat) node.outputFormat = [];
  node.outputFormat.push({ key: '', type: 'string', description: '' });
  sync();
}

function removeOutputFormat(index: number) {
  const node = selectedNode.value;
  if (!node || props.readonly || !node.outputFormat) return;
  node.outputFormat.splice(index, 1);
  sync();
}

function updateOutputFormat(index: number, field: Partial<OutputFieldDef>) {
  const node = selectedNode.value;
  if (!node || props.readonly || !node.outputFormat) return;
  Object.assign(node.outputFormat[index], field);
  if (field.type) {
    if (field.type === 'array') {
      if (!node.outputFormat[index].itemFields || node.outputFormat[index].itemFields.length === 0) {
        node.outputFormat[index].itemFields = [{ key: '', type: 'string', description: '' }];
      }
    } else if (field.type === 'object') {
      if (!node.outputFormat[index].itemFields || node.outputFormat[index].itemFields.length === 0) {
        node.outputFormat[index].itemFields = [{ key: '', type: 'string', description: '' }];
      }
    } else {
      delete node.outputFormat[index].itemFields;
    }
  }
  sync();
}

function addOutputFormatItem(index: number) {
  const node = selectedNode.value;
  if (!node || props.readonly || !node.outputFormat) return;
  const field = node.outputFormat[index];
  if (!field.itemFields) field.itemFields = [];
  field.itemFields.push({ key: '', type: 'string', description: '' });
  sync();
}

function removeOutputFormatItem(index: number, itemIndex: number) {
  const node = selectedNode.value;
  if (!node || props.readonly || !node.outputFormat?.[index]?.itemFields) return;
  node.outputFormat[index].itemFields?.splice(itemIndex, 1);
  sync();
}

function updateOutputFormatItem(index: number, itemIndex: number, field: Partial<OutputFieldDef>) {
  const node = selectedNode.value;
  if (!node || props.readonly || !node.outputFormat?.[index]?.itemFields) return;
  const item = node.outputFormat[index].itemFields[itemIndex];
  if (!item) return;
  Object.assign(item, field);
  sync();
}

function normalizeOutputFormat(fields: OutputFieldDef[]): OutputFieldDef[] {
  return fields.map((field) => {
    const next: OutputFieldDef = {
      key: field.key,
      type: field.type || 'string',
      description: field.description || '',
    };
    if (['array', 'object'].includes(next.type) && field.itemFields && field.itemFields.length > 0) {
      next.itemFields = normalizeOutputFormat(field.itemFields);
    }
    return next;
  });
}

function setSingleOutputFormatType(type: OutputType) {
  const node = selectedNode.value;
  if (!node || props.readonly) return;
  const current = node.outputFormat?.[0];
  const key = current?.key || node.outputKey || 'result';
  const next: OutputFieldDef = {
    key,
    type,
    description: current?.description || '',
  };
  if (['array', 'object'].includes(type)) {
    next.itemFields = current?.itemFields?.length ? current.itemFields : [{ key: '', type: 'string', description: '' }];
  }
  node.outputFormat = [next];
  sync();
}

function singleOutputFormatType(node: DagNode): OutputType {
  return (node.outputFormat?.[0]?.type as OutputType | undefined) || 'string';
}

function normalizeBranches(value: unknown): ConditionBranch[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is Record<string, unknown> => item !== null && typeof item === 'object')
    .map((item) => ({
      match: String(item.match ?? ''),
      next: String(item.next ?? ''),
    }));
}

function addConditionBranch() {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'CONDITION') return;
  const branches = getConditionBranches(node);
  branches.push({ match: '', next: '' });
  node.config = { ...node.config, branches };
  branchText.value = strJson(branches, '[]');
  sync();
}

function removeConditionBranch(index: number) {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'CONDITION') return;
  const branches = getConditionBranches(node);
  branches.splice(index, 1);
  node.config = { ...node.config, branches };
  branchText.value = strJson(branches, '[]');
  sync();
}

function updateConditionBranch(index: number, field: Partial<ConditionBranch>) {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'CONDITION') return;
  const branches = getConditionBranches(node);
  branches[index] = { ...branches[index], ...field };
  node.config = { ...node.config, branches };
  branchText.value = strJson(branches, '[]');
  sync();
}

function setPortTarget(from: string, port: number, to: string) {
  const node = nodes.value.find((n) => n.id === from);
  if (node?.type === 'CONDITION') {
    const branches = getConditionBranches(node);
    if (port < branches.length) {
      branches[port] = { ...branches[port], next: to };
      node.config = { ...node.config, branches };
    } else {
      node.config = { ...node.config, default: to };
    }
    return;
  }
  edges.value = edges.value
    .filter((edge) => !(edge.from === from && (edge.fromPort ?? 0) === port));
  edges.value.push({ from, to, fromPort: port });
}

function clearConditionTarget(node: DagNode, to: string, fromPort?: number) {
  const branches = getConditionBranches(node);
  const next: Record<string, unknown> = { ...node.config };
  if (fromPort !== undefined && fromPort < branches.length) {
    if (branches[fromPort]?.next === to) branches[fromPort] = { ...branches[fromPort], next: '' };
  } else if (fromPort !== undefined) {
    if (next.default === to) next.default = '';
  } else {
    for (let i = 0; i < branches.length; i++) {
      if (branches[i].next === to) branches[i] = { ...branches[i], next: '' };
    }
    if (next.default === to) next.default = '';
  }
  next.branches = branches;
  node.config = next;
  if (selectedNodeId.value === node.id) branchText.value = strJson(branches, '[]');
}

function normalizeVisibleEdges(sourceNodes: DagNode[], sourceEdges: DagEdge[]): DagEdge[] {
  const normalized: DagEdge[] = [];
  const branchNodeIds = new Set(sourceNodes.filter((n) => ['CONDITION', 'INTENT'].includes(n.type)).map((n) => n.id));
  for (const edge of sourceEdges) {
    if (!edge.from || !edge.to || branchNodeIds.has(edge.from)) continue;
    normalized.push(edge);
  }
  for (const node of sourceNodes) {
    if (node.type === 'CONDITION') {
      const branches = getConditionBranches(node);
      branches.forEach((branch, index) => {
        if (branch.next) normalized.push({ from: node.id, to: branch.next, fromPort: index });
      });
      const defaultNext = String(node.config.default ?? '');
      if (defaultNext) normalized.push({ from: node.id, to: defaultNext, fromPort: branches.length });
    }
    if (node.type === 'INTENT') {
      normalizeIntentEdges(node, sourceEdges).forEach((edge) => normalized.push(edge));
    }
  }
  return normalized;
}

function normalizeIntentEdges(node: DagNode, sourceEdges: DagEdge[]): DagEdge[] {
  const portCount = getIntents(node).length + 1;
  const direct = sourceEdges.filter((edge) => edge.from === node.id && edge.to);
  const result: DagEdge[] = [];
  for (let port = 0; port < portCount; port++) {
    const exact = direct.find((edge) => (edge.fromPort ?? 0) === port);
    if (exact) {
      result.push({ from: node.id, to: exact.to, fromPort: port });
      continue;
    }
    if (direct.length === 1 && direct[0].fromPort === undefined) {
      result.push({ from: node.id, to: direct[0].to, fromPort: port });
    }
  }
  return result;
}

function addIntent() {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'INTENT') return;
  const intents = getIntents(node);
  intents.push('');
  node.config = { ...node.config, intents };
  sync();
}

function removeIntent(index: number) {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'INTENT') return;
  const intents = getIntents(node);
  intents.splice(index, 1);
  node.config = { ...node.config, intents };
  sync();
}

function updateIntent(index: number, value: string) {
  const node = selectedNode.value;
  if (!node || props.readonly || node.type !== 'INTENT') return;
  const intents = getIntents(node);
  intents[index] = value;
  node.config = { ...node.config, intents };
  sync();
}

function hasDataSource(key: string): boolean {
  const node = selectedNode.value;
  if (!node) return false;
  const raw = node.config.dataSources;
  return Array.isArray(raw) && raw.includes(key);
}

function toggleDataSource(key: string) {
  const node = selectedNode.value;
  if (!node || props.readonly) return;
  const raw = node.config.dataSources;
  const sources: string[] = Array.isArray(raw) ? [...raw] : [];
  const idx = sources.indexOf(key);
  if (idx >= 0) {
    sources.splice(idx, 1);
  } else {
    sources.push(key);
  }
  node.config = { ...node.config, dataSources: sources };
  sync();
}

function updateTargetUser(value: string) {
  updateConfig('targetUserId', value || REF_CURRENT_USER);
}

function updateModelConfig(key: 'temperature' | 'maxTokens', value: number) {
  modelConfigForm.value = { ...modelConfigForm.value, [key]: value };
  emit('update:modelConfigJson', JSON.stringify(modelConfigForm.value));
}

function supportsPrompt(type: NodeType) {
  return ['LLM', 'INTENT', 'APPROVAL', 'KNOWLEDGE', 'FORM_EXTRACT', 'SUMMARY', 'TRANSFORM'].includes(type);
}

function supportsToolCalls(type: NodeType) {
  return ['LLM', 'INTENT', 'APPROVAL', 'KNOWLEDGE', 'FORM_EXTRACT', 'SUMMARY', 'TRANSFORM'].includes(type);
}

function enterTrialMode(nodeId: string) {
  trialMode.value = true;
  trialNodeId.value = nodeId;
  trialInput.value = '';
  trialOutput.value = '';
  trialRunning.value = false;
  trialStatus.value = 'idle';
  selectedNodeId.value = '';
  showGlobalConfig.value = false;
}

function exitTrialMode() {
  trialMode.value = false;
  trialNodeId.value = '';
  trialInput.value = '';
  trialOutput.value = '';
  trialRunning.value = false;
  trialStatus.value = 'idle';
  trialStatusNodeId.value = '';
}

async function runTrial() {
  if (!trialNodeId.value || trialRunning.value || !canRunTest.value) return;
  trialRunning.value = true;
  trialStatus.value = 'running';
  trialStatusNodeId.value = trialNodeId.value;
  trialOutput.value = '';
  try {
    const result = await adminApi.agentConfig.trialRun({
      nodeId: trialNodeId.value,
      inputJson: trialInput.value || undefined,
      workflowConfigJson: props.modelValue || undefined,
      systemPrompt: props.systemPrompt || undefined,
      modelConfigJson: props.modelConfigJson || undefined,
    });
    if (result.success) {
      trialOutput.value = trialDisplayText(result.outputJson);
      trialStatus.value = 'success';
    } else {
      trialOutput.value = safeBusinessMessage(result.errorMessage, '执行失败，请检查节点配置');
      trialStatus.value = 'error';
    }
  } catch (e) {
    trialOutput.value = e instanceof Error ? safeBusinessMessage(e.message, '请求失败') : '请求失败';
    trialStatus.value = 'error';
  } finally {
    trialRunning.value = false;
    setTimeout(() => { trialStatus.value = 'idle'; trialStatusNodeId.value = ''; }, 3000);
  }
}

function enterWorkflowTest() {
  if (!canRunTest.value) return;
  workflowTestMode.value = true;
  workflowTestInput.value = '';
  workflowTestResult.value = null;
  selectedNodeId.value = '';
  trialMode.value = false;
  showGlobalConfig.value = false;
}

function exitWorkflowTest() {
  workflowTestMode.value = false;
  workflowTestInput.value = '';
  workflowTestResult.value = null;
}

async function runWorkflowTest() {
  if (workflowTestRunning.value || !canRunTest.value) return;
  workflowTestRunning.value = true;
  workflowTestResult.value = null;
  try {
    const result = await adminApi.agentConfig.workflowRun({
      inputJson: workflowTestInput.value || '{}',
      versionId: props.versionId,
      workflowConfigJson: props.modelValue || undefined,
      systemPrompt: props.systemPrompt || undefined,
      modelConfigJson: props.modelConfigJson || undefined,
    });
    workflowTestResult.value = {
      ...result,
      errorMessage: result.errorMessage ? safeBusinessMessage(result.errorMessage, '执行失败，请检查流程配置') : undefined,
    };
  } catch (e) {
    workflowTestResult.value = { success: false, errorMessage: e instanceof Error ? safeBusinessMessage(e.message, '请求失败') : '请求失败' };
  } finally {
    if (workflowTestResult.value) emit('workflowTestResult', workflowTestResult.value);
    workflowTestRunning.value = false;
  }
}

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onMove);
  window.removeEventListener('pointerup', onUp);
  window.removeEventListener('pointercancel', onUp);
  window.removeEventListener('keydown', onKeyDown);
  window.removeEventListener('keyup', onKeyUp);
});
if (typeof window !== 'undefined') {
  window.addEventListener('keydown', onKeyDown);
  window.addEventListener('keyup', onKeyUp);
}
</script>

<template>
  <div class="dag-editor">
    <div class="dag-toolbar">
      <div class="dag-toolbar-left">
        <div class="tb-add-wrap">
          <button class="tb-btn tb-primary" type="button" :disabled="readonly" @click="showNodePanel = !showNodePanel">
            <Plus :size="14" /> 节点
          </button>
          <div v-if="showNodePanel" class="node-panel">
            <div v-for="cat in NODE_CATEGORIES" :key="cat.label" class="node-cat">
              <div class="node-cat-title">{{ cat.label }}</div>
              <div class="node-cat-grid">
                <button
                  v-for="item in cat.items"
                  :key="item.type"
                  class="node-cat-item"
                  :disabled="readonly"
                  @click="addNode(item.type); showNodePanel = false"
                >{{ item.label }}</button>
              </div>
            </div>
          </div>
        </div>
        <button
          class="tb-btn"
          :class="{ active: workflowTestMode }"
          :disabled="!workflowTestMode && !canRunTest"
          :title="canRunTest ? '流程测试' : '当前版本不可测试'"
          @click="workflowTestMode ? exitWorkflowTest() : enterWorkflowTest()"
        >
          <Play :size="14" /> 流程
        </button>
        <button class="tb-btn" type="button" :disabled="readonly || nodes.length < 2" @click="layoutWorkflow">
          <Route :size="14" /> 整理
        </button>
        <button class="tb-btn" :class="{ active: showGlobalConfig }" @click="showGlobalConfig = !showGlobalConfig">
          全局
        </button>
      </div>
      <div class="dag-toolbar-right">
        <span class="tb-metric">{{ nodes.length }} 节点</span>
        <span class="tb-metric">{{ visibleEdges.length }} 连线</span>
        <span class="tb-metric">{{ scalePercent }}</span>
        <span v-if="parseError" class="tb-error">{{ parseError }}</span>
      </div>
    </div>

    <div class="dag-body">
      <div
        ref="canvasRef"
        class="dag-canvas"
        :class="{ panning: spaceHeld || panDrag }"
        @pointerdown="startPan"
        @wheel="onWheel"
      >
        <div class="dag-viewport" :style="{ transform: `translate(${panOffset.x}px, ${panOffset.y}px) scale(${scale})` }">
          <svg class="dag-edges" aria-hidden="true">
            <defs>
              <marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
                <path class="edge-arrow" d="M0,0 L0,6 L9,3 z" />
              </marker>
            </defs>
            <g v-for="edge in edgePaths" :key="`${edge.from}-${edge.fromPort ?? 0}-${edge.to}`" class="edge-group">
              <path :d="edge.d" class="edge-path" />
              <foreignObject :x="edge.mx - 10" :y="edge.my - 10" width="20" height="20" class="edge-delete-wrap">
                <button
                  class="edge-delete-btn"
                  title="删除连线"
                  :disabled="readonly"
                  @click.stop="removeEdge(edge.from, edge.to, edge.fromPort)"
                >×</button>
              </foreignObject>
            </g>
            <path v-if="edgeDrag" :d="tempEdgePath" class="edge-temp" />
          </svg>

          <div
            v-for="node in nodes"
            :key="node.id"
            :class="['dag-node', `type-${node.type.toLowerCase()}`, { selected: node.id === selectedNodeId }]"
            :style="{
              left: `${node.position.x}px`,
              top: `${node.position.y}px`,
              minHeight: ['INTENT', 'CONDITION'].includes(node.type) && getBranchPortLabels(node).length > 0
                ? `${nodeVisualHeight(node)}px`
                : undefined
            }"
            @pointerdown.left.stop="startNodeDrag($event, node)"
            @contextmenu.prevent.stop="showNodeContextMenu($event, node)"
            @click.stop="selectedNodeId = node.id"
          >
            <div class="node-header">
              <span class="node-icon">
                <component :is="nodeIcon(node.type)" :size="15" />
              </span>
              <span class="node-type">{{ NODE_TYPE_LABEL[node.type] }}</span>
              <span class="node-index">{{ nodeIndexLabel(node) }}</span>
            </div>
            <div class="node-body">
              <small v-if="nodeMetaText(node)" class="node-meta">{{ nodeMetaText(node) }}</small>
              <div v-if="(node.inputFields ?? []).length > 0" class="node-fields">
                <span v-for="f in node.inputFields" :key="f.key" class="node-field in">← {{ f.label || f.key }}</span>
              </div>
              <div v-if="(node.outputFields ?? []).length > 0" class="node-fields">
                <span v-for="f in node.outputFields" :key="f.key" class="node-field out">→ {{ f.label || f.key }}</span>
              </div>
              <small v-if="node.outputKey && (node.outputFields ?? []).length === 0 && node.type !== 'INTENT'" class="node-output">已配置输出</small>
            </div>
            <div v-if="node.type !== 'START'" class="port port-in" :class="{ hovered: hoveredPortIn === node.id }" />
            <template v-if="['INTENT', 'CONDITION'].includes(node.type) && getBranchPortLabels(node).length > 0">
              <div
                v-for="(label, portIdx) in getBranchPortLabels(node)"
                :key="portIdx"
                class="port port-out"
                :class="'port-out-' + portIdx"
                :style="{ top: getPortOutY(node, portIdx) + 'px' }"
                @pointerdown.left.stop="startEdgeDrag($event, node.id, portIdx)"
                @click.stop="showPortMenu($event, node.id, portIdx)"
              />
              <span
                v-for="(label, portIdx) in getBranchPortLabels(node)"
                :key="'label-' + portIdx"
                class="port-label"
                :class="{ visible: node.id === selectedNodeId || nodeDrag?.nodeId === node.id || edgeDrag?.fromNodeId === node.id }"
                :style="{ top: (getPortOutY(node, portIdx) - 7) + 'px' }"
              >{{ label }}</span>
            </template>
            <div
              v-else-if="node.type !== 'END'"
              class="port port-out"
              @pointerdown.left.stop="startEdgeDrag($event, node.id, 0)"
              @click.stop="showPortMenu($event, node.id)"
            />
            <button
              class="node-trial-btn"
              title="试运行"
              :disabled="!canRunTest"
              @pointerdown.stop
              @click.stop="enterTrialMode(node.id)"
            >
              <Play :size="12" />
            </button>
            <div v-if="trialStatusNodeId === node.id && trialStatus !== 'idle'" class="node-trial-status">
              <Loader2 v-if="trialStatus === 'running'" :size="14" class="spin" />
              <Check v-else-if="trialStatus === 'success'" :size="14" class="status-success" />
              <X v-else :size="14" class="status-error" />
            </div>
          </div>

          <div v-if="portMenu" class="port-menu" :style="{ left: `${portMenu.x}px`, top: `${portMenu.y}px` }">
            <div v-for="cat in NODE_CATEGORIES" :key="cat.label" class="port-menu-cat">
              <div class="port-menu-cat-title">{{ cat.label }}</div>
              <div class="port-menu-cat-grid">
                <button
                  v-for="item in cat.items"
                  :key="item.type"
                  class="port-menu-item"
                  @pointerdown.stop
                  @click="onPortMenuSelect(item.type)"
                >{{ item.label }}</button>
              </div>
            </div>
          </div>

          <div v-if="nodeContextMenu && nodeContextMenuNode" class="node-context-menu" :style="{ left: `${nodeContextMenu.x}px`, top: `${nodeContextMenu.y}px` }">
            <button type="button" :disabled="!canRunTest" @pointerdown.stop @click="enterTrialModeFromContext(nodeContextMenu.nodeId)">
              <Play :size="13" />
              <span>试运行</span>
            </button>
            <button v-if="nodeContextMenuNode.type === 'CONDITION'" type="button" :disabled="readonly" @pointerdown.stop @click="addConditionBranchFromContext">
              <GitBranch :size="13" />
              <span>添加分支</span>
            </button>
            <button v-if="nodeContextMenuNode.type === 'INTENT'" type="button" :disabled="readonly" @pointerdown.stop @click="addIntentFromContext">
              <Route :size="13" />
              <span>添加意图</span>
            </button>
            <div class="node-context-group">
              <span>添加后续节点</span>
              <button
                v-for="item in NODE_CATEGORIES.flatMap(cat => cat.items).filter(item => item.type !== 'START')"
                :key="item.type"
                type="button"
                :disabled="readonly || nodeContextMenuNode.type === 'END'"
                @pointerdown.stop
                @click="addNodeFromContext(item.type)"
              >
                <component :is="nodeIcon(item.type)" :size="13" />
                <span>{{ item.label }}</span>
              </button>
            </div>
            <button type="button" :disabled="readonly || nodeContextMenuNode.type === 'START'" @pointerdown.stop @click="duplicateNode(nodeContextMenu.nodeId)">
              <Plus :size="13" />
              <span>复制节点</span>
            </button>
            <button type="button" :disabled="readonly" @pointerdown.stop @click="disconnectNode(nodeContextMenu.nodeId)">
              <X :size="13" />
              <span>断开连线</span>
            </button>
            <button class="danger" type="button" :disabled="readonly || nodeContextMenuNode.type === 'START'" @pointerdown.stop @click="removeNode(nodeContextMenu.nodeId)">
              <Trash2 :size="13" />
              <span>删除节点</span>
            </button>
          </div>
        </div>
      </div>

      <aside v-if="selectedNode || showGlobalConfig || trialMode || workflowTestMode" class="dag-inspector">
        <div class="inspector-header">
          <strong v-if="trialMode">试运行 · {{ nodeDisplayName(trialNodeId) }}</strong>
          <strong v-else-if="workflowTestMode">流程测试</strong>
          <strong v-else-if="selectedNode">{{ NODE_TYPE_LABEL[selectedNode.type] }}</strong>
          <strong v-else>全局配置</strong>
          <button class="icon-button" title="关闭" @click="trialMode ? exitTrialMode() : workflowTestMode ? exitWorkflowTest() : (selectedNodeId = '', showGlobalConfig = false)">
            <X :size="14" />
          </button>
        </div>

        <template v-if="workflowTestMode">
          <label>
            输入
            <textarea v-model="workflowTestInput" rows="5" placeholder="输入内容..." class="mono" />
          </label>
          <button class="trial-run-btn" :disabled="workflowTestRunning || !canRunTest" @click="runWorkflowTest">
            <Play :size="14" />
              {{ workflowTestRunning ? '运行中...' : '运行' }}
          </button>
          <div v-if="workflowTestResult" class="workflow-result">
            <div :class="['workflow-result-status', workflowTestResult.success ? 'test-ok' : 'test-fail']">
              {{ workflowTestResult.success ? '执行成功' : '执行失败' }}
              <span v-if="workflowTestResult.durationMs"> · {{ workflowTestResult.durationMs }}ms</span>
            </div>
            <div v-if="(workflowTestResult.steps && workflowTestResult.steps.length > 0) || workflowTestResult.finalReply" class="workflow-result-steps">
              <strong>执行步骤</strong>
              <div v-for="(step, idx) in (workflowTestResult.steps ?? [])" :key="idx" class="workflow-step" @click="selectedNodeId = step.nodeId">
                <span class="workflow-step-idx">{{ idx + 1 }}</span>
                <span class="workflow-step-id">{{ nodeDisplayName(step.nodeId) }}</span>
                <span class="workflow-step-type">{{ NODE_TYPE_LABEL[step.type as NodeType] ?? '节点' }}</span>
                <span :class="['workflow-step-status', step.status === 'SUCCESS' ? 'test-ok' : 'test-fail']">{{ workflowRunStatusLabel(step.status) }}</span>
                <span class="workflow-step-ms">{{ step.durationMs }}ms</span>
              </div>
              <div v-if="workflowTestResult.finalReply" class="workflow-step workflow-step-final">
                <span class="workflow-step-idx">{{ (workflowTestResult.steps?.length ?? 0) + 1 }}</span>
                <span class="workflow-step-id">最终输出</span>
                <span class="workflow-step-type">回复结果</span>
                <span class="workflow-step-status test-ok">成功</span>
              </div>
              <div v-if="workflowTestResult.finalReply" class="workflow-final-output">
                <pre>{{ workflowFinalReplyText(workflowTestResult.finalReply) }}</pre>
              </div>
              <div v-if="workflowTestResult.steps?.some(s => s.status === 'FAILED' && s.output)" class="workflow-step-error">
                <strong>失败原因</strong>
                <pre>节点执行失败，请检查该节点配置</pre>
              </div>
            </div>
            <div v-if="workflowTestResult.errorMessage" class="workflow-result-error">{{ workflowTestResult.errorMessage }}</div>
          </div>
        </template>

        <template v-else-if="trialMode">
          <label>
            输入
            <textarea v-model="trialInput" rows="8" placeholder="输入内容..." class="mono" />
          </label>
          <label>
            输出
            <textarea :value="trialOutput" rows="8" readonly placeholder="运行结果..." class="mono" />
          </label>
          <button class="trial-run-btn" :disabled="trialRunning || !canRunTest" @click="runTrial">
            <Play :size="14" />
            {{ trialRunning ? '运行中...' : '运行' }}
          </button>
        </template>

        <template v-else-if="selectedNode">
          <label>
            节点名称
            <input :value="nodeLabel(selectedNode)" disabled />
          </label>
          <label>
            类型
            <input :value="NODE_TYPE_LABEL[selectedNode.type]" disabled />
          </label>
          <div class="field-section">
            <div class="field-section-header">
              <strong>输入字段</strong>
              <button v-if="!readonly" class="icon-button" title="添加" @click="addInputField"><Plus :size="13" /></button>
            </div>
            <div v-if="(selectedNode.inputFields ?? []).length === 0" class="field-empty">无输入字段</div>
            <div v-for="(field, idx) in selectedNode.inputFields ?? []" :key="idx" class="field-item">
              <div class="field-row">
                <select class="field-type" :value="field.type ?? 'string'" :disabled="readonly" @change="updateInputField(idx, { type: ($event.target as HTMLSelectElement).value as any })">
                  <option value="string">文本</option>
                  <option value="number">数字</option>
                  <option value="boolean">布尔</option>
                  <option value="json">结构化</option>
                  <option value="object">对象</option>
                  <option value="array">数组</option>
                </select>
                <input class="field-label" :value="field.label" placeholder="说明" :disabled="readonly" @change="updateInputField(idx, { label: ($event.target as HTMLInputElement).value })" />
                <button v-if="!readonly" class="icon-button field-del" title="删除" @click="removeInputField(idx)"><Trash2 :size="12" /></button>
              </div>
              <div class="field-row field-ref-row">
                <span class="field-ref-label">引用</span>
                <select class="field-ref-select" :value="field.ref ?? ''" :disabled="readonly" @change="updateInputField(idx, { ref: ($event.target as HTMLSelectElement).value })">
                  <option value="">选择变量</option>
                  <option v-for="varRef in getUpstreamVariableOptions(selectedNode.id)" :key="varRef.value" :value="varRef.value">{{ varRef.label }}</option>
                </select>
              </div>
            </div>
          </div>

          <div class="field-section">
            <div class="field-section-header">
              <strong>输出字段</strong>
              <button v-if="!readonly" class="icon-button" title="添加" @click="addOutputField"><Plus :size="13" /></button>
            </div>
            <div v-if="(selectedNode.outputFields ?? []).length === 0" class="field-empty">无输出字段</div>
            <div v-for="(field, idx) in selectedNode.outputFields ?? []" :key="idx" class="field-item">
              <div class="field-row">
                <select class="field-type" :value="field.type ?? 'string'" :disabled="readonly" @change="updateOutputField(idx, { type: ($event.target as HTMLSelectElement).value as any })">
                  <option value="string">文本</option>
                  <option value="number">数字</option>
                  <option value="boolean">布尔</option>
                  <option value="json">结构化</option>
                  <option value="object">对象</option>
                  <option value="array">数组</option>
                </select>
                <input class="field-label" :value="field.label" placeholder="说明" :disabled="readonly" @change="updateOutputField(idx, { label: ($event.target as HTMLInputElement).value })" />
                <button v-if="!readonly" class="icon-button field-del" title="删除" @click="removeOutputField(idx)"><Trash2 :size="12" /></button>
              </div>
            </div>
          </div>

          <div v-if="supportsPrompt(selectedNode.type)" class="field-section">
            <div class="field-section-header">
              <strong>输出格式</strong>
              <button v-if="!readonly && ['object', 'json'].includes(singleOutputFormatType(selectedNode))" class="icon-button" title="添加" @click="addOutputFormat"><Plus :size="13" /></button>
            </div>
            <label>
              输出类型
              <select :value="singleOutputFormatType(selectedNode)" :disabled="readonly" @change="setSingleOutputFormatType(($event.target as HTMLSelectElement).value as OutputType)">
                <option value="string">文本</option>
                <option value="number">数字</option>
                <option value="boolean">布尔</option>
                <option value="object">对象</option>
                <option value="json">结构化</option>
                <option value="array">数组</option>
              </select>
            </label>
            <div v-if="(selectedNode.outputFormat ?? []).length === 0" class="field-empty">默认文本输出</div>
            <div v-for="(field, idx) in selectedNode.outputFormat ?? []" :key="idx" class="field-item">
              <div v-if="['object', 'json'].includes(singleOutputFormatType(selectedNode))" class="field-row">
                <select class="field-type" :value="field.type || 'string'" :disabled="readonly" @change="updateOutputFormat(idx, { type: ($event.target as HTMLSelectElement).value })">
                  <option value="string">文本</option>
                  <option value="number">数字</option>
                  <option value="boolean">布尔</option>
                  <option value="object">对象</option>
                  <option value="json">结构化</option>
                  <option value="array">数组</option>
                </select>
                <input class="field-ref" :value="field.description" placeholder="说明" :disabled="readonly" @change="updateOutputFormat(idx, { description: ($event.target as HTMLInputElement).value })" />
                <button v-if="!readonly" class="icon-button field-del" title="删除" @click="removeOutputFormat(idx)"><Trash2 :size="12" /></button>
              </div>
              <div v-else-if="!['array', 'object'].includes(field.type)" class="field-row">
                <input class="field-ref" :value="field.description" placeholder="输出说明" :disabled="readonly" @change="updateOutputFormat(idx, { description: ($event.target as HTMLInputElement).value })" />
              </div>
              <div v-if="field.type === 'array'" class="array-fields">
                <div class="field-section-header">
                  <strong>数组项字段</strong>
                  <button v-if="!readonly" class="icon-button" title="添加" @click="addOutputFormatItem(idx)"><Plus :size="13" /></button>
                </div>
                <div v-if="(field.itemFields ?? []).length === 0" class="field-empty">未配置数组项</div>
                <div v-for="(item, itemIdx) in field.itemFields ?? []" :key="itemIdx" class="field-row">
                  <select class="field-type" :value="item.type || 'string'" :disabled="readonly" @change="updateOutputFormatItem(idx, itemIdx, { type: ($event.target as HTMLSelectElement).value })">
                    <option value="string">文本</option>
                    <option value="number">数字</option>
                    <option value="boolean">布尔</option>
                    <option value="object">对象</option>
                    <option value="json">结构化</option>
                  </select>
                  <input class="field-ref" :value="item.description" placeholder="说明" :disabled="readonly" @change="updateOutputFormatItem(idx, itemIdx, { description: ($event.target as HTMLInputElement).value })" />
                  <button v-if="!readonly" class="icon-button field-del" title="删除" @click="removeOutputFormatItem(idx, itemIdx)"><Trash2 :size="12" /></button>
                </div>
              </div>
              <div v-if="field.type === 'object'" class="array-fields">
                <div class="field-section-header">
                  <strong>对象字段</strong>
                  <button v-if="!readonly" class="icon-button" title="添加" @click="addOutputFormatItem(idx)"><Plus :size="13" /></button>
                </div>
                <div v-if="(field.itemFields ?? []).length === 0" class="field-empty">未配置对象字段</div>
                <div v-for="(item, itemIdx) in field.itemFields ?? []" :key="itemIdx" class="field-row">
                  <select class="field-type" :value="item.type || 'string'" :disabled="readonly" @change="updateOutputFormatItem(idx, itemIdx, { type: ($event.target as HTMLSelectElement).value })">
                    <option value="string">文本</option>
                    <option value="number">数字</option>
                    <option value="boolean">布尔</option>
                    <option value="object">对象</option>
                    <option value="json">结构化</option>
                    <option value="array">数组</option>
                  </select>
                  <input class="field-ref" :value="item.description" placeholder="说明" :disabled="readonly" @change="updateOutputFormatItem(idx, itemIdx, { description: ($event.target as HTMLInputElement).value })" />
                  <button v-if="!readonly" class="icon-button field-del" title="删除" @click="removeOutputFormatItem(idx, itemIdx)"><Trash2 :size="12" /></button>
                </div>
              </div>
            </div>
            <span v-if="singleOutputFormatType(selectedNode) === 'string'" class="field-hint">运行结果会被裁剪成文本值</span>
            <span v-else-if="singleOutputFormatType(selectedNode) === 'array'" class="field-hint">数组项只保留已配置字段，缺失值为 null</span>
            <span v-else-if="(selectedNode.outputFormat ?? []).length > 0" class="field-hint">对象只保留已配置字段，缺失值为 null</span>
          </div>

          <div v-if="selectedNode.type === 'KNOWLEDGE'" class="field-section">
            <div class="field-section-header">
              <strong>知识检索</strong>
            </div>
            <label>
              知识库
              <select :value="knowledgeSpaceId(selectedNode)" :disabled="readonly" @change="updateKnowledgeSpace(($event.target as HTMLSelectElement).value)">
                <option value="">请选择知识库</option>
                <option
                  v-for="space in knowledgeSpaces"
                  :key="space.spaceId"
                  :value="String(space.spaceId)"
                  :disabled="space.status !== 'ACTIVE'"
                >
                  {{ space.spaceName }} · {{ space.statusName || space.status }}
                </option>
              </select>
            </label>
            <label>
              返回切片数
              <input
                type="number"
                min="1"
                max="20"
                step="1"
                :value="knowledgeTopK(selectedNode)"
                :disabled="readonly"
                @input="updateKnowledgeTopK(Number(($event.target as HTMLInputElement).value))"
              />
            </label>
          </div>

          <template v-if="supportsPrompt(selectedNode.type) && selectedNode.type !== 'APPROVAL' && selectedNode.type !== 'INTENT'">
            <label>
              模型
              <select :value="String(selectedNode.config.modelId ?? '')" :disabled="readonly" @change="updateConfig('modelId', ($event.target as HTMLSelectElement).value)">
                <option value="">未选择</option>
                <option v-for="m in models" :key="m.modelId" :value="m.modelId">{{ m.displayName }} · {{ providerLabel(m.provider) }}</option>
              </select>
            </label>
            <label>
              提示词
              <textarea :value="String(selectedNode.config.prompt ?? '')" :disabled="readonly" rows="5" @input="updateConfig('prompt', ($event.target as HTMLTextAreaElement).value)" />
            </label>
          </template>

          <template v-if="selectedNode.type === 'INTENT'">
            <label>
              模型
              <select :value="String(selectedNode.config.modelId ?? '')" :disabled="readonly" @change="updateConfig('modelId', ($event.target as HTMLSelectElement).value)">
                <option value="">未选择</option>
                <option v-for="m in models" :key="m.modelId" :value="m.modelId">{{ m.displayName }} · {{ providerLabel(m.provider) }}</option>
              </select>
            </label>
          </template>

          <div v-if="selectedNode.type === 'INTENT'" class="field-section">
            <div class="field-section-header">
              <strong>意图列表</strong>
              <button v-if="!readonly" class="icon-button" title="添加意图" @click="addIntent"><Plus :size="13" /></button>
            </div>
            <div v-if="getIntents(selectedNode).length === 0" class="field-empty">未配置意图，输出端口将显示为单一出口</div>
            <div v-for="(intent, idx) in getIntents(selectedNode)" :key="idx" class="field-row">
              <input class="field-ref" :value="intent" placeholder="意图名称" :disabled="readonly" @change="updateIntent(idx, ($event.target as HTMLInputElement).value)" />
              <button v-if="!readonly" class="icon-button field-del" title="删除" @click="removeIntent(idx)"><Trash2 :size="12" /></button>
            </div>
            <span v-if="getIntents(selectedNode).length > 0" class="field-hint">{{ getIntents(selectedNode).length + 1 }} 个输出端口（含"其他"兜底）</span>
          </div>

          <template v-if="selectedNode.type === 'APPROVAL'">
            <label>
              模型
              <select :value="String(selectedNode.config.modelId ?? '')" :disabled="readonly" @change="updateConfig('modelId', ($event.target as HTMLSelectElement).value)">
                <option value="">未选择</option>
                <option v-for="m in models" :key="m.modelId" :value="m.modelId">{{ m.displayName }} · {{ providerLabel(m.provider) }}</option>
              </select>
            </label>
            <div class="field-section">
              <div class="field-section-header">
                <strong>数据源</strong>
              </div>
              <div class="datasource-list">
                <label
                  v-for="ds in DATA_SOURCES"
                  :key="ds.key"
                  class="datasource-item"
                >
                  <input
                    type="checkbox"
                    :checked="hasDataSource(ds.key)"
                    :disabled="readonly"
                    @change="toggleDataSource(ds.key)"
                  />
                  <span class="ds-label">{{ ds.label }}</span>
                  <span class="ds-desc">{{ ds.desc }}</span>
                </label>
              </div>
            </div>
            <label>
              审核提示词
              <textarea :value="String(selectedNode.config.prompt ?? '')" :disabled="readonly" rows="5" @input="updateConfig('prompt', ($event.target as HTMLTextAreaElement).value)" />
            </label>
            <label>
              置信度阈值
              <input
                type="number"
                min="0"
                max="1"
                step="0.05"
                :value="Number(selectedNode.config.confidenceThreshold ?? 0.7)"
                :disabled="readonly"
                @input="updateConfig('confidenceThreshold', Number(($event.target as HTMLInputElement).value))"
              />
              <span class="field-hint">低于此值自动转人工 (0-1)</span>
            </label>
          </template>

          <template v-if="selectedNode.type === 'TOOL'">
            <div class="field-section">
              <div class="field-section-header">
                <strong>数据源</strong>
              </div>
              <div class="datasource-list">
                <label
                  v-for="ds in DATA_SOURCES"
                  :key="ds.key"
                  class="datasource-item"
                >
                  <input
                    type="checkbox"
                    :checked="hasDataSource(ds.key)"
                    :disabled="readonly"
                    @change="toggleDataSource(ds.key)"
                  />
                  <span class="ds-label">{{ ds.label }}</span>
                  <span class="ds-desc">{{ ds.desc }}</span>
                </label>
              </div>
              <div v-if="hasDataSource('leaveBalanceFields')" class="field-hint">已输出假期余额字段</div>
              <div v-if="hasDataSource('departmentHeadcountFields')" class="field-hint">已输出部门人数字段</div>
            </div>
            <label>
              目标用户
              <select :value="String(selectedNode.config.targetUserId ?? REF_CURRENT_USER)" :disabled="readonly" @change="updateTargetUser(($event.target as HTMLSelectElement).value)">
                <option v-for="item in TARGET_USER_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <label v-if="!selectedNode.config.dataSources">
              工具
              <select :value="String(selectedNode.config[['tool', 'Code'].join('')] ?? '')" :disabled="readonly" @change="updateTool(($event.target as HTMLSelectElement).value)">
                <option value="">选择工具</option>
                <option v-for="t in tools" :key="t.toolId" :value="t[['tool', 'Code'].join('') as keyof typeof t] ?? ''">{{ t.toolName || '未命名工具' }}</option>
              </select>
            </label>
          </template>

          <template v-if="selectedNode.type === 'CONDITION'">
            <label>
              判断字段
              <select :value="String(selectedNode.config.field ?? '')" :disabled="readonly" @change="updateConfig('field', ($event.target as HTMLSelectElement).value)">
                <option value="">请选择上游字段</option>
                <option v-for="item in conditionFieldOptions(selectedNode.id)" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <div class="field-section">
              <div class="field-section-header">
                <strong>条件分支</strong>
                <button v-if="!readonly" class="icon-button" title="添加分支" @click="addConditionBranch"><Plus :size="13" /></button>
              </div>
              <div v-if="getConditionBranches(selectedNode).length === 0" class="field-empty">未配置分支</div>
              <div v-for="(branch, idx) in getConditionBranches(selectedNode)" :key="idx" class="field-item">
                <div class="field-row">
                  <input class="field-key" :value="branch.match" placeholder="匹配值" :disabled="readonly" @change="updateConditionBranch(idx, { match: ($event.target as HTMLInputElement).value })" />
                  <select class="field-ref" :value="branch.next" :disabled="readonly" @change="updateConditionBranch(idx, { next: ($event.target as HTMLSelectElement).value })">
                    <option value="">选择节点</option>
                    <option v-for="n in selectableTargets" :key="n.id" :value="n.id">{{ nodeLabel(n) }}</option>
                  </select>
                  <button v-if="!readonly" class="icon-button field-del" title="删除" @click="removeConditionBranch(idx)"><Trash2 :size="12" /></button>
                </div>
              </div>
              <span class="field-hint">每个分支会在画布上生成一个真实出口</span>
            </div>
            <label>
              默认节点
              <select :value="String(selectedNode.config.default ?? '')" :disabled="readonly" @change="updateConfig('default', ($event.target as HTMLSelectElement).value)">
                <option value="">未设置</option>
                <option v-for="n in selectableTargets" :key="n.id" :value="n.id">{{ nodeLabel(n) }}</option>
              </select>
            </label>
          </template>

          <template v-if="selectedNode.type === 'END'">
            <label>
              返回格式
              <select :value="endReplyMode(selectedNode)" :disabled="readonly" @change="setEndReplyMode(($event.target as HTMLSelectElement).value as 'text' | 'json')">
                <option value="text">文本</option>
                <option value="json">结构化</option>
              </select>
            </label>
            <label v-if="endReplyMode(selectedNode) === 'text'">
              回复模板
              <textarea :value="String(selectedNode.config.reply ?? '')" :disabled="readonly" rows="4" @input="updateConfig('reply', ($event.target as HTMLTextAreaElement).value)" />
            </label>
            <div v-if="endReplyMode(selectedNode) === 'json'" class="field-section card-config">
              <div class="field-section-header">
                <strong>响应卡片</strong>
              </div>
              <label>
                分类
                <select :value="String(endReplyJson(selectedNode).category ?? 'QA')" :disabled="readonly" @change="updateEndCard('category', ($event.target as HTMLSelectElement).value)">
                  <option v-for="item in CARD_CATEGORIES" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </label>
              <label>
                状态
                <select :value="String(endReplyJson(selectedNode).status ?? 'SUCCESS')" :disabled="readonly" @change="updateEndCard('status', ($event.target as HTMLSelectElement).value)">
                  <option v-for="item in CARD_STATUSES" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </label>
              <label>
                标题
                <input :value="String(endReplyJson(selectedNode).title ?? '')" :disabled="readonly" @input="updateEndCard('title', ($event.target as HTMLInputElement).value)" />
              </label>
              <label>
                回复内容
                <textarea :value="String(endReplyJson(selectedNode).answer ?? '')" :disabled="readonly" rows="4" @input="updateEndCard('answer', ($event.target as HTMLTextAreaElement).value)" />
              </label>
              <label>
                说明
                <input :value="String(endReplyJson(selectedNode).reason ?? '')" :disabled="readonly" @input="updateEndCard('reason', ($event.target as HTMLInputElement).value)" />
              </label>
              <label>
                置信度
                <select :value="String(endReplyJson(selectedNode).confidence ?? '')" :disabled="readonly" @change="updateEndCard('confidence', ($event.target as HTMLSelectElement).value)">
                  <option value="">不显示</option>
                  <option value="high">高</option>
                  <option value="medium">中</option>
                  <option value="low">低</option>
                </select>
              </label>
              <label>
                字段来源
                <select :value="String(endReplyData(selectedNode).fields ?? '')" :disabled="readonly" @change="updateEndCardData('fields', ($event.target as HTMLSelectElement).value)">
                  <option v-for="item in CARD_FIELD_SOURCES" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </label>
              <div class="field-section">
                <div class="field-section-header">
                  <strong>操作</strong>
                  <button v-if="!readonly" class="icon-button" title="添加" @click="addEndCardAction"><Plus :size="13" /></button>
                </div>
                <div v-if="endReplyActions(selectedNode).length === 0" class="field-empty">无操作</div>
                <div v-for="(action, idx) in endReplyActions(selectedNode)" :key="idx" class="field-item">
                  <div class="field-row">
                    <select class="field-key" :value="String(action.type ?? ACTION_OPEN_BUSINESS)" :disabled="readonly" @change="updateEndCardAction(idx, 'type', ($event.target as HTMLSelectElement).value)">
                      <option v-for="item in CARD_ACTION_TYPES" :key="item.value" :value="item.value">{{ item.label }}</option>
                    </select>
                    <input class="field-label" :value="String(action.label ?? '')" placeholder="按钮" :disabled="readonly" @change="updateEndCardAction(idx, 'label', ($event.target as HTMLInputElement).value)" />
                    <button v-if="!readonly" class="icon-button field-del" title="删除" @click="removeEndCardAction(idx)"><Trash2 :size="12" /></button>
                  </div>
                  <select
                    v-if="String(action.type ?? ACTION_OPEN_BUSINESS) === ACTION_OPEN_BUSINESS"
                    class="field-ref"
                    :value="String(action.target ?? '/workbench/requests')"
                    :disabled="readonly"
                    @change="updateEndCardAction(idx, 'target', ($event.target as HTMLSelectElement).value)"
                  >
                    <option v-for="item in CARD_ACTION_TARGET_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
                  </select>
                  <input
                    v-else
                    class="field-ref"
                    :value="String(action.target ?? '')"
                    placeholder="目标地址"
                    :disabled="readonly"
                    @change="updateEndCardAction(idx, 'target', ($event.target as HTMLInputElement).value)"
                  />
                </div>
              </div>
            </div>
          </template>

          <div v-if="selectedNodeEdges.length > 0" class="edge-section">
            <strong>连线</strong>
            <div v-for="edge in selectedNodeEdges" :key="`${edge.from}-${edge.fromPort ?? 0}-${edge.to}`" class="edge-item">
              <span>{{ nodeDisplayName(edge.from) }} → {{ nodeDisplayName(edge.to) }}</span>
              <button class="icon-button" type="button" title="删除" :disabled="readonly" @click="removeEdge(edge.from, edge.to, edge.fromPort)">
                <Trash2 :size="13" />
              </button>
            </div>
          </div>

          <button v-if="selectedNode.type !== 'START'" class="delete-btn" :disabled="readonly" @click="removeNode(selectedNode.id)">
            <Trash2 :size="14" /> 删除节点
          </button>
        </template>

        <template v-else-if="showGlobalConfig">
          <label>
            系统提示词
            <textarea :value="systemPrompt" :disabled="readonly" rows="6" placeholder="你是一个企业协同助手..." @input="emit('update:systemPrompt', ($event.target as HTMLTextAreaElement).value)" />
          </label>
          <label>
            随机程度
            <input
              type="number"
              min="0"
              max="1"
              step="0.1"
              :value="modelConfigForm.temperature"
              :disabled="readonly"
              @input="updateModelConfig('temperature', Number(($event.target as HTMLInputElement).value))"
            />
          </label>
          <label>
            回复长度
            <select
              :value="modelConfigForm.maxTokens"
              :disabled="readonly"
              @change="updateModelConfig('maxTokens', Number(($event.target as HTMLSelectElement).value))"
            >
              <option :value="512">较短</option>
              <option :value="1024">标准</option>
              <option :value="2048">较长</option>
              <option :value="4096">很长</option>
            </select>
          </label>
        </template>

      </aside>
    </div>
  </div>
</template>

<style scoped>
.dag-editor {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  min-width: 0;
  background: var(--surface);
}

.dag-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-sm) var(--space-base);
  border-bottom: 1px solid var(--line);
  background: var(--panel);
  flex-shrink: 0;
  z-index: 5;
}

.dag-toolbar-left,
.dag-toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

.dag-toolbar-right {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.tb-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs);
  min-height: 34px;
  padding: 0 var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  box-shadow: var(--shadow-sm);
  transition: border-color 0.12s, background 0.12s, color 0.12s;
}

.tb-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.tb-btn:hover:not(:disabled) {
  border-color: var(--green-light);
  color: var(--green-dark);
}

.tb-btn.active,
.tb-btn.tb-primary {
  background: var(--green);
  border-color: var(--green);
  color: var(--panel-strong);
}

.tb-btn.tb-primary:hover:not(:disabled),
.tb-btn.active:hover:not(:disabled) {
  color: var(--panel-strong);
}

.tb-metric {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--panel);
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.tb-sep { width: 1px; height: 20px; background: var(--line); margin: 0 4px; }
.tb-hint { margin-left: auto; color: var(--muted); font-size: 11px; white-space: nowrap; }
.tb-error {
  color: var(--red-dark);
  font-size: 11px;
  font-weight: 700;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tb-add-wrap { position: relative; }

.node-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 30;
  width: 320px;
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel);
  box-shadow: var(--shadow);
}

.node-cat { margin-bottom: var(--space-md); }
.node-cat:last-child { margin-bottom: 0; }

.node-cat-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--muted);
  margin-bottom: var(--space-xs);
  text-align: left;
}

.node-cat-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-xs);
}

.node-cat-item {
  display: block;
  width: 100%;
  min-height: 34px;
  padding: 7px var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  text-align: left;
  transition: background 0.1s, border-color 0.1s;
}

.node-cat-item:hover:not(:disabled) {
  background: var(--hover-bg);
  border-color: var(--green-light);
  color: var(--green-dark);
}

.node-cat-item:disabled { opacity: 0.5; cursor: not-allowed; }

.dag-body {
  display: flex;
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.dag-canvas {
  position: relative;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  background: var(--surface);
  cursor: default;
}

.dag-canvas.panning { cursor: grab; }

.dag-viewport {
  position: absolute;
  inset: 0;
  transform-origin: 0 0;
  will-change: transform;
}

.dag-edges {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: visible;
}

.edge-path {
  fill: none;
  stroke: var(--dag-edge);
  stroke-width: 2.5;
  marker-end: url(#arrow);
  filter: drop-shadow(0 1px 1px var(--dag-edge-shadow));
}

.edge-arrow { fill: var(--dag-edge-arrow); }
.edge-group { pointer-events: stroke; }
.edge-group:hover .edge-path { stroke: var(--dag-edge-hover); stroke-width: 3.5; }

.edge-delete-wrap { overflow: visible; pointer-events: all; opacity: 0; transition: opacity 0.15s; }
.edge-group:hover .edge-delete-wrap { opacity: 1; }

.edge-delete-btn {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.5px solid var(--red);
  background: var(--panel-strong);
  color: var(--red);
  font-size: 14px;
  font-weight: 900;
  line-height: 1;
  cursor: pointer;
  display: grid;
  place-items: center;
  padding: 0;
  box-shadow: var(--shadow-sm);
}

.edge-delete-btn:hover { background: var(--error-bg); }
.edge-delete-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.edge-temp { fill: none; stroke: var(--dag-edge-temp); stroke-width: 2; stroke-dasharray: 6 4; }

.dag-node {
  position: absolute;
  z-index: 1;
  width: 236px;
  height: 118px;
  min-height: 118px;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 8px;
  align-content: start;
  justify-items: start;
  padding: var(--space-sm) var(--space-base);
  border: 1px solid var(--line);
  border-top: 3px solid var(--green);
  border-left: 5px solid var(--green);
  border-radius: var(--radius-lg);
  background: var(--panel);
  color: var(--ink);
  touch-action: none;
  user-select: none;
  cursor: move;
  overflow: visible;
  box-shadow: var(--shadow-sm);
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s, background 0.15s;
}

.dag-node:hover {
  box-shadow: var(--shadow);
  transform: translateY(-1px);
}

.dag-node.selected {
  border-color: var(--green-light);
  box-shadow: 0 0 0 4px var(--dag-selected-ring), var(--shadow);
}

.type-tool { border-top-color: var(--amber); border-left-color: var(--amber); background: linear-gradient(90deg, var(--dag-tool-bg), var(--panel)); }
.type-condition { border-top-color: var(--red); border-left-color: var(--red); background: linear-gradient(90deg, var(--dag-condition-bg), var(--panel)); }
.type-intent { border-top-color: var(--dag-intent); border-left-color: var(--dag-intent); background: linear-gradient(90deg, var(--dag-intent-bg), var(--panel)); }
.type-approval { border-top-color: var(--dag-approval); border-left-color: var(--dag-approval); background: linear-gradient(90deg, var(--dag-approval-bg), var(--panel)); }
.type-knowledge { border-top-color: var(--dag-knowledge); border-left-color: var(--dag-knowledge); background: linear-gradient(90deg, var(--dag-knowledge-bg), var(--panel)); }
.type-form_extract { border-top-color: var(--dag-form-extract); border-left-color: var(--dag-form-extract); background: linear-gradient(90deg, var(--dag-form-extract-bg), var(--panel)); }
.type-summary { border-top-color: var(--dag-summary); border-left-color: var(--dag-summary); background: linear-gradient(90deg, var(--dag-summary-bg), var(--panel)); }
.type-transform { border-top-color: var(--dag-transform); border-left-color: var(--dag-transform); background: linear-gradient(90deg, var(--dag-transform-bg), var(--panel)); }
.type-end { border-top-color: var(--green-dark); border-left-color: var(--green-dark); }

.node-header {
  width: 100%;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-xs);
  min-width: 0;
}

.node-icon {
  width: 24px;
  height: 24px;
  display: inline-grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
  color: var(--green-dark);
  border: 1px solid var(--line);
}

.node-type {
  display: inline-flex;
  align-items: center;
  justify-self: start;
  min-height: 20px;
  max-width: 100%;
  padding: 0 7px;
  border-radius: var(--radius-pill);
  background: var(--dag-node-type-bg);
  color: var(--green-dark);
  font-size: 10px;
  font-weight: 900;
  line-height: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-index {
  color: var(--muted);
  font-size: 11px;
  font-weight: 900;
  line-height: 1;
}

.type-tool .node-type { background: var(--dag-tool-bg); color: var(--dag-tool-text); }
.type-condition .node-type { background: var(--dag-condition-bg); color: var(--red-dark); }
.type-intent .node-type { background: var(--dag-intent-bg); color: var(--dag-intent-text); }
.type-approval .node-type { background: var(--dag-approval-bg); color: var(--dag-approval-text); }
.type-knowledge .node-type { background: var(--dag-knowledge-bg); color: var(--dag-knowledge); }
.type-form_extract .node-type { background: var(--dag-form-extract-bg); color: var(--dag-form-extract-text); }
.type-summary .node-type { background: var(--dag-summary-bg); color: var(--dag-summary-text); }
.type-transform .node-type { background: var(--dag-transform-bg); color: var(--dag-transform-text); }
.type-end .node-type { background: var(--primary-soft); color: var(--green-dark); }
.type-tool .node-icon { color: var(--dag-tool-text); background: var(--dag-tool-bg); }
.type-condition .node-icon { color: var(--red-dark); background: var(--dag-condition-bg); }
.type-intent .node-icon { color: var(--dag-intent-text); background: var(--dag-intent-bg); }
.type-approval .node-icon { color: var(--dag-approval-text); background: var(--dag-approval-bg); }
.type-knowledge .node-icon { color: var(--dag-knowledge); background: var(--dag-knowledge-bg); }
.type-form_extract .node-icon { color: var(--dag-form-extract-text); background: var(--dag-form-extract-bg); }
.type-summary .node-icon { color: var(--dag-summary-text); background: var(--dag-summary-bg); }
.type-transform .node-icon { color: var(--dag-transform-text); background: var(--dag-transform-bg); }
.type-end .node-icon { color: var(--green-dark); background: var(--primary-soft); }

.node-output { color: var(--muted); font-size: 11px; font-weight: 700; }

.node-body {
  width: 100%;
  min-height: 0;
  display: grid;
  align-content: start;
  gap: 5px;
  overflow: hidden;
}

.node-meta {
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  max-height: 44px;
  overflow: hidden;
}

.node-field {
  max-width: 100%;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: var(--radius-pill);
  font-weight: 700;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-field.in { background: var(--dag-node-type-bg); color: var(--green-dark); }
.node-field.out { background: var(--dag-tool-bg); color: var(--dag-tool-text); }

.port {
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid var(--dag-port-border);
  background: var(--green);
  z-index: 2;
  box-shadow: 0 0 0 1px var(--dag-port-ring), 0 2px 8px var(--dag-port-shadow);
  transition: background 0.15s, transform 0.15s, box-shadow 0.15s;
}

.port-in { left: -7px; top: 59px; transform: translateY(-50%); background: var(--dag-port-border); }
.port-out { right: -7px; top: 59px; transform: translateY(-50%); cursor: crosshair; background: var(--green); }
.port-out:hover { transform: translateY(-50%) scale(1.3); }
.port-in.hovered {
  background: var(--green);
  transform: translateY(-50%) scale(1.4);
  box-shadow: 0 0 0 5px var(--dag-port-hover-ring), 0 2px 8px var(--dag-port-shadow);
}

.port-label {
  position: absolute;
  right: -52px;
  max-width: 72px;
  font-size: 9px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  pointer-events: none;
  z-index: 1;
  opacity: 0;
  transform: translateY(-50%) translateX(-4px);
  font-weight: 700;
  transition: opacity 0.12s, transform 0.12s;
}

.dag-node:hover .port-label,
.port-label.visible {
  opacity: 1;
  transform: translateY(-50%) translateX(0);
}

.port-menu {
  position: absolute;
  z-index: 20;
  min-width: 260px;
  padding: var(--space-sm);
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line);
  box-shadow: var(--shadow);
}

.port-menu-cat { margin-bottom: 6px; }
.port-menu-cat:last-child { margin-bottom: 0; }

.port-menu-cat-title {
  padding: 2px 4px 4px;
  font-size: 10px;
  font-weight: 700;
  color: var(--muted);
  text-align: left;
}

.port-menu-cat-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-xs);
}

.port-menu-item {
  display: block;
  width: 100%;
  min-height: 30px;
  padding: 5px var(--space-sm);
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  text-align: left;
}

.port-menu-item:hover {
  background: var(--hover-bg);
  border-color: var(--green-light);
  color: var(--green-dark);
}

.node-context-menu {
  position: absolute;
  z-index: 24;
  width: 220px;
  max-height: 430px;
  overflow-y: auto;
  display: grid;
  gap: 3px;
  padding: var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel);
  box-shadow: var(--shadow);
}

.node-context-menu button,
.node-context-group button {
  width: 100%;
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  padding: 5px var(--space-sm);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--ink);
  font-size: 12px;
  font-weight: 800;
  text-align: left;
  cursor: pointer;
}

.node-context-menu button:hover:not(:disabled),
.node-context-group button:hover:not(:disabled) {
  background: var(--hover-bg);
  border-color: var(--green-light);
  color: var(--green-dark);
}

.node-context-menu button:disabled,
.node-context-group button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.node-context-menu .danger {
  color: var(--red);
}

.node-context-group {
  display: grid;
  gap: 3px;
  padding-top: var(--space-xs);
  margin-top: var(--space-xs);
  border-top: 1px solid var(--line);
}

.node-context-group > span {
  padding: 0 var(--space-xs);
  color: var(--muted);
  font-size: 10px;
  font-weight: 900;
}

.dag-inspector {
  width: 360px;
  flex-shrink: 0;
  border-left: 1px solid var(--line);
  background: var(--panel);
  padding: var(--space-base);
  overflow-y: auto;
  display: grid;
  gap: var(--space-md);
  align-content: start;
  box-shadow: var(--shadow-sm);
}

.inspector-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--line);
}

.inspector-header strong {
  color: var(--ink);
  font-size: 15px;
  font-weight: 900;
}

.dag-inspector label {
  display: grid;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.edge-section {
  display: grid;
  gap: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--line);
}

.edge-section strong { font-size: var(--text-sm); color: var(--muted); }

.edge-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: 7px var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  font-size: var(--text-xs);
}

.field-section {
  display: grid;
  gap: var(--space-sm);
  padding-top: var(--space-md);
  border-top: 1px solid var(--line);
}

.field-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.field-section-header strong { font-size: 12px; color: var(--muted); font-weight: 700; }

.field-empty { font-size: 11px; color: var(--muted-light); }

.field-item {
  display: grid;
  gap: var(--space-xs);
  padding: 6px 0;
  border-bottom: 1px solid var(--line);
}

.field-item:last-child { border-bottom: none; }

.array-fields {
  display: grid;
  gap: var(--space-xs);
  margin-left: var(--space-sm);
  padding-left: var(--space-sm);
  border-left: 2px solid var(--dag-array-border);
}

.field-row {
  display: flex;
  gap: var(--space-xs);
  align-items: center;
}

.field-ref-row {
  gap: 3px;
  padding-left: 2px;
}

.field-ref-label {
  font-size: 10px;
  color: var(--muted);
  width: 24px;
  flex-shrink: 0;
}

.field-key { flex: 1; min-width: 0; min-height: 30px; font-size: 12px; padding: 4px 7px; }
.field-label { flex: 1; min-width: 0; min-height: 30px; font-size: 12px; padding: 4px 7px; }
.field-type { width: 66px; min-height: 30px; font-size: 11px; padding: 4px 5px; flex-shrink: 0; }
.field-ref { flex: 1; min-width: 0; min-height: 30px; font-size: 11px; padding: 4px 7px; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.field-ref-select { flex: 1; min-width: 0; min-height: 28px; font-size: 11px; padding: 3px 5px; }
.field-del { width: 22px; min-height: 22px; padding: 0; border: none; background: transparent; color: var(--red); flex-shrink: 0; }

.field-hint { color: var(--muted); font-size: 11px; margin-top: 2px; }

.card-config {
  padding-top: 8px;
}

.datasource-list {
  display: grid;
  gap: var(--space-xs);
}

.datasource-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  cursor: pointer;
  transition: background 0.1s;
}

.datasource-item:hover { background: var(--hover-bg); }

.datasource-item input[type="checkbox"] {
  width: 16px;
  height: 16px;
  min-height: 16px;
  flex-shrink: 0;
}

.ds-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
  min-width: 64px;
}

.ds-desc {
  font-size: 11px;
  color: var(--muted);
}

.delete-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 34px;
  padding: 0 var(--space-md);
  border: 1px solid var(--red);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--red);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  margin-top: var(--space-sm);
}

.delete-btn:hover:not(:disabled) { background: var(--error-bg); }
.delete-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.node-trial-btn {
  position: absolute;
  top: -10px;
  right: -10px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid var(--dag-port-border);
  background: var(--green);
  color: var(--green);
  cursor: pointer;
  display: grid;
  place-items: center;
  padding: 0;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, transform 0.15s;
  z-index: 3;
  box-shadow: var(--shadow-sm);
}

.dag-node:hover .node-trial-btn:not(:disabled) { opacity: 1; }
.node-trial-btn,
.node-trial-btn:hover {
  color: var(--panel-strong);
}

.node-trial-btn:hover { background: var(--green-dark); transform: scale(1.08); }
.node-trial-btn:disabled {
  opacity: 0;
  cursor: not-allowed;
}

.node-trial-status {
  position: absolute;
  bottom: -24px;
  left: 50%;
  transform: translateX(-50%);
  display: grid;
  place-items: center;
  z-index: 3;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.spin { animation: spin 1s linear infinite; color: var(--green); }
.status-success { color: var(--success); }
.status-error { color: var(--red); }

.trial-run-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs);
  min-height: 38px;
  padding: 0 var(--space-base);
  border: 1px solid var(--green);
  border-radius: var(--radius-sm);
  background: var(--green);
  color: var(--panel-strong);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s;
}

.trial-run-btn:hover:not(:disabled) { background: var(--green-dark); }
.trial-run-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.workflow-result { display: grid; gap: var(--space-sm); margin-top: var(--space-sm); }
.workflow-result-status { padding: 7px var(--space-sm); border-radius: var(--radius-sm); font-size: 13px; font-weight: 700; }
.workflow-result-steps { font-size: 12px; }
.workflow-result-steps strong { display: block; margin-bottom: 4px; color: var(--muted); font-size: 11px; }
.workflow-step { display: flex; align-items: center; gap: var(--space-xs); padding: 6px 7px; border-bottom: 1px solid var(--line); font-size: 11px; }
.workflow-step:last-child { border-bottom: none; }
.workflow-step-final {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--pill-bg);
}
.workflow-step-idx { width: 20px; height: 20px; display: grid; place-items: center; border-radius: 50%; background: var(--pill-bg); font-size: 10px; font-weight: 700; flex-shrink: 0; }
.workflow-step-id { font-weight: 700; }
.workflow-step-type { color: var(--muted); font-size: 10px; }
.workflow-step-status { font-size: 10px; font-weight: 700; }
.workflow-step-ms { color: var(--muted); font-size: 10px; margin-left: auto; }
.workflow-final-output {
  margin-top: var(--space-xs);
  padding: var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
}
.workflow-final-output pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
}
.workflow-result-error { color: var(--red-dark); font-size: 12px; padding: 7px var(--space-sm); background: var(--error-bg); border-radius: var(--radius-sm); }
.workflow-step-error { margin-top: 4px; padding: 7px var(--space-sm); background: var(--error-bg); border-radius: var(--radius-sm); font-size: 11px; }
.workflow-step-error strong { display: block; margin-bottom: 4px; color: var(--red-dark); font-size: 10px; }
.workflow-step-error pre { margin: 0; white-space: pre-wrap; word-break: break-all; color: var(--red-dark); font-size: 11px; }

.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 12px; line-height: 1.5; }

.json-toggle { border-top: 1px solid var(--line); padding-top: var(--space-sm); }
.json-toggle summary { cursor: pointer; font-size: 12px; font-weight: 700; color: var(--muted); }
.json-toggle pre {
  max-height: 220px;
  overflow: auto;
  margin: var(--space-sm) 0 0;
  font-size: 11px;
  line-height: 1.45;
  white-space: pre-wrap;
  background: var(--panel);
  padding: var(--space-sm);
  border-radius: var(--radius-sm);
  border: 1px solid var(--line);
}

@media (max-width: 980px) {
  .dag-body { flex-direction: column; }
  .dag-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .dag-toolbar-left,
  .dag-toolbar-right {
    width: 100%;
    flex-wrap: wrap;
  }

  .dag-inspector {
    width: 100%;
    border-left: none;
    border-top: 1px solid var(--line);
    max-height: 40vh;
    padding-bottom: 96px;
  }
  .tb-hint { display: none; }
}
</style>
