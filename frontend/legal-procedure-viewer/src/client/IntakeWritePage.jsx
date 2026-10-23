import { useEffect, useRef, useState } from 'react';
import { CASE_TYPES, CURRENT_STAGES, DOCUMENT_OPTIONS, INTAKE_FIELDS, NEXT_STEPS, PARTY_POSITIONS, PURPOSES, intakePayload, intakeValues } from './model.js';
import { Button, Feedback, Steps } from './components.jsx';
import IntakeReference from './IntakeReference.jsx';
import Icon from './Icon.jsx';

function Field({ name, label, required, error, icon, children, className = '' }) {
  return <div className={`write-field ${className}`}>
    <label htmlFor={`write-${name}`}>{label}{required && <span className="required-mark" aria-label="필수"> *</span>}</label>
    <div className={`write-input ${icon ? 'with-icon' : ''}`}>{icon && <Icon name={icon}/ >}{children}</div>
    {error && <small className="write-error" id={`error-${name}`}>{error}</small>}
  </div>;
}

export default function IntakeWritePage({ api, draft, preview, form, setForm, onCreated }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [searchOpen, setSearchOpen] = useState(false);
  const request = useRef(null);
  const formElement = useRef(null);
  const caseTypeField = useRef(null);
  const pendingFocus = useRef(null);
  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => {
    if (!pendingFocus.current) return;
    if (!searchOpen && ['officeType', 'experienceLevel', 'publicCareerPreference', 'consultationStyle', 'lawyerSearchPurpose'].includes(pendingFocus.current)) { setSearchOpen(true); return; }
    formElement.current?.querySelector?.(`[name="${pendingFocus.current}"]`)?.focus();
    pendingFocus.current = null;
  }, [fieldErrors, searchOpen]);
  if (!preview) return <main className="intake-shell compact"><Steps current={2}/><section className="intake-panel"><h1>사건 유형부터 확인해 주세요.</h1><p>이전 화면에서 문의 목적과 현재 상황을 입력하면 이어서 작성할 수 있습니다.</p><a className="intake-button" href="#/intake/type">사건 유형 확인으로 이동</a></section></main>;

  const values = intakeValues(draft, preview, form);
  const type = CASE_TYPES.find(item => item.value === values.caseType);
  const originalPreview = values.caseType === preview.caseType && values.coreStatement.trim() === draft.coreStatement.trim();
  function update(name, value) {
    setForm(current => ({ ...current, [name]: value }));
    setFieldErrors(current => ({ ...current, [name]: '' }));
    setError(null);
  }
  function inputProps(name) {
    return { id: `write-${name}`, name, value: values[name] || '', maxLength: INTAKE_FIELDS[name][0],
      required: !!INTAKE_FIELDS[name][2], 'aria-invalid': !!fieldErrors[name],
      'aria-describedby': fieldErrors[name] ? `error-${name}` : undefined,
      onChange: event => update(name, event.target.value) };
  }
  function focusError(errors) {
    // Only fixed DTO field names are used as selectors.
    pendingFocus.current = [...Object.keys(INTAKE_FIELDS), 'documents'].find(key => errors[key]);
  }
  async function submit(event) {
    event.preventDefault();
    if (request.current) return;
    let payload;
    try { payload = intakePayload(draft, preview, form); }
    catch (failure) { setFieldErrors(failure.fieldErrors || {}); setError(failure); focusError(failure.fieldErrors || {}); return; }
    const controller = new AbortController();
    request.current = controller;
    setBusy(true); setError(null); setFieldErrors({});
    try {
      const data = await api.create(payload, controller.signal);
      if (!controller.signal.aborted) onCreated(data.id);
    } catch (failure) {
      if (!controller.signal.aborted) {
        const errors = {};
        Object.keys(failure.fieldErrors || {}).forEach(key => {
          if (INTAKE_FIELDS[key]) errors[key] = `${INTAKE_FIELDS[key][1]} 항목을 다시 확인해 주세요.`;
          else if (key.startsWith('documents')) errors.documents = '보유 자료 선택을 다시 확인해 주세요.';
        });
        setError(failure); setFieldErrors(errors); focusError(errors);
      }
    } finally {
      if (request.current === controller) { request.current = null; setBusy(false); }
    }
  }
  function documentChanged(document, checked) {
    const selected = checked ? document === '없음' ? ['없음'] : [...form.documents.filter(item => item !== '없음'), document]
      : form.documents.filter(item => item !== document);
    update('documents', [...new Set(selected)]);
  }
  const documentOptions = [...DOCUMENT_OPTIONS.slice(0, -1), ...form.documents.filter(item => !DOCUMENT_OPTIONS.includes(item)), '없음'];

  return <main className="intake-shell write-shell"><div className="write-layout">
    <div className="write-main"><Steps current={2}/><section className="intake-panel intake-write-panel">
      <div className="write-intro"><h1>상담에 필요한 정보를 정리해 주세요.</h1><p>입력하신 내용은 더 정확한 법률 안내를 위해 사용됩니다.</p></div>
      <div className="selected-case"><div className="selected-case-label">선택한 사건 유형 <small>(수정 가능)</small></div><div className="selected-case-body"><Icon name={type?.icon || 'help'}/><div><strong>{type?.label}</strong><span>{originalPreview ? preview.disputeArea : '직접 선택'}</span><p>{originalPreview ? `${preview.disputeArea} 관련 문의입니다.` : '변경한 사건 유형으로 문의 내용을 작성합니다.'}</p></div></div><button type="button" className="case-edit" disabled={busy} onClick={() => caseTypeField.current?.focus()}><Icon name="pencil"/>다른 사건 유형으로 수정하기</button></div>
      <form ref={formElement} onSubmit={submit} noValidate aria-label="문의 접수 정보" aria-busy={busy}>
        <fieldset className="write-controls" disabled={busy}><legend className="visually-hidden">문의 정보 입력</legend>
          <div className="write-section-title"><h2>필수 정보</h2><span className="required-note">● 필수</span></div>
          <div className="write-required-grid">
            <Field name="inquiryPurpose" label="문의 목적" required error={fieldErrors.inquiryPurpose} icon="document"><select {...inputProps('inquiryPurpose')}>{PURPOSES.map(item => <option key={item.value}>{item.value}</option>)}</select></Field>
            <Field name="caseType" label="사건 유형" required error={fieldErrors.caseType}><select ref={caseTypeField} {...inputProps('caseType')}>{CASE_TYPES.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</select></Field>
            <fieldset className="party-positions" aria-describedby={fieldErrors.partyPosition ? 'error-partyPosition' : undefined}><legend>당사자 위치<span className="required-mark"> *</span></legend><div>{PARTY_POSITIONS.map(position => <label key={position} className={values.partyPosition === position ? 'selected' : ''}><input type="radio" name="partyPosition" value={position} checked={values.partyPosition === position} onChange={() => update('partyPosition', position)} aria-invalid={!!fieldErrors.partyPosition}/><span>{position}</span></label>)}</div>{fieldErrors.partyPosition && <small className="write-error" id="error-partyPosition">{fieldErrors.partyPosition}</small>}</fieldset>
          </div>
          <div className="write-description-grid"><Field name="coreStatement" label="핵심 내용" required error={fieldErrors.coreStatement}><textarea {...inputProps('coreStatement')} placeholder="사건의 경위와 현재 상황을 적어 주세요."/><span className="write-count">{values.coreStatement.length.toLocaleString()} / {INTAKE_FIELDS.coreStatement[0].toLocaleString('ko-KR')}</span></Field><Field name="desiredNextStep" label="원하는 다음 단계" required error={fieldErrors.desiredNextStep} icon="search"><select {...inputProps('desiredNextStep')}><option value="">원하는 다음 단계를 선택해 주세요.</option>{NEXT_STEPS.map(step => <option key={step}>{step}</option>)}</select></Field></div>
          <div className="write-section-title optional-title"><h2>선택 정보 <small>(선택 사항)</small></h2></div>
          <div className="write-optional-grid">
            <Field name="incidentDate" label="사건 발생일" error={fieldErrors.incidentDate}><input {...inputProps('incidentDate')} type="date"/></Field>
            <Field name="counterpart" label="상대방 (개인/회사명)" error={fieldErrors.counterpart}><input {...inputProps('counterpart')} placeholder="상대방 이름 또는 회사명" autoComplete="off"/></Field>
            <Field name="amountOrDamage" label="금액·피해 규모" error={fieldErrors.amountOrDamage} icon="won"><input {...inputProps('amountOrDamage')} placeholder="예: 5,000,000원"/></Field>
            <Field name="currentStage" label="현재 진행 단계" error={fieldErrors.currentStage}><select {...inputProps('currentStage')}><option value="">선택해 주세요.</option>{CURRENT_STAGES.map(stage => <option key={stage}>{stage}</option>)}</select></Field>
            <Field name="clientName" label="이름" error={fieldErrors.clientName} icon="user"><input {...inputProps('clientName')} placeholder="이름" autoComplete="name"/></Field>
            <Field name="clientPhone" label="연락처" error={fieldErrors.clientPhone} icon="phone"><input {...inputProps('clientPhone')} type="tel" placeholder="010-1234-5678" autoComplete="tel"/></Field>
            <Field name="preferredRegion" label="선호 지역" error={fieldErrors.preferredRegion} icon="pin"><input {...inputProps('preferredRegion')} placeholder="예: 서울특별시" autoComplete="address-level1"/></Field>
            <button type="button" className="search-condition-toggle" aria-expanded={searchOpen} aria-controls="lawyer-search-fields" onClick={() => setSearchOpen(value => !value)}>변호사 검색 선호 조건 (선택)<Icon name="chevron" className={searchOpen ? 'rotate' : ''}/></button>
          </div>
          <div id="lawyer-search-fields" className="lawyer-search-fields" hidden={!searchOpen}>
            {[
              ['officeType', '사무소 유형', ['개인 법률사무소', '법무법인', '상관없음']],
              ['experienceLevel', '경력 조건', ['5년 미만', '5년 이상', '10년 이상', '상관없음']],
              ['publicCareerPreference', '공직·수사기관 경력 선호', ['선호함', '상관없음']],
              ['consultationStyle', '상담 방식', ['방문 상담', '전화 상담', '온라인 상담', '상관없음']],
            ].map(([name, label, options]) => <Field key={name} name={name} label={label} error={fieldErrors[name]}><select {...inputProps(name)}><option value="">선택하지 않음</option>{options.map(option => <option key={option}>{option}</option>)}</select></Field>)}
            <Field name="lawyerSearchPurpose" label="변호사 검색 목적" error={fieldErrors.lawyerSearchPurpose} className="search-purpose"><textarea {...inputProps('lawyerSearchPurpose')} placeholder="상담받고 싶은 내용이나 선호 조건을 적어 주세요."/></Field>
          </div>
          <fieldset className="write-documents"><legend>보유 자료 <small>(보유하고 있는 자료를 선택해 주세요. 여러 개 선택 가능)</small></legend><div>{documentOptions.map(document => <label key={document} className={form.documents.includes(document) ? 'selected' : ''}><input name="documents" type="checkbox" checked={form.documents.includes(document)} onChange={event => documentChanged(document, event.target.checked)}/><span>{document}</span></label>)}</div>{fieldErrors.documents && <small id="error-documents" className="write-error">{fieldErrors.documents}</small>}</fieldset>
        </fieldset>
        {error && <Feedback error={error}/ >}
        <div className="write-footer"><p className="write-privacy"><Icon name="lock"/><span>입력한 정보는 법률 상담과 안내를 위해 사용됩니다.<br/><small>주민등록번호·계좌번호 등 불필요한 민감 정보는 입력하지 마세요.</small></span></p><div><Button type="button" className="secondary" disabled={busy} onClick={() => { window.location.hash = '/intake/type'; }}>이전</Button><Button type="submit" disabled={busy} busy={busy}><Icon name="document"/ >{busy ? '문의 접수 중' : '문의 접수하기'}</Button></div></div>
      </form>
    </section></div>
    <IntakeReference key={values.caseType} api={api} domain={values.caseType}/>
  </div></main>;
}
