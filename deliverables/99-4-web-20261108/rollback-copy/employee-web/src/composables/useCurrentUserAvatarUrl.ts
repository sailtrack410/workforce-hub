import { employeeApi } from '@/api';
import type { useSessionStore } from '@/stores/session';
import type { useShellStore } from '@/stores/shell';
import { usePersonalFileTransfer } from './usePersonalFileTransfer';

type StoreShell = ReturnType<typeof useShellStore>;
type StoreSession = ReturnType<typeof useSessionStore>;

export function useCurrentUserAvatarUrl(shell: StoreShell, session: StoreSession) {
  const { resolveDownloadUrl } = usePersonalFileTransfer();

  async function refreshCurrentUserAvatarUrl() {
    const refId = session.profile?.avatarAttachmentRefId;
    if (!refId) {
      shell.setAvatarUrl('');
      return;
    }
    try {
      const ticket = await employeeApi.files.createDownloadTicket(refId, { downloadType: 'INLINE' });
      shell.setAvatarUrl(resolveDownloadUrl(ticket, employeeApi.files.downloadUrl(ticket.ticketNo)));
    } catch {
      shell.setAvatarUrl('');
    }
  }

  return {
    refreshCurrentUserAvatarUrl,
  };
}
