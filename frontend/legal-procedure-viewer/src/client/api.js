import { CASE_TYPES, classificationPayload, minimalPayload } from './model.js';
import { validIntakeTime } from './intakeListModel.js';
import { consultationMemoPayload, FOLLOW_UP_ITEMS, isIntakeStatus } from './consultationModel.js';
import { documentMime, documentPath, validDocumentName } from './intakeDetailModel.js';
import { searchResultPayload, SEARCH_STAGES } from './searchModel.js';
import { AUTH_ROLES, loginCredentials } from './authModel.js';
import { serverProblem, validVersion, writeOptions } from '../shared/writeContracts.js';

export class ApiError extends Error {
  constructor(message, status = 0, fieldErrors = {}, traceId = '', code = '', details = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.traceId = traceId;
    this.code = code;
    this.details = details;
  }
}

export function normalizeBase(value = '') {
  if (!value.trim()) return '';
  const url = new URL(value.trim());
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error('API 서버 주소는 사용자 정보·쿼리가 없는 HTTP(S) 주소여야 합니다.');
  }
  return url.href.replace(/\/+$/, '');
}

const isObject = value => value && typeof value === 'object' && !Array.isArray(value);
const isStrings = value => Array.isArray(value) && value.every(item => typeof item === 'string');
const isProcedure = value => isObject(value) && ['code', 'domain', 'title', 'summary', 'userRole'].every(key => typeof value[key] === 'string') && value.code.length > 0;
const isStep = value => isObject(value) && Number.isInteger(value.stepOrder) && value.stepOrder > 0
  && ['stepCode', 'title', 'description'].every(key => typeof value[key] === 'string') && isStrings(value.checklist);
const isForm = value => isObject(value) && ['procedureCode', 'formCode', 'title', 'fileType', 'usageNote'].every(key => typeof value[key] === 'string') && !!value.formCode && !!value.procedureCode;
const requireCode = (code, message) => { if (typeof code !== 'string' || !code.trim()) throw new Error(message); return encodeURIComponent(code); };
const isId = value => typeof value === 'number'
  ? Number.isSafeInteger(value) && value > 0
  : typeof value === 'string' && /^[1-9]\d*$/.test(value);
const isIntake = value => isObject(value) && isId(value.id)
  && (value.version === undefined || validVersion(value.version))
  && ['caseType', 'caseTypeLabel', 'inquiryPurpose', 'partyPosition', 'coreStatementMasked', 'desiredNextStep', 'clientGuideMessage', 'privacyNotice', 'status'].every(key => typeof value[key] === 'string')
  && (value.permissions === undefined || (isObject(value.permissions) && typeof value.permissions.canManage === 'boolean' && typeof value.permissions.canAssign === 'boolean'))
  && CASE_TYPES.some(type => type.value === value.caseType)
  && ['sessionId', 'incidentDate', 'counterpartMasked', 'amountOrDamage', 'currentStage', 'clientNameMasked', 'clientPhoneMasked', 'preferredRegion', 'disputeArea'].every(key => value[key] == null || typeof value[key] === 'string')
  && isStrings(value.selectedDocuments) && isStrings(value.recommendedDocuments)
  && (value.lawyerSearchCondition == null || (isObject(value.lawyerSearchCondition) && isId(value.lawyerSearchCondition.id)
    && String(value.lawyerSearchCondition.intakeId) === String(value.id)
    && ['legalField', 'disputeType', 'preferredRegion', 'officeType', 'experienceLevel', 'publicCareerPreference', 'consultationStyle', 'sourcePolicy', 'searchQuery'].every(key => typeof value.lawyerSearchCondition[key] === 'string')));
const isSearch = value => isObject(value) && ['id', 'intakeId', 'conditionId'].every(key => isId(value[key]))
  && ['searchPurpose', 'sourcePolicy', 'status', 'searchQuery'].every(key => typeof value[key] === 'string') && validIntakeTime(value.createdAt)
  && Array.isArray(value.results) && value.results.every(item => isObject(item) && isId(item.id) && String(item.searchRequestId) === String(value.id)
    && ['sourceType', 'title', 'url'].every(key => typeof item[key] === 'string') && (item.snippet == null || typeof item.snippet === 'string')
    && (item.explanation == null || (typeof item.explanation === 'string' && item.explanation.length <= 2000))
    && Number.isInteger(item.displayOrder) && item.displayOrder >= -2147483648 && item.displayOrder <= 2147483647 && validIntakeTime(item.createdAt))
  && new Set(value.results.map(item => String(item.id))).size === value.results.length
  && (value.execution == null ? !['QUEUED', 'RUNNING'].includes(value.status) : (isObject(value.execution)
    && typeof value.execution.id === 'string' && /^[a-f0-9-]{36}$/i.test(value.execution.id)
    && Object.hasOwn(SEARCH_STAGES, value.execution.stage) && value.execution.provider === 'NAVER_WEB'
    && validIntakeTime(value.execution.startedAt) && (value.execution.finishedAt == null || validIntakeTime(value.execution.finishedAt))
    && (value.execution.sourceWarningCode == null || ['KBA_SEARCH_UNAVAILABLE', 'GENERAL_SEARCH_UNAVAILABLE'].includes(value.execution.sourceWarningCode))
    && (value.execution.errorCode == null || (typeof value.execution.errorCode === 'string' && /^[A-Z_]+$/.test(value.execution.errorCode)))));
const isMemo = (value, intakeId) => isObject(value) && isId(value.id) && String(value.intakeId) === String(intakeId)
  && (value.intakeVersion == null || validVersion(value.intakeVersion))
  && typeof value.contentMasked === 'string' && validIntakeTime(value.createdAt)
  && Array.isArray(value.followUpItems) && value.followUpItems.length <= 4
  && value.followUpItems.every(code => FOLLOW_UP_ITEMS.some(item => item.value === code))
  && new Set(value.followUpItems).size === value.followUpItems.length;
const isConsultationIntake = (data, id) => isIntake(data) && String(data.id) === String(id) && validIntakeTime(data.createdAt)
  && isIntakeStatus(data.status) && Array.isArray(data.consultationMemos)
  && data.consultationMemos.every(memo => isMemo(memo, id))
  && new Set(data.consultationMemos.map(memo => String(memo.id))).size === data.consultationMemos.length;
const isUploadedDocument = (value, intakeId) => isObject(value) && isId(value.id) && String(value.intakeId) === String(intakeId)
  && typeof value.documentType === 'string' && validDocumentName(value.originalFilename, value.contentType)
  && Number.isSafeInteger(value.sizeBytes) && value.sizeBytes > 0 && validIntakeTime(value.createdAt)
  && value.downloadUrl === documentPath(intakeId, value.id);

export function createIntakeApi({ base = '', getToken = () => '', onUnauthorized = () => {}, timeoutMs = 15000 } = {}) {
  const apiBase = normalizeBase(base);
  async function request(path, { method = 'GET', body, signal, authenticated = false, accessToken, binary = false, validate = isObject, idempotencyKey } = {}) {
    const controller = new AbortController();
    const abort = () => controller.abort();
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) controller.abort();
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);
    const token = authenticated ? (accessToken ?? getToken()) : '';
    try {
      const response = await fetch(`${apiBase}${path}`, {
        method, signal: controller.signal, cache: 'no-store', credentials: 'omit',
        headers: { Accept: binary ? '*/*' : 'application/json', ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}) },
        ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      });
      const data = await (binary && response.ok ? response.blob() : response.json()).catch(error => {
        if (error.name === 'AbortError') throw error;
        return null;
      });
      controller.signal.throwIfAborted();
      if (!response.ok) {
        if (response.status === 401 && token && getToken() === token) onUnauthorized();
        const messages = {
          400: '입력 내용을 확인해 주세요.', 401: '로그인 정보를 확인하거나 다시 로그인해 주세요.',
          403: '이 요청에 대한 접근 권한이 없습니다.', 404: '요청한 정보를 찾을 수 없습니다.',
          409: '요청 상태가 변경되었습니다. 다시 확인해 주세요.', 429: '요청이 많습니다. 잠시 후 다시 시도해 주세요.',
        };
        const problem = serverProblem(data, response.status);
        throw new ApiError(problem.message || messages[response.status] || '서버에서 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.', response.status,
          isObject(data?.fieldErrors) ? Object.fromEntries(Object.entries(data.fieldErrors).filter(([, value]) => typeof value === 'string')) : {},
          response.headers.get('X-Trace-Id') || (typeof data?.traceId === 'string' ? data.traceId : ''), problem.code, problem.details);
      }
      if (!validate(data)) throw new ApiError('서버 응답 형식을 확인할 수 없습니다. 다시 시도해 주세요.');
      if (idempotencyKey) {
        const replayed = response.headers.get('Idempotent-Replayed');
        if (replayed !== null && !['true', 'false'].includes(replayed)) throw new ApiError('서버의 재전송 확인 응답 형식을 확인할 수 없습니다.');
        if (replayed !== null) return { ...data, replayed: replayed === 'true' };
      }
      return data;
    } catch (error) {
      if (timedOut) throw new ApiError('응답 대기 시간(15초)을 넘었습니다. 다시 시도해 주세요.');
      if (error.name === 'AbortError') throw error;
      if (error instanceof TypeError) throw new ApiError('서버에 연결하지 못했습니다. 연결 상태를 확인하고 다시 시도해 주세요.');
      throw error;
    } finally {
      clearTimeout(timer);
      signal?.removeEventListener('abort', abort);
    }
  }
  return {
    classify: (draft, signal) => request('/api/intakes/classify-preview', {
      method: 'POST', body: classificationPayload(draft), signal,
      validate: data => isObject(data) && ['caseType', 'caseTypeLabel', 'disputeArea', 'clientGuideMessage', 'classificationMethod'].every(key => typeof data[key] === 'string') && CASE_TYPES.some(type => type.value === data.caseType) && isStrings(data.recommendedDocuments) && data.persisted === false,
    }),
    procedures: (domain, signal) => request(`/api/legal/procedures${domain && domain !== 'UNKNOWN' ? `?domain=${encodeURIComponent(domain)}` : ''}`, {
      signal, validate: data => Array.isArray(data) && data.every(item => isProcedure(item) && (!domain || domain === 'UNKNOWN' || item.domain === domain)),
    }),
    procedure: (code, signal, expectedDomain) => {
      if (typeof code !== 'string' || !code.trim()) throw new Error('목록에서 절차를 선택해 주세요.');
      return request(`/api/legal/procedures/${encodeURIComponent(code)}`, { signal, validate: data => isProcedure(data) && data.code === code && (!expectedDomain || data.domain === expectedDomain) && Array.isArray(data.steps) && data.steps.every(isStep) && Array.isArray(data.forms) && data.forms.every(form => isForm(form) && form.procedureCode === code) });
    },
    procedureSteps: (code, signal) => request(`/api/legal/procedures/${requireCode(code, '목록에서 절차를 선택해 주세요.')}/steps`, {
      signal, validate: data => Array.isArray(data) && data.every(isStep) && new Set(data.map(step => step.stepCode)).size === data.length,
    }),
    forms: (code, signal) => request(`/api/legal/forms?procedure=${requireCode(code, '목록에서 절차를 선택해 주세요.')}`, {
      signal, validate: data => Array.isArray(data) && data.every(form => isForm(form) && form.procedureCode === code),
    }),
    form: (formCode, procedureCode, signal) => request(`/api/legal/forms/${requireCode(formCode, '목록에서 양식을 선택해 주세요.')}`, {
      signal, validate: data => isForm(data) && data.formCode === formCode && data.procedureCode === procedureCode,
    }),
    login: (username, password, signal) => request('/api/auth/login', { method: 'POST', body: loginCredentials(username, password), signal,
      validate: data => isObject(data) && typeof data.accessToken === 'string' && !!data.accessToken && !/\s/.test(data.accessToken)
        && typeof data.username === 'string' && !!data.username.trim() && Object.hasOwn(AUTH_ROLES, data.role),
    }),
    currentUser: (accessToken, signal) => {
      const selectedToken = accessToken ?? getToken();
      if (typeof selectedToken !== 'string' || !selectedToken || /\s/.test(selectedToken)) throw new ApiError('로그인 후 계정 정보를 확인해 주세요.', 401);
      return request('/api/me', { signal, authenticated: true, accessToken: selectedToken,
        validate: data => isObject(data) && typeof data.username === 'string' && !!data.username.trim() && Object.hasOwn(AUTH_ROLES, data.role),
      });
    },
    create: (body, signal, options) => {
      const { idempotencyKey } = writeOptions(options);
      return request('/api/intakes', { method: 'POST', body, signal, idempotencyKey, authenticated: true, validate: data => isObject(data) && isId(data.id) });
    },
    intake: (id, signal) => {
      if (!isId(id)) throw new Error('유효한 문의 번호가 필요합니다.');
      return request(`/api/intakes/${encodeURIComponent(id)}`, { signal, authenticated: true, validate: data => isIntake(data) && String(data.id) === String(id) });
    },
    receiptIntake: (id, signal) => {
      if (!isId(id)) throw new Error('유효한 문의 번호가 필요합니다.');
      return request(`/api/intakes/${encodeURIComponent(id)}`, { signal, authenticated: true,
        validate: data => isIntake(data) && String(data.id) === String(id) && validIntakeTime(data.createdAt) && isIntakeStatus(data.status),
      });
    },
    intakes: signal => request('/api/intakes', { signal, authenticated: true,
      validate: data => Array.isArray(data) && data.length <= 50
        && data.every(item => isIntake(item) && validIntakeTime(item.createdAt))
        && new Set(data.map(item => String(item.id))).size === data.length,
    }),
    consultationIntake: (id, signal) => {
      if (!isId(id)) throw new Error('유효한 문의 번호가 필요합니다.');
      return request(`/api/intakes/${encodeURIComponent(id)}`, { signal, authenticated: true,
        validate: data => isConsultationIntake(data, id),
      });
    },
    detailIntake: (id, signal) => {
      if (!isId(id)) throw new Error('유효한 문의 번호가 필요합니다.');
      return request(`/api/intakes/${encodeURIComponent(id)}`, { signal, authenticated: true,
        validate: data => isIntake(data) && String(data.id) === String(id) && validIntakeTime(data.createdAt)
          && (data.consultationMemos === undefined || isConsultationIntake(data, id)) && Array.isArray(data.uploadedDocuments)
          && data.uploadedDocuments.every(file => isUploadedDocument(file, id))
          && new Set(data.uploadedDocuments.map(file => String(file.id))).size === data.uploadedDocuments.length,
      });
    },
    assignLawyer: (id, username, signal) => {
      if (!isId(id) || (username !== null && !/^[A-Za-z0-9_.-]{1,100}$/.test(username))) throw new Error('담당 변호사 계정을 확인해 주세요.');
      return request(`/api/intakes/${encodeURIComponent(id)}/assignment`, { method: 'PATCH', body: { assignedLawyerUsername: username }, signal, authenticated: true,
        validate: data => isConsultationIntake(data, id) && (data.assignedLawyerUsername ?? null) === username && data.permissions?.canAssign === true });
    },
    downloadDocument: (intakeId, file, signal) => {
      if (!isId(intakeId) || !isUploadedDocument(file, intakeId)) throw new Error('이 문의에 등록된 첨부파일을 선택해 주세요.');
      if (!getToken()) throw new ApiError('첨부파일은 로그인 후 다운로드할 수 있습니다.', 401);
      return request(documentPath(intakeId, file.id), { signal, authenticated: true, binary: true,
        validate: data => data instanceof Blob && data.size === file.sizeBytes && documentMime(data.type) === documentMime(file.contentType),
      });
    },
    updateStatus: (id, status, signal, options) => {
      if (!isId(id) || !isIntakeStatus(status)) throw new Error('문의 번호와 변경할 상태를 확인해 주세요.');
      const { expectedVersion } = writeOptions(options);
      return request(`/api/intakes/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status, ...(expectedVersion !== undefined ? { expectedVersion } : {}) }, signal, authenticated: true,
        validate: data => isObject(data) && String(data.id) === String(id) && data.status === status
          && (expectedVersion === undefined ? data.version === undefined || validVersion(data.version) : validVersion(data.version) && data.version >= expectedVersion)
          && isIntakeStatus(data.previousStatus) && typeof data.changed === 'boolean'
          && data.changed === (data.previousStatus !== data.status),
      });
    },
    createMemo: (id, content, followUpItems, signal, options) => {
      if (!isId(id)) throw new Error('유효한 문의 번호가 필요합니다.');
      const { idempotencyKey, expectedVersion } = writeOptions(options);
      return request(`/api/intakes/${encodeURIComponent(id)}/memo`, { method: 'POST', body: { ...consultationMemoPayload(content, followUpItems), ...(expectedVersion !== undefined ? { expectedVersion } : {}) }, signal, idempotencyKey, authenticated: true,
        validate: data => isMemo(data, id) && (expectedVersion === undefined || validVersion(data.intakeVersion) && data.intakeVersion >= expectedVersion),
      });
    },
    minimal: (intake, signal) => {
      if (!isIntake(intake)) throw new Error('등록된 문의 내용을 먼저 확인해 주세요.');
      const body = minimalPayload(intake);
      return request('/api/legal-intake/minimal', { method: 'POST', body, signal, authenticated: true,
        validate: data => isObject(data) && CASE_TYPES.some(type => type.value === data.caseType)
          && (body.caseType === 'UNKNOWN' || data.caseType === body.caseType)
          && ['caseTypeLabel', 'disputeArea', 'clientGuideMessage', 'privacyNotice', 'securityNotice'].every(key => typeof data[key] === 'string')
          && ['sessionId', 'inquiryPurpose', 'partyRole', 'desiredNextStep'].every(key => data[key] === body[key])
          && isStrings(data.checkedFacts) && isStrings(data.recommendedDocuments),
      });
    },
    criminalChecklist: signal => request('/api/legal/criminal/checklist/supplementary-investigation', { signal,
      validate: data => isObject(data) && data.domain === 'CRIMINAL' && data.role === 'COMPLAINANT'
        && data.stageCode === 'SUPPLEMENTARY_INVESTIGATION_AFTER_TRANSFER'
        && ['stageLabel', 'recommendedAction'].every(key => typeof data[key] === 'string') && isStrings(data.checklist),
    }),
    createSearch: (intakeId, searchPurpose, signal, sourcePolicy) => {
      if (!isId(intakeId)) throw new Error('유효한 문의 번호가 필요합니다.');
      if (typeof searchPurpose !== 'string' || searchPurpose.length > 300) throw new Error('검색 목적은 300자 이내로 입력해 주세요.');
      if (sourcePolicy !== undefined && (typeof sourcePolicy !== 'string' || !sourcePolicy.trim() || sourcePolicy.length > 100)) throw new Error('출처 정책은 100자 이내로 확인해 주세요.');
      return request(`/api/intakes/${encodeURIComponent(intakeId)}/search-requests`, { method: 'POST', body: { searchPurpose: searchPurpose.trim(), ...(sourcePolicy !== undefined ? { sourcePolicy: sourcePolicy.trim() } : {}) }, signal, authenticated: true,
        validate: data => isSearch(data) && String(data.intakeId) === String(intakeId),
      });
    },
    searchRequest: (intakeId, searchRequestId, signal) => {
      if (!isId(intakeId) || !isId(searchRequestId)) throw new Error('유효한 문의·검색 요청 번호가 필요합니다.');
      return request(`/api/intakes/search-requests/${encodeURIComponent(searchRequestId)}`, { signal, authenticated: true,
        validate: data => isSearch(data) && String(data.id) === String(searchRequestId) && String(data.intakeId) === String(intakeId),
      });
    },
    searchHistory: (intakeId, signal) => {
      if (!isId(intakeId)) throw new Error('유효한 문의 번호가 필요합니다.');
      if (!getToken()) throw new ApiError('검색 요청 이력은 로그인 후 확인해 주세요.', 401);
      return request(`/api/intakes/${encodeURIComponent(intakeId)}/search-requests`, { signal, authenticated: true,
        validate: data => Array.isArray(data) && data.every(item => isSearch(item) && String(item.intakeId) === String(intakeId))
          && new Set(data.map(item => String(item.id))).size === data.length,
      });
    },
    executeSearch: (intakeId, searchRequestId, retryExplanation = false, signal) => {
      if (!isId(intakeId) || !isId(searchRequestId)) throw new Error('유효한 문의·검색 요청 번호가 필요합니다.');
      if (!getToken()) throw new ApiError('외부 검색 실행은 문의 소유자 또는 관리자 로그인이 필요합니다.', 401);
      if (typeof retryExplanation !== 'boolean') throw new Error('설명 재생성 여부를 확인해 주세요.');
      return request(`/api/intakes/search-requests/${encodeURIComponent(searchRequestId)}/execute`, {
        method: 'POST', body: { retryExplanation }, signal, authenticated: true,
        validate: data => isSearch(data) && String(data.id) === String(searchRequestId) && String(data.intakeId) === String(intakeId),
      }).catch(error => {
        if (error.status === 503) error.message = '외부 검색 설정이 준비되지 않았습니다. 서버의 LLM·네이버 검색 설정이 필요합니다.';
        throw error;
      });
    },
    addSearchResult: (intakeId, searchRequestId, form, signal) => {
      if (!isId(intakeId) || !isId(searchRequestId)) throw new Error('유효한 문의·검색 요청 번호가 필요합니다.');
      return request(`/api/intakes/search-requests/${encodeURIComponent(searchRequestId)}/results`, { method: 'POST', body: searchResultPayload(form), signal, authenticated: true,
        validate: data => isSearch(data) && String(data.id) === String(searchRequestId) && String(data.intakeId) === String(intakeId),
      });
    },
  };
}
