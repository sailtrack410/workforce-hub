export async function uploadToTicketTarget(uploadUrl: string, file: File, uploadHeaders: Record<string, string>, onProgress: (percent: number) => void) {
  const remote = /^https?:\/\//i.test(uploadUrl);
  const requestUrl = remote ? uploadUrl : '/__admin-local-upload';
  const safeUploadHeaders = stripHostCsrfHeader(uploadHeaders);
  const headers = remote
    ? safeUploadHeaders
    : {
      'Content-Type': safeUploadHeaders['Content-Type'] ?? (file.type || 'application/octet-stream'),
      'X-Upload-Path': encodeURIComponent(uploadUrl),
    };
  const crossOrigin = remote && new URL(uploadUrl).origin !== window.location.origin;
  if (!crossOrigin) {
    const response = await fetch(requestUrl, {
      method: 'PUT',
      body: file,
      headers,
      credentials: 'omit',
    });
    onProgress(100);
    return response;
  }
  return new Promise<Response>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', requestUrl);
    xhr.withCredentials = false;
    Object.entries(headers).forEach(([key, value]) => {
      if (value) xhr.setRequestHeader(key, value);
    });
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () => resolve(new Response(xhr.responseText, { status: xhr.status, statusText: xhr.statusText }));
    xhr.onerror = () => reject(new Error('文件上传失败'));
    xhr.onabort = () => reject(new Error('文件上传已取消'));
    xhr.send(file);
  });
}

function stripHostCsrfHeader(headers: Record<string, string>) {
  return Object.fromEntries(
    Object.entries(headers).filter(([key]) => key.toLowerCase() !== 'x-xsrf-token'),
  );
}
