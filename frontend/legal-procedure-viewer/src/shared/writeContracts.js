export const validVersion = value => Number.isSafeInteger(value) && value >= 0;
export const validRequestKey = value => typeof value === 'string' && /^[A-Za-z0-9._:-]{8,100}$/.test(value);

// A logical write keeps its key in memory until confirmed, discarded or replaced.
// Versions are intentionally not part of a memo's logical payload.
export function prepareWriteAttempt(previous, scope, payload) {
  const signature = JSON.stringify(payload);
  if (previous?.scope === scope && previous.signature === signature) return previous;
  if (previous?.scope === scope && previous.uncertain) {
    const error = new Error('이전 저장 여부를 확인하지 못했습니다. 기존 문의를 확인한 뒤 새 접수를 진행해 주세요.');
    error.code = 'UNCERTAIN_WRITE_CHANGED';
    throw error;
  }
  return { scope, signature, key: crypto.randomUUID(), uncertain: false };
}

const statuses = new Set(['SUBMITTED', 'REVIEWED', 'NEED_MORE_INFO', 'CONSULTATION_READY', 'CLOSED', 'OPEN', 'ACKNOWLEDGED', 'RESOLVED']);
const positiveId = value => Number.isSafeInteger(value) && value > 0;
const conflictMessages = {
  INTAKE_VERSION_CONFLICT: '다른 담당자가 문의를 수정했습니다. 최신 내용을 확인한 뒤 다시 저장해 주세요.',
  CONCURRENT_MODIFICATION: '다른 요청이 먼저 저장됐습니다. 최신 내용을 확인한 뒤 다시 저장해 주세요.',
  IDEMPOTENCY_KEY_REUSED: '이 저장 요청 번호가 다른 내용에 사용됐습니다. 기존 저장 내용을 확인해 주세요.',
  INTAKE_STATUS_TRANSITION_NOT_ALLOWED: '현재 문의 상태에서는 선택한 상태로 변경할 수 없습니다.',
  INCIDENT_ALREADY_RESOLVED: '이미 해결된 건입니다. 최신 상태를 확인해 주세요.',
  INCIDENT_STATUS_TRANSITION_NOT_ALLOWED: '현재 Incident 상태에서는 이 작업을 처리할 수 없습니다.',
};

export function serverProblem(data, status) {
  const code = typeof data?.code === 'string' && /^[A-Z][A-Z0-9_]{0,79}$/.test(data.code) ? data.code : '';
  const details = {};
  if (data?.details && typeof data.details === 'object' && !Array.isArray(data.details)) {
    for (const name of ['intakeId', 'incidentId']) if (positiveId(data.details[name])) details[name] = data.details[name];
    for (const name of ['currentVersion', 'expectedVersion']) if (validVersion(data.details[name])) details[name] = data.details[name];
    for (const name of ['currentStatus', 'requestedStatus']) if (statuses.has(data.details[name])) details[name] = data.details[name];
  }
  return { code, details, message: status === 409 ? conflictMessages[code] : undefined };
}

export function writeOptions({ idempotencyKey, expectedVersion } = {}) {
  if (idempotencyKey !== undefined && !validRequestKey(idempotencyKey)) throw new Error('저장 요청 번호를 확인해 주세요.');
  if (expectedVersion !== undefined && !validVersion(expectedVersion)) throw new Error('문의 버전 정보를 확인한 뒤 다시 저장해 주세요.');
  return { idempotencyKey, expectedVersion };
}
