export type KnowledgeAnswerState =
  | 'ANSWERED'
  | 'NO_MATCH'
  | 'LOW_CONFIDENCE'
  | 'NO_PERMISSION'
  | 'INGESTING'
  | 'SEARCH_FAILED';

export type AgentResultCategory = 'QA' | 'OA' | 'KNOWLEDGE';

export type AgentResultStatus = 'LOADING' | 'SUCCESS' | 'EMPTY' | 'NO_PERMISSION' | 'FAILED';

export type KnowledgeApiRefStatus = 'available' | 'planned' | 'unavailable';

export interface KnowledgeApiRef {
  status: KnowledgeApiRefStatus;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  path: string;
  description?: string;
}

export interface KnowledgeCitation {
  id?: string;
  title: string;
  snippet?: string;
  sourceType?: 'DOC' | 'FILE' | 'WORKFLOW' | 'POLICY' | 'UNKNOWN';
  score?: number;
  apiRef?: KnowledgeApiRef;
}

export interface KnowledgeRelatedFlow {
  id: string;
  title: string;
  status: string;
  statusTone?: 'primary' | 'success' | 'warning' | 'danger' | 'neutral';
  summary?: string;
  fields?: Array<[string, string]>;
  apiRef?: KnowledgeApiRef;
}

export interface AgentResultFlow {
  requestId?: string;
  requestNo?: string;
  title?: string;
  status?: string;
  currentNode?: string;
  detailUrl?: string;
}

export interface AgentResultRecord {
  title: string;
  summary?: string;
  fields?: Array<[string, string]>;
}

export interface AgentResultData {
  fields?: KnowledgeAnswerRelatedRef[] | null;
  flow?: AgentResultFlow | null;
  records?: AgentResultRecord[] | null;
}

export interface KnowledgeAnswerAction {
  label: string;
  action: string;
  apiRef?: KnowledgeApiRef;
  payload?: Record<string, unknown>;
  disabled?: boolean;
}

export interface KnowledgeAnswerRelatedRef {
  label: string;
  value: string;
  tone?: 'blue' | 'green' | 'amber' | 'red' | 'gray';
}

export interface KnowledgeAnswerCard {
  state: KnowledgeAnswerState;
  category?: AgentResultCategory;
  status?: AgentResultStatus;
  title: string;
  answer: string;
  confidence?: 'high' | 'medium' | 'low';
  citations?: KnowledgeCitation[];
  relatedFlows?: KnowledgeRelatedFlow[];
  missingReason?: string;
  suggestedActions?: KnowledgeAnswerAction[];
  relatedBusiness?: KnowledgeAnswerRelatedRef[];
  generatedAt?: string;
}
