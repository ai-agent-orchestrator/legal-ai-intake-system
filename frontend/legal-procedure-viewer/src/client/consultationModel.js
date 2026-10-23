import { INTAKE_STATUSES } from './model.js';
export const FOLLOW_UP_ITEMS = [
  { value: 'REQUEST_DOCUMENTS', label: '자료 보완 요청' },
  { value: 'SCHEDULE_CONSULTATION', label: '상담 일정 확인 필요' },
  { value: 'DOCUMENT_REVIEW', label: '서면 검토 필요' },
  { value: 'E_LITIGATION_GUIDE', label: '전자소송 안내 필요' },
];
export const isIntakeStatus = value => Object.hasOwn(INTAKE_STATUSES, value);
// Mirrors IntakeStatus.canChangeTo: same value is allowed; CLOSED cannot reopen.
export function allowedStatuses(current) {
  if (!isIntakeStatus(current)) return [];
  return Object.keys(INTAKE_STATUSES).filter(next => next === current || (current !== 'CLOSED' && next !== 'SUBMITTED'));
}
export function consultationMemoPayload(content, followUpItems) {
  const fieldErrors = {};
  if (typeof content !== 'string' || !content.trim()) fieldErrors.content = '상담 준비 메모를 입력해 주세요.';
  else if (content.length > 4000) fieldErrors.content = '메모는 4,000자 이내로 입력해 주세요.';
  if (!Array.isArray(followUpItems) || followUpItems.length > 4 || followUpItems.some(value => !FOLLOW_UP_ITEMS.some(item => item.value === value))) fieldErrors.followUpItems = '후속 업무 항목을 다시 선택해 주세요.';
  if (Object.keys(fieldErrors).length) {
    const error = new Error(Object.values(fieldErrors)[0]); error.fieldErrors = fieldErrors; throw error;
  }
  return { content: content.trim(), followUpItems: [...new Set(followUpItems)] };
}
export function recentMemos(memos) {
  return [...memos].sort((a, b) => b.createdAt.localeCompare(a.createdAt)
    || String(b.id).length - String(a.id).length || String(b.id).localeCompare(String(a.id))).slice(0, 5);
}
export function consultationTime(value) {
  return typeof value === 'string' ? value.slice(0, 16).replaceAll('-', '. ').replace('T', '. ') : '제공되지 않음';
}
