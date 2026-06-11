import type { useSessionStore } from '@/stores/session';
import type { useShellStore } from '@/stores/shell';

type StoreShell = ReturnType<typeof useShellStore>;
type StoreSession = ReturnType<typeof useSessionStore>;

export function syncShellSessionProfile(shell: StoreShell, session: StoreSession) {
  shell.setColleagues(session.colleagues);
  shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
}
