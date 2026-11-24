import { computed, onBeforeUnmount, onMounted, type Ref } from 'vue';
import { useLocalDraft } from './localData';
export function useViewState(key: string, fields: Record<string, Ref>) {
  const draft = useLocalDraft(key, computed({
    get: () => Object.fromEntries(Object.entries(fields).map(([name, value]) => [name, value.value])),
    set: (value) => { for (const [name, field] of Object.entries(fields)) if (name in value && typeof field.value === typeof value[name]) field.value = value[name]; },
  }));
  onMounted(() => { void draft.restore(); });
  onBeforeUnmount(draft.stop);
}
