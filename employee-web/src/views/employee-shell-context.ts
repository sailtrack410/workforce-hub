import { inject, provide } from 'vue';
import type { ComputedRef, InjectionKey, Ref } from 'vue';
import type { useImStore } from '../stores/im';
import type { useSessionStore } from '../stores/session';
import type { AgentResponse } from '../api';
import type {
  KnowledgeAnswerAction,
  KnowledgeCitation,
  KnowledgeRelatedFlow,
} from '@/types/knowledge';
import type {
  ContactRecord,
  FileSpaceRecord,
  PersonalFileRecord,
  SectionAction,
  SectionMeta,
  TodoRecord,
  useShellStore,
} from '../stores/shell';
import type { useWorkflowStore } from '../stores/workflow';

type StoreShell = ReturnType<typeof useShellStore>;
type StoreIm = ReturnType<typeof useImStore>;
type StoreSession = ReturnType<typeof useSessionStore>;
type StoreWorkflow = ReturnType<typeof useWorkflowStore>;
type ReadonlyRef<T> = ComputedRef<T> | Readonly<Ref<T>>;
type RunSectionAction = (action?: SectionAction) => void;

export interface MessagesSectionContext {
  shell: StoreShell;
  im: StoreIm;
  session: StoreSession;
  wsConnected: ReadonlyRef<boolean>;
  showToast: (message: string) => void;
  runAction: RunSectionAction;
  openDetail: (payload?: KnowledgeAnswerAction | KnowledgeCitation | KnowledgeRelatedFlow) => void;
  openUploadPanel: () => void;
  syncConversationMessages: (id: string) => Promise<void>;
  navigateMessagesConversation: (id: string) => void;
  startAgentConversationFromSearch: (agent: AgentResponse) => Promise<void>;
  openFilePreviewFromUrl: (file: PersonalFileRecord, url: string) => Promise<void>;
}

export interface TodosSectionContext {
  workflow: StoreWorkflow;
  activeTodo: ReadonlyRef<TodoRecord>;
  pendingTodoCount: ReadonlyRef<number>;
  mustHandleTodos: ReadonlyRef<TodoRecord[]>;
  approvalTodos: ReadonlyRef<TodoRecord[]>;
  deferredTodos: ReadonlyRef<TodoRecord[]>;
  hasActiveTodo: ReadonlyRef<boolean>;
  backToList: () => void;
  runAction: RunSectionAction;
  retryTodos: () => Promise<void>;
}

export interface WorkbenchSectionContext {
  shell: StoreShell;
  session: StoreSession;
  showToast: (message: string) => void;
}

export interface FilesSectionContext {
  im: StoreIm;
  fileUploading: Ref<boolean>;
  activeFile: ReadonlyRef<FileSpaceRecord>;
  fileUsageCircumference: number;
  activeFileUsage: ReadonlyRef<number>;
  activeFileUsageOffset: ReadonlyRef<number>;
  currentMeta: ReadonlyRef<SectionMeta>;
  backToList: () => void;
  runAction: RunSectionAction;
  sendSelectedFileToConversation: (file: PersonalFileRecord) => Promise<void>;
  openPersonalFilePreview: (file: PersonalFileRecord) => Promise<void>;
  downloadPersonalFile: (file: PersonalFileRecord) => Promise<void>;
  trashPersonalFile: (file: PersonalFileRecord) => Promise<void>;
  restorePersonalFile: (file: PersonalFileRecord) => Promise<void>;
  purgePersonalFile: (file: PersonalFileRecord) => Promise<void>;
}

export interface SettingsSectionContext {
  shell: StoreShell;
  session: StoreSession;
  showToast: (message: string) => void;
}

export interface ContactsSectionContext {
  shell: StoreShell;
  activeContact: ReadonlyRef<ContactRecord | null>;
  backToList: () => void;
}

export interface EmptySectionContext {
  currentMeta: ReadonlyRef<SectionMeta>;
  businessEmptyTitle: ReadonlyRef<string>;
  backToList: () => void;
}

export interface EmployeeShellContexts {
  messages: MessagesSectionContext;
  todos: TodosSectionContext;
  workbench: WorkbenchSectionContext;
  files: FilesSectionContext;
  settings: SettingsSectionContext;
  contacts: ContactsSectionContext;
  empty: EmptySectionContext;
}

const messagesSectionContextKey: InjectionKey<MessagesSectionContext> = Symbol('messages-section-context');
const todosSectionContextKey: InjectionKey<TodosSectionContext> = Symbol('todos-section-context');
const workbenchSectionContextKey: InjectionKey<WorkbenchSectionContext> = Symbol('workbench-section-context');
const filesSectionContextKey: InjectionKey<FilesSectionContext> = Symbol('files-section-context');
const settingsSectionContextKey: InjectionKey<SettingsSectionContext> = Symbol('settings-section-context');
const contactsSectionContextKey: InjectionKey<ContactsSectionContext> = Symbol('contacts-section-context');
const emptySectionContextKey: InjectionKey<EmptySectionContext> = Symbol('empty-section-context');

function injectStrict<T>(key: InjectionKey<T>, name: string) {
  const context = inject(key);
  if (!context) {
    throw new Error(`${name} is not available`);
  }
  return context;
}

export function provideMessagesSectionContext(context: MessagesSectionContext) {
  provide(messagesSectionContextKey, context);
}

export function provideTodosSectionContext(context: TodosSectionContext) {
  provide(todosSectionContextKey, context);
}

export function provideWorkbenchSectionContext(context: WorkbenchSectionContext) {
  provide(workbenchSectionContextKey, context);
}

export function provideFilesSectionContext(context: FilesSectionContext) {
  provide(filesSectionContextKey, context);
}

export function provideSettingsSectionContext(context: SettingsSectionContext) {
  provide(settingsSectionContextKey, context);
}

export function provideContactsSectionContext(context: ContactsSectionContext) {
  provide(contactsSectionContextKey, context);
}

export function provideEmptySectionContext(context: EmptySectionContext) {
  provide(emptySectionContextKey, context);
}

export function provideEmployeeShellContexts(contexts: EmployeeShellContexts) {
  provideMessagesSectionContext(contexts.messages);
  provideTodosSectionContext(contexts.todos);
  provideWorkbenchSectionContext(contexts.workbench);
  provideFilesSectionContext(contexts.files);
  provideSettingsSectionContext(contexts.settings);
  provideContactsSectionContext(contexts.contacts);
  provideEmptySectionContext(contexts.empty);
}

export function useMessagesSectionContext() {
  return injectStrict(messagesSectionContextKey, 'Messages section context');
}

export function useTodosSectionContext() {
  return injectStrict(todosSectionContextKey, 'Todos section context');
}

export function useWorkbenchSectionContext() {
  return injectStrict(workbenchSectionContextKey, 'Workbench section context');
}

export function useFilesSectionContext() {
  return injectStrict(filesSectionContextKey, 'Files section context');
}

export function useSettingsSectionContext() {
  return injectStrict(settingsSectionContextKey, 'Settings section context');
}

export function useContactsSectionContext() {
  return injectStrict(contactsSectionContextKey, 'Contacts section context');
}

export function useEmptySectionContext() {
  return injectStrict(emptySectionContextKey, 'Empty section context');
}
