export const DOCUMENT_MIMES = { 'application/pdf': ['pdf'], 'image/png': ['png'], 'image/jpeg': ['jpg', 'jpeg'], 'text/plain': ['txt'] };
export function documentPath(intakeId, documentId) { return `/api/intakes/${intakeId}/documents/${documentId}/download`; }
export function documentMime(value) { return typeof value === 'string' ? value.split(';')[0].trim().toLowerCase() : ''; }
export function validDocumentName(filename, contentType) {
  return typeof filename === 'string' && filename.trim().length > 0 && filename.length <= 255
    && !/[\x00-\x1f\x7f/\\]/.test(filename)
    && !!DOCUMENT_MIMES[documentMime(contentType)]?.includes(filename.split('.').at(-1).toLowerCase());
}
export function safeDownloadName(filename) {
  const name = filename.replace(/[<>:"/\\|?*\x00-\x1f\x7f]/g, '_').replace(/[. ]+$/, '').trim();
  return /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(name) ? `file-${name}` : name;
}
export function fileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
}
export function documentTypeLabel(contentType) { return { 'application/pdf': 'PDF', 'image/png': 'PNG', 'image/jpeg': 'JPEG', 'text/plain': 'TXT' }[documentMime(contentType)] || '파일'; }
export function saveDocument(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url; link.download = safeDownloadName(filename);
  document.body.append(link); link.click(); link.remove();
  // Let the browser acquire the blob before releasing its object URL.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
