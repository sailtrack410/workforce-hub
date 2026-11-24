export async function downloadFile(url: string) {
  if (window.workforceDesktop) await window.workforceDesktop.download(url);
  else window.open(url, '_blank', 'noopener');
}
