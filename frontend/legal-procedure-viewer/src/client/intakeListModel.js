import { INTAKE_STATUSES } from './model.js';

export const LIST_STATUSES = { ...INTAKE_STATUSES, SUBMITTED: '접수됨', REVIEWED: '검토됨', NEED_MORE_INFO: '보완 필요', CONSULTATION_READY: '상담 준비' };
export const EMPTY_LIST_FILTERS = { caseType: '', status: '', desiredNextStep: '', startDate: '', endDate: '', query: '' };

export function validDay(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '') || value < '1000-01-01') return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}
// The server sends LocalDateTime, without a time zone. Keep its calendar date as supplied.
export function validIntakeTime(value) {
  return typeof value === 'string' && validDay(value.slice(0, 10))
    && /^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?$/.test(value);
}
export function listFilterError(filters) {
  if ((filters.startDate && !validDay(filters.startDate)) || (filters.endDate && !validDay(filters.endDate))) return '조회 기간을 올바른 날짜로 입력해 주세요.';
  if (filters.startDate && filters.endDate && filters.startDate > filters.endDate) return '시작일은 종료일보다 늦을 수 없습니다.';
  if (filters.query.length > 100) return '검색어는 100자 이내로 입력해 주세요.';
  return '';
}
export function filterIntakes(items, filters, descending = true) {
  if (listFilterError(filters)) return [];
  const query = filters.query.trim().toLocaleLowerCase('ko');
  return items.filter(item => (!filters.caseType || item.caseType === filters.caseType)
    && (!filters.status || item.status === filters.status)
    && (!filters.desiredNextStep || item.desiredNextStep === filters.desiredNextStep)
    && (!filters.startDate || item.createdAt.slice(0, 10) >= filters.startDate)
    && (!filters.endDate || item.createdAt.slice(0, 10) <= filters.endDate)
    && (!query || [String(item.id), `No. ${item.id}`, item.clientNameMasked, item.counterpartMasked, item.coreStatementMasked,
      item.caseTypeLabel, item.disputeArea, item.inquiryPurpose, item.desiredNextStep].some(value => value?.toLocaleLowerCase('ko').includes(query))))
    .sort((a, b) => {
      const time = a.createdAt.localeCompare(b.createdAt);
      const id = String(a.id).length - String(b.id).length || String(a.id).localeCompare(String(b.id));
      return (time || id) * (descending ? -1 : 1);
    });
}
export function listCounts(items) {
  return { total: items.length, pending: items.filter(item => item.status === 'SUBMITTED').length,
    needsInfo: items.filter(item => item.status === 'NEED_MORE_INFO').length,
    ready: items.filter(item => item.status === 'CONSULTATION_READY').length };
}
export function declaredDocumentCount(item) {
  return new Set(item.selectedDocuments.filter(value => value.trim() && !['없음', '선택 자료 없음'].includes(value.trim()))).size;
}
export function intakeDateParts(value) {
  return { date: value.slice(0, 10).replaceAll('-', '. ') + '.', time: value.slice(11, 16) };
}
