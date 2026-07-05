<script setup lang="ts">
import { computed, onBeforeUnmount, onErrorCaptured, ref, watch } from 'vue';
import { Check, GitBranchPlus, Loader2, Play, Plus, Trash2, X } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { AgentToolResponse } from '@/api/types';

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
  { value: 'QA', label: 'QA' },
  { value: 'OA', label: 'OA' },
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

const NODE_W = 210;
const NODE_H = 86;
const PORT_OUT_X = NODE_W + 6;
const PORT_OUT_Y = 43;
const PORT_IN_X = -6;
const PORT_IN_Y = 43;

export interface LlmModel {
  modelId: string;
  displayName: string;
  provider: string;
}

const props = withDefaults(defineProps<{
  modelValue: string;
  tools: AgentToolResponse[];
  models?: LlmModel[];
  readonly?: boolean;
  versionId?: string | number;
  systemPrompt?: string;
  modelConfigJson?: string;
}>(), { models: () => [], systemPrompt: '', modelConfigJson: '' });

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
const argsText = ref('');
const toolCallsText = ref('');
const replyJsonText = ref('');
const showGlobalConfig = ref(false);
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
const prettyJson = computed(() => serializeWorkflow());
const scalePercent = computed(() => `${Math.round(scale.value * 100)}%`);

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
  return Math.max(NODE_H, 40 + labels.length * 22);
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

function getUpstreamVariables(nodeId: string): string[] {
  const upstream = getUpstreamNodes(nodeId);
  const vars: string[] = [];
  for (const node of upstream) {
    const fields = getNodeOutputFields(node);
    for (const f of fields) {
      vars.push(`{{${node.id}.${f}}}`);
    }
  }
  return vars;
}

function getPortOutY(node: DagNode | undefined | null, portIdx: number): number {
  if (!node) return PORT_OUT_Y;
  const totalPorts = nodePortCount(node);
  if (totalPorts <= 1) return PORT_OUT_Y;
  const nodeHeight = nodeVisualHeight(node);
  return 40 + portIdx * (nodeHeight - 40) / (totalPorts + 1);
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

watch(selectedNode, (node) => {
  argsText.value = node?.type === 'TOOL' && !node.config.dataSources ? strJson(node.config.args, '{\n  "userId": "{{input.userId}}"\n}') : '';
  branchText.value = node?.type === 'CONDITION' ? strJson(node.config.branches, '[\n  { "match": "YES", "next": "end" }\n]') : '';
  toolCallsText.value = node && supportsToolCalls(node.type) ? strJson(node.config.toolCalls, '[\n  {\n    "toolCode": "ORG_GET_USER_SUMMARY",\n    "args": { "userId": "{{input.userId}}" },\n    "outputKey": "user"\n  }\n]') : '';
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

function startNodeDrag(event: PointerEvent, node: DagNode) {
  selectedNodeId.value = node.id;
  portMenu.value = null;
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

function updateSelectedId(value: string) {
  const node = selectedNode.value;
  if (!node || props.readonly) return;
  const nextId = sanitize(value);
  if (!nextId || nodes.value.some((n) => n.id === nextId && n !== node)) return;
  const old = node.id;
  node.id = nextId;
  if (node.outputKey === old) node.outputKey = nextId;
  edges.value = edges.value.map((e) => ({
    from: e.from === old ? nextId : e.from,
    to: e.to === old ? nextId : e.to,
  }));
  for (const item of nodes.value) {
    if (item.type !== 'CONDITION') continue;
    const branches = getConditionBranches(item)
      .map((branch) => ({ ...branch, next: branch.next === old ? nextId : branch.next }));
    item.config = {
      ...item.config,
      branches,
      default: item.config.default === old ? nextId : item.config.default,
    };
  }
  selectedNodeId.value = nextId;
  sync();
}

function updateConfig(key: string, value: unknown) {
  const node = selectedNode.value;
  if (!node || props.readonly) return;
  node.config = { ...node.config, [key]: value };
  sync();
}

function updateOutputKey(value: string) {
  const node = selectedNode.value;
  if (!node || props.readonly) return;
  node.outputKey = value.trim() || undefined;
  sync();
}

function applyArgs() {
  try {
    updateConfig('args', argsText.value.trim() ? JSON.parse(argsText.value) : {});
    parseError.value = '';
  } catch (e) {
    parseError.value = e instanceof Error ? e.message : 'args 不是合法 JSON';
  }
}

function applyBranches() {
  try {
    updateConfig('branches', normalizeBranches(branchText.value.trim() ? JSON.parse(branchText.value) : []));
    parseError.value = '';
  } catch (e) {
    parseError.value = e instanceof Error ? e.message : 'branches 不是合法 JSON';
  }
}

function applyToolCalls() {
  try {
    updateConfig('toolCalls', toolCallsText.value.trim() ? JSON.parse(toolCallsText.value) : []);
    parseError.value = '';
  } catch (e) {
    parseError.value = e instanceof Error ? e.message : 'toolCalls 不是合法 JSON';
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
  replyJson.actions = [...endReplyActions(node), { type: 'OPEN_FLOW', label: '打开申请', target: '/workbench/requests' }];
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
      const p: Record<string, unknown> = { id: n.id, type: n.type, position: n.position };
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
    config: { dataSources: ['employeeInfo'] as string[], targetUserId: '{{input.userId}}' },
    inputFields: [{ key: 'userId', label: '目标用户', ref: '{{input.userId}}' }],
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
        { toolCode: 'KNOWLEDGE_SEARCH_CHUNKS', args: { spaceId: '', query: '{{start.query}}', topK: 5 }, outputKey: 'knowledge' },
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
      prompt: '请根据公司政策和员工信息，审核该申请是否合规。输出 APPROVE/REJECT/ESCALATE 及理由。',
      confidenceThreshold: 0.7,
    },
    inputFields: [
      { key: 'request', label: '审批申请', ref: '{{start.query}}' },
      { key: 'applicantId', label: '申请人 ID', ref: '' },
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
    if (Number.isFinite(x) && Number.isFinite(y)) return { x: Math.max(0, x), y: Math.max(0, y) };
  }
  return { x: 100 + (i % 4) * 300, y: 150 + Math.floor(i / 4) * 180 };
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
  if (!trialNodeId.value || trialRunning.value) return;
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
      trialOutput.value = result.outputJson || '{}';
      trialStatus.value = 'success';
    } else {
      trialOutput.value = result.errorMessage || '执行失败';
      trialStatus.value = 'error';
    }
  } catch (e) {
    trialOutput.value = e instanceof Error ? e.message : '请求失败';
    trialStatus.value = 'error';
  } finally {
    trialRunning.value = false;
    setTimeout(() => { trialStatus.value = 'idle'; trialStatusNodeId.value = ''; }, 3000);
  }
}

function enterWorkflowTest() {
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
  if (workflowTestRunning.value) return;
  workflowTestRunning.value = true;
  workflowTestResult.value = null;
  try {
    workflowTestResult.value = await adminApi.agentConfig.workflowRun({
      inputJson: workflowTestInput.value || '{}',
      versionId: props.versionId,
      workflowConfigJson: props.modelValue || undefined,
      systemPrompt: props.systemPrompt || undefined,
      modelConfigJson: props.modelConfigJson || undefined,
    });
  } catch (e) {
    workflowTestResult.value = { success: false, errorMessage: e instanceof Error ? e.message : '请求失败' };
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
        <button class="tb-btn" :class="{ active: workflowTestMode }" @click="workflowTestMode ? exitWorkflowTest() : enterWorkflowTest()">
          <Play :size="14" /> 流程
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
                <path d="M0,0 L0,6 L9,3 z" fill="rgba(0,137,255,0.7)" />
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
            @click.stop="selectedNodeId = node.id"
          >
            <span class="node-type">{{ NODE_TYPE_LABEL[node.type] }}</span>
            <strong class="node-id">{{ node.id }}</strong>
            <div v-if="(node.inputFields ?? []).length > 0" class="node-fields">
              <span v-for="f in node.inputFields" :key="f.key" class="node-field in">← {{ f.label || f.key }}</span>
            </div>
            <div v-if="(node.outputFields ?? []).length > 0" class="node-fields">
              <span v-for="f in node.outputFields" :key="f.key" class="node-field out">→ {{ f.label || f.key }}</span>
            </div>
            <div v-if="node.type === 'INTENT' && getIntents(node).length > 0" class="node-fields">
              <span v-for="(intent, idx) in getIntents(node)" :key="idx" class="node-field out">{{ intent }}</span>
              <span class="node-field out">其他</span>
            </div>
            <div v-if="node.type === 'CONDITION' && getBranchPortLabels(node).length > 0" class="node-fields">
              <span v-for="(label, idx) in getBranchPortLabels(node)" :key="idx" class="node-field out">{{ label }}</span>
            </div>
            <small v-if="node.outputKey && (node.outputFields ?? []).length === 0 && node.type !== 'INTENT'" class="node-output">{{ node.outputKey }}</small>
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
        </div>
      </div>

      <aside v-if="selectedNode || showGlobalConfig || trialMode || workflowTestMode" class="dag-inspector">
        <div class="inspector-header">
          <strong v-if="trialMode">试运行 · {{ trialNodeId }}</strong>
          <strong v-else-if="workflowTestMode">流程测试</strong>
          <strong v-else-if="selectedNode">{{ NODE_TYPE_LABEL[selectedNode.type] }} · {{ selectedNode.id }}</strong>
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
          <button class="trial-run-btn" :disabled="workflowTestRunning" @click="runWorkflowTest">
            <Play :size="14" />
            {{ workflowTestRunning ? '运行中...' : '运行' }}
          </button>
          <div v-if="workflowTestResult" class="workflow-result">
            <div :class="['workflow-result-status', workflowTestResult.success ? 'test-ok' : 'test-fail']">
              {{ workflowTestResult.success ? '执行成功' : '执行失败' }}
              <span v-if="workflowTestResult.durationMs"> · {{ workflowTestResult.durationMs }}ms</span>
            </div>
            <div v-if="workflowTestResult.finalReply" class="workflow-result-reply">
              <strong>最终回复</strong>
              <pre>{{ workflowTestResult.finalReply }}</pre>
            </div>
            <div v-if="workflowTestResult.steps && workflowTestResult.steps.length > 0" class="workflow-result-steps">
              <strong>执行步骤</strong>
              <div v-for="(step, idx) in workflowTestResult.steps" :key="idx" class="workflow-step" @click="selectedNodeId = step.nodeId">
                <span class="workflow-step-idx">{{ idx + 1 }}</span>
                <span class="workflow-step-id">{{ step.nodeId }}</span>
                <span class="workflow-step-type">{{ step.type }}</span>
                <span :class="['workflow-step-status', step.status === 'SUCCESS' ? 'test-ok' : 'test-fail']">{{ step.status }}</span>
                <span class="workflow-step-ms">{{ step.durationMs }}ms</span>
              </div>
              <div v-if="workflowTestResult.steps.some(s => s.status === 'FAILED' && s.output)" class="workflow-step-error">
                <strong>错误详情</strong>
                <pre>{{ workflowTestResult.steps.find(s => s.status === 'FAILED')?.output }}</pre>
              </div>
            </div>
            <div v-if="workflowTestResult.errorMessage" class="workflow-result-error">{{ workflowTestResult.errorMessage }}</div>
          </div>
        </template>

        <template v-else-if="trialMode">
          <label>
            输入
            <textarea v-model="trialInput" rows="8" placeholder="输入 JSON 参数..." class="mono" />
          </label>
          <label>
            输出
            <textarea :value="trialOutput" rows="8" readonly placeholder="运行结果..." class="mono" />
          </label>
          <button class="trial-run-btn" :disabled="trialRunning" @click="runTrial">
            <Play :size="14" />
            {{ trialRunning ? '运行中...' : '运行' }}
          </button>
        </template>

        <template v-else-if="selectedNode">
          <label>
            节点 ID
            <input :value="selectedNode.id" :disabled="readonly" @change="updateSelectedId(($event.target as HTMLInputElement).value)" />
          </label>
          <label>
            类型
            <input :value="NODE_TYPE_LABEL[selectedNode.type]" disabled />
          </label>
          <label v-if="selectedNode.type !== 'START' && selectedNode.type !== 'END' && selectedNode.type !== 'CONDITION'">
            输出变量
            <input :value="selectedNode.outputKey ?? ''" :disabled="readonly" @input="updateOutputKey(($event.target as HTMLInputElement).value)" />
          </label>

          <div class="field-section">
            <div class="field-section-header">
              <strong>输入字段</strong>
              <button v-if="!readonly" class="icon-button" title="添加" @click="addInputField"><Plus :size="13" /></button>
            </div>
            <div v-if="(selectedNode.inputFields ?? []).length === 0" class="field-empty">无输入字段</div>
            <div v-for="(field, idx) in selectedNode.inputFields ?? []" :key="idx" class="field-item">
              <div class="field-row">
                <input class="field-key" :value="field.key" placeholder="字段名" :disabled="readonly" @change="updateInputField(idx, { key: ($event.target as HTMLInputElement).value })" />
                <select class="field-type" :value="field.type ?? 'string'" :disabled="readonly" @change="updateInputField(idx, { type: ($event.target as HTMLSelectElement).value as any })">
                  <option value="string">文本</option>
                  <option value="number">数字</option>
                  <option value="boolean">布尔</option>
                  <option value="json">JSON</option>
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
                  <option v-for="varRef in getUpstreamVariables(selectedNode.id)" :key="varRef" :value="varRef">{{ varRef }}</option>
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
                <input class="field-key" :value="field.key" placeholder="字段名" :disabled="readonly" @change="updateOutputField(idx, { key: ($event.target as HTMLInputElement).value })" />
                <select class="field-type" :value="field.type ?? 'string'" :disabled="readonly" @change="updateOutputField(idx, { type: ($event.target as HTMLSelectElement).value as any })">
                  <option value="string">文本</option>
                  <option value="number">数字</option>
                  <option value="boolean">布尔</option>
                  <option value="json">JSON</option>
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
                <option value="json">JSON</option>
                <option value="array">数组</option>
              </select>
            </label>
            <div v-if="(selectedNode.outputFormat ?? []).length === 0" class="field-empty">默认文本输出</div>
            <div v-for="(field, idx) in selectedNode.outputFormat ?? []" :key="idx" class="field-item">
              <div v-if="['object', 'json'].includes(singleOutputFormatType(selectedNode))" class="field-row">
                <input class="field-key" :value="field.key" placeholder="属性名" :disabled="readonly" @change="updateOutputFormat(idx, { key: ($event.target as HTMLInputElement).value })" />
                <select class="field-type" :value="field.type || 'string'" :disabled="readonly" @change="updateOutputFormat(idx, { type: ($event.target as HTMLSelectElement).value })">
                  <option value="string">文本</option>
                  <option value="number">数字</option>
                  <option value="boolean">布尔</option>
                  <option value="object">对象</option>
                  <option value="json">JSON</option>
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
                  <input class="field-key" :value="item.key" placeholder="参数名" :disabled="readonly" @change="updateOutputFormatItem(idx, itemIdx, { key: ($event.target as HTMLInputElement).value })" />
                  <select class="field-type" :value="item.type || 'string'" :disabled="readonly" @change="updateOutputFormatItem(idx, itemIdx, { type: ($event.target as HTMLSelectElement).value })">
                    <option value="string">文本</option>
                    <option value="number">数字</option>
                    <option value="boolean">布尔</option>
                    <option value="object">对象</option>
                    <option value="json">JSON</option>
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
                  <input class="field-key" :value="item.key" placeholder="参数名" :disabled="readonly" @change="updateOutputFormatItem(idx, itemIdx, { key: ($event.target as HTMLInputElement).value })" />
                  <select class="field-type" :value="item.type || 'string'" :disabled="readonly" @change="updateOutputFormatItem(idx, itemIdx, { type: ($event.target as HTMLSelectElement).value })">
                    <option value="string">文本</option>
                    <option value="number">数字</option>
                    <option value="boolean">布尔</option>
                    <option value="object">对象</option>
                    <option value="json">JSON</option>
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

          <template v-if="supportsPrompt(selectedNode.type) && selectedNode.type !== 'APPROVAL' && selectedNode.type !== 'INTENT'">
            <label>
              模型
              <select :value="String(selectedNode.config.modelId ?? '')" :disabled="readonly" @change="updateConfig('modelId', ($event.target as HTMLSelectElement).value)">
                <option value="">未选择</option>
                <option v-for="m in models" :key="m.modelId" :value="m.modelId">{{ m.displayName }} · {{ m.provider }}</option>
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
                <option v-for="m in models" :key="m.modelId" :value="m.modelId">{{ m.displayName }} · {{ m.provider }}</option>
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
                <option v-for="m in models" :key="m.modelId" :value="m.modelId">{{ m.displayName }} · {{ m.provider }}</option>
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
              <div v-if="hasDataSource('leaveBalanceFields')" class="field-hint">输出：{{ selectedNode.outputKey ?? selectedNode.id }}.leaveBalanceFields</div>
              <div v-if="hasDataSource('departmentHeadcountFields')" class="field-hint">输出：{{ selectedNode.outputKey ?? selectedNode.id }}.departmentHeadcountFields</div>
            </div>
            <label>
              目标用户
              <input :value="String(selectedNode.config.targetUserId ?? '{{input.userId}}')" :disabled="readonly" @input="updateConfig('targetUserId', ($event.target as HTMLInputElement).value)" />
            </label>
            <label v-if="!selectedNode.config.dataSources">
              工具
              <select :value="String(selectedNode.config.toolCode ?? '')" :disabled="readonly" @change="updateConfig('toolCode', ($event.target as HTMLSelectElement).value)">
                <option value="">选择工具</option>
                <option v-for="t in tools" :key="t.toolId" :value="t.toolCode ?? ''">{{ t.toolCode }} · {{ t.toolName }}</option>
              </select>
            </label>
            <label v-if="!selectedNode.config.dataSources">
              参数 JSON
              <textarea v-model="argsText" :disabled="readonly" rows="5" @blur="applyArgs" />
            </label>
          </template>

          <label v-if="supportsToolCalls(selectedNode.type)">
            节点内工具调用 JSON
            <textarea v-model="toolCallsText" :disabled="readonly" rows="7" spellcheck="false" class="mono" @blur="applyToolCalls" />
          </label>

          <template v-if="selectedNode.type === 'CONDITION'">
            <label>
              判断字段
              <input :value="String(selectedNode.config.field ?? '')" :disabled="readonly" @input="updateConfig('field', ($event.target as HTMLInputElement).value)" />
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
                    <option v-for="n in selectableTargets" :key="n.id" :value="n.id">{{ NODE_TYPE_LABEL[n.type] }} · {{ n.id }}</option>
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
                <option v-for="n in selectableTargets" :key="n.id" :value="n.id">{{ NODE_TYPE_LABEL[n.type] }} · {{ n.id }}</option>
              </select>
            </label>
            <label>
              分支 JSON
              <textarea v-model="branchText" :disabled="readonly" rows="5" @blur="applyBranches" />
            </label>
          </template>

          <template v-if="selectedNode.type === 'END'">
            <label>
              返回格式
              <select :value="endReplyMode(selectedNode)" :disabled="readonly" @change="setEndReplyMode(($event.target as HTMLSelectElement).value as 'text' | 'json')">
                <option value="text">文本</option>
                <option value="json">JSON</option>
              </select>
            </label>
            <label v-if="endReplyMode(selectedNode) === 'text'">
              回复模板
              <textarea :value="String(selectedNode.config.reply ?? '')" :disabled="readonly" rows="4" @input="updateConfig('reply', ($event.target as HTMLTextAreaElement).value)" />
            </label>
            <label v-else>
              JSON 模板
              <textarea v-model="replyJsonText" :disabled="readonly" rows="8" spellcheck="false" class="mono" @blur="applyReplyJson" />
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
                    <input class="field-key" :value="String(action.type ?? 'OPEN_FLOW')" placeholder="类型" :disabled="readonly" @change="updateEndCardAction(idx, 'type', ($event.target as HTMLInputElement).value)" />
                    <input class="field-label" :value="String(action.label ?? '')" placeholder="按钮" :disabled="readonly" @change="updateEndCardAction(idx, 'label', ($event.target as HTMLInputElement).value)" />
                    <button v-if="!readonly" class="icon-button field-del" title="删除" @click="removeEndCardAction(idx)"><Trash2 :size="12" /></button>
                  </div>
                  <input class="field-ref" :value="String(action.target ?? '')" placeholder="目标地址" :disabled="readonly" @change="updateEndCardAction(idx, 'target', ($event.target as HTMLInputElement).value)" />
                </div>
              </div>
            </div>
          </template>

          <div v-if="selectedNodeEdges.length > 0" class="edge-section">
            <strong>连线</strong>
            <div v-for="edge in selectedNodeEdges" :key="`${edge.from}-${edge.fromPort ?? 0}-${edge.to}`" class="edge-item">
              <span>{{ edge.from }}<span v-if="edge.fromPort !== undefined">[{{ edge.fromPort }}]</span> → {{ edge.to }}</span>
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
            模型配置 (JSON)
            <textarea :value="modelConfigJson" :disabled="readonly" rows="4" spellcheck="false" class="mono" @input="emit('update:modelConfigJson', ($event.target as HTMLTextAreaElement).value)" />
          </label>
        </template>

        <details class="json-toggle">
          <summary>JSON 预览</summary>
          <pre>{{ prettyJson }}</pre>
        </details>
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
  background:
    linear-gradient(90deg, rgba(221, 227, 236, 0.64) 1px, transparent 1px),
    linear-gradient(rgba(221, 227, 236, 0.64) 1px, transparent 1px),
    var(--surface);
  background-size: 36px 36px;
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
  stroke: rgba(22, 119, 255, 0.58);
  stroke-width: 2.5;
  marker-end: url(#arrow);
  filter: drop-shadow(0 1px 1px rgba(15, 23, 42, 0.12));
}

.edge-group { pointer-events: stroke; }
.edge-group:hover .edge-path { stroke: rgba(22, 119, 255, 0.95); stroke-width: 3.5; }

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
.edge-temp { fill: none; stroke: rgba(22, 119, 255, 0.3); stroke-width: 2; stroke-dasharray: 6 4; }

.dag-node {
  position: absolute;
  z-index: 1;
  width: 210px;
  min-height: 86px;
  display: grid;
  gap: 6px;
  align-content: center;
  justify-items: start;
  padding: var(--space-md) var(--space-base);
  border: 1px solid var(--line);
  border-top: 3px solid var(--green);
  border-radius: var(--radius-lg);
  background: var(--panel);
  color: var(--ink);
  touch-action: none;
  user-select: none;
  cursor: move;
  box-shadow: var(--shadow-sm);
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.dag-node:hover {
  box-shadow: var(--shadow);
  transform: translateY(-1px);
}

.dag-node.selected {
  border-color: var(--green-light);
  box-shadow: 0 0 0 4px rgba(22, 119, 255, 0.14), var(--shadow);
}

.type-tool { border-top-color: var(--amber); }
.type-condition { border-top-color: var(--red); }
.type-intent { border-top-color: #7c5cbf; }
.type-approval { border-top-color: #2563eb; }
.type-knowledge { border-top-color: #0f766e; }
.type-form_extract { border-top-color: #0891b2; }
.type-summary { border-top-color: #9333ea; }
.type-transform { border-top-color: #4b5563; }
.type-end { border-top-color: var(--green-dark); }

.node-type {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 7px;
  border-radius: var(--radius-pill);
  background: rgba(22, 119, 255, 0.1);
  color: var(--green-dark);
  font-size: 10px;
  font-weight: 900;
  line-height: 1;
}

.type-tool .node-type { background: rgba(167, 101, 26, 0.12); color: #8a4d0f; }
.type-condition .node-type { background: rgba(179, 49, 49, 0.1); color: var(--red-dark); }
.type-intent .node-type { background: rgba(124, 92, 191, 0.12); color: #5b3ea3; }
.type-approval .node-type { background: rgba(37, 99, 235, 0.12); color: #1d4ed8; }
.type-knowledge .node-type { background: rgba(15, 118, 110, 0.12); color: #0f766e; }
.type-form_extract .node-type { background: rgba(8, 145, 178, 0.12); color: #0e7490; }
.type-summary .node-type { background: rgba(147, 51, 234, 0.12); color: #7e22ce; }
.type-transform .node-type { background: rgba(75, 85, 99, 0.12); color: #374151; }
.type-end .node-type { background: rgba(22, 119, 255, 0.12); color: var(--green-dark); }

.node-id {
  font-size: 14px;
  font-weight: 900;
  overflow-wrap: anywhere;
  line-height: 1.2;
  letter-spacing: 0;
}

.node-output { color: var(--muted); font-size: 11px; font-weight: 700; }

.node-fields { display: flex; flex-wrap: wrap; gap: 3px; }

.node-field {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: var(--radius-pill);
  font-weight: 700;
}

.node-field.in { background: rgba(22, 119, 255, 0.1); color: var(--green-dark); }
.node-field.out { background: rgba(167, 101, 26, 0.12); color: #8a4d0f; }

.port {
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid #ffffff;
  background: var(--green);
  z-index: 2;
  box-shadow: 0 0 0 1px rgba(22, 119, 255, 0.62), 0 2px 8px rgba(0, 80, 180, 0.25);
  transition: background 0.15s, transform 0.15s, box-shadow 0.15s;
}

.port-in { left: -7px; top: 43px; transform: translateY(-50%); background: #ffffff; }
.port-out { right: -7px; top: 43px; transform: translateY(-50%); cursor: crosshair; background: var(--green); }
.port-out:hover { transform: translateY(-50%) scale(1.3); }
.port-in.hovered {
  background: var(--green);
  transform: translateY(-50%) scale(1.4);
  box-shadow: 0 0 0 5px rgba(22, 119, 255, 0.16), 0 2px 8px rgba(0, 80, 180, 0.25);
}

.port-label {
  position: absolute;
  right: -56px;
  font-size: 9px;
  color: var(--muted);
  white-space: nowrap;
  pointer-events: none;
  z-index: 1;
  transform: translateY(-50%);
  font-weight: 700;
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
  border-left: 2px solid rgba(22, 119, 255, 0.18);
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
  border: 2px solid #ffffff;
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

.dag-node:hover .node-trial-btn { opacity: 1; }
.node-trial-btn,
.node-trial-btn:hover {
  color: var(--panel-strong);
}

.node-trial-btn:hover { background: var(--green-dark); transform: scale(1.08); }

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
.workflow-result-reply { font-size: 12px; }
.workflow-result-reply strong { display: block; margin-bottom: 4px; color: var(--muted); font-size: 11px; }
.workflow-result-reply pre {
  margin: 0;
  padding: var(--space-sm);
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  white-space: pre-wrap;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
}
.workflow-result-steps { font-size: 12px; }
.workflow-result-steps strong { display: block; margin-bottom: 4px; color: var(--muted); font-size: 11px; }
.workflow-step { display: flex; align-items: center; gap: var(--space-xs); padding: 6px 7px; border-bottom: 1px solid var(--line); font-size: 11px; }
.workflow-step:last-child { border-bottom: none; }
.workflow-step-idx { width: 20px; height: 20px; display: grid; place-items: center; border-radius: 50%; background: var(--pill-bg); font-size: 10px; font-weight: 700; flex-shrink: 0; }
.workflow-step-id { font-weight: 700; }
.workflow-step-type { color: var(--muted); font-size: 10px; }
.workflow-step-status { font-size: 10px; font-weight: 700; }
.workflow-step-ms { color: var(--muted); font-size: 10px; margin-left: auto; }
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
