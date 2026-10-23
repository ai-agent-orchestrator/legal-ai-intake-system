import { useEffect, useRef, useState } from 'react';
import { CASE_TYPES, PURPOSES, MAX_CASE_STORY_CHARS, selectProcedureCode, validateClassification } from './model.js';
import { Button, ChoiceGroup, Feedback, Steps, useResource } from './components.jsx';
import Icon from './Icon.jsx';

function ProcedureDetail({ api, code, domain }) {
  const detail = useResource(signal => api.procedure(code, signal, domain), [api, code, domain]);
  if (detail.status === 'loading') return <div className="intake-state" role="status"><span className="intake-spinner"/>절차를 불러오는 중입니다.</div>;
  if (detail.error) return <Feedback error={detail.error} retry={detail.retry}/>;
  return <div className="procedure-detail"><h3>{detail.data.title}</h3><p>{detail.data.summary}</p>{detail.data.steps.length ? <ol>{[...detail.data.steps].sort((a, b) => a.stepOrder - b.stepOrder).map(step => <li key={step.stepOrder}><span>{step.stepOrder}</span><div><strong>{step.title}</strong><p>{step.description}</p></div></li>)}</ol> : <p className="muted">등록된 단계가 없습니다.</p>}{detail.data.forms.length > 0 && <div className="reference-forms"><strong>참고 양식</strong>{detail.data.forms.map(form => <p key={form.formCode}>{form.title}<small>{form.usageNote}</small></p>)}</div>}</div>;
}

function ProcedurePanel({ api, domain }) {
  const list = useResource(signal => api.procedures(domain, signal), [api, domain]);
  const [selected, setSelected] = useState(null);
  const [expanded, setExpanded] = useState(true);
  const selectedCode = selectProcedureCode(list.data, selected);
  return <section className="intake-panel procedures" aria-labelledby="procedures-title"><h2 id="procedures-title"><Icon name="document"/>관련 절차 살펴보기</h2><p className="section-caption">{domain === 'UNKNOWN' ? '사건 유형이 미분류되어 전체 분야의 참고 절차를 표시합니다. 선택한 절차가 사건 유형을 확정하지는 않습니다.' : '선택한 사건 유형과 관련된 일반적인 절차를 확인해 보세요.'}</p>{list.status === 'loading' ? <div className="intake-state" role="status"><span className="intake-spinner"/>관련 절차를 불러오는 중입니다.</div> : list.error ? <Feedback error={list.error} retry={list.retry}/> : list.data.length === 0 ? <div className="intake-state">이 사건 유형에 등록된 참고 절차가 없습니다.</div> : <div className="procedure-layout"><div className="procedure-list" aria-label="참고 절차 목록">{list.data.map(procedure => <button type="button" key={procedure.code} className={selectedCode === procedure.code ? 'selected' : ''} aria-pressed={selectedCode === procedure.code} onClick={() => { setSelected(procedure.code); setExpanded(true); }}><Icon name={procedure.domain === 'CIVIL' ? 'scales' : 'document'}/><span><strong>{procedure.title}</strong><small>{procedure.summary}</small></span></button>)}</div><div className="procedure-content"><button className="detail-toggle" type="button" aria-expanded={expanded} aria-controls="procedure-body" onClick={() => setExpanded(value => !value)}>{list.data.find(item => item.code === selectedCode)?.title}<Icon name="chevron" className={expanded ? 'rotate' : ''}/></button>{expanded && <div id="procedure-body"><ProcedureDetail key={selectedCode} api={api} code={selectedCode} domain={list.data.find(item => item.code === selectedCode)?.domain}/></div>}</div></div>}<p className="reference-note">절차 참고 정보는 분류 결과와 별도로 제공됩니다.</p></section>;
}

function Preview({ result, busy, error, retry }) {
  const type = CASE_TYPES.find(option => option.value === result?.caseType);
  return <section className="intake-panel preview" aria-labelledby="preview-title" aria-busy={busy}><h2 id="preview-title"><Icon name="sparkles"/>사건 유형 확인 결과</h2><div aria-live="polite">{busy ? <div className="intake-state preview-empty"><span className="intake-spinner"/><strong>입력하신 내용을 확인하고 있습니다.</strong><span>잠시만 기다려 주세요.</span></div> : error ? <Feedback error={error} retry={retry}/> : !result ? <div className="intake-state preview-empty"><span className="empty-icon"><Icon name="scales"/></span><strong>어떤 도움이 필요하신가요?</strong><span>현재 상황을 적고 ‘사건 유형 확인하기’를 누르면<br/>사건 유형과 준비할 자료를 안내해 드립니다.</span></div> : <><div className={`result-hero ${result.caseType === 'UNKNOWN' ? 'unknown' : ''}`}><span className="result-icon"><Icon name={type?.icon || 'help'}/></span><div><div className="result-heading"><strong>{result.caseType === 'UNKNOWN' ? '미분류' : type?.label || result.caseTypeLabel}</strong><span className="result-badge">{result.caseType === 'UNKNOWN' ? '추가 확인 필요' : '판단 결과'}</span></div><p>{result.caseType === 'UNKNOWN' ? '상대방, 사건 경위 등을 조금 더 적어 보세요.' : `${result.disputeArea}으로 보입니다.`}</p></div></div><dl className="result-fields"><div><dt>주요 분쟁 분야</dt><dd>{result.disputeArea || '분류 대기'}</dd></div><div><dt>추천 준비 서류</dt><dd>{result.recommendedDocuments.length ? <ul>{result.recommendedDocuments.map(document => <li key={document}>{document}</li>)}</ul> : '추천 서류가 없습니다.'}</dd></div><div><dt>안내 메시지</dt><dd className="guide-message">{result.clientGuideMessage}</dd></div></dl><div className="preview-meta"><span>분류 방식 <b>{result.classificationMethod === 'RULE_BASED' ? '규칙 기반 분류 (RULE_BASED)' : result.classificationMethod}</b></span><span><Icon name="info"/>{result.persisted ? '저장됨' : '저장되지 않은 미리보기입니다.'}</span></div></>}</div></section>;
}

export default function ClassificationPage({ api, draft, setDraft, preview, setPreview }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [validation, setValidation] = useState('');
  const request = useRef(null);
  const description = useRef(null);
  useEffect(() => () => request.current?.abort(), []);

  function update(field, value) {
    request.current?.abort(); request.current = null;
    setBusy(false); setError(null); setValidation(''); setPreview(null);
    setDraft(current => ({ ...current, [field]: value }));
  }
  async function classify(event) {
    event?.preventDefault();
    if (request.current) return;
    const invalid = validateClassification(draft);
    setValidation(invalid);
    if (invalid) { description.current?.focus(); return; }
    const controller = new AbortController();
    request.current = controller;
    setBusy(true); setError(null); setPreview(null);
    try {
      const result = await api.classify(draft, controller.signal);
      if (!controller.signal.aborted) setPreview(result);
    } catch (failure) {
      if (!controller.signal.aborted) { setError(failure); if (failure.fieldErrors?.coreStatement) setValidation(`현재 상황을 ${MAX_CASE_STORY_CHARS}자 이내로 다시 확인해 주세요.`); }
    } finally {
      if (request.current === controller) { request.current = null; setBusy(false); }
    }
  }
  function next() {
    const invalid = validateClassification(draft);
    if (invalid) { setValidation(invalid); description.current?.focus(); return; }
    if (!preview) { setValidation('사건 유형을 먼저 확인해 주세요.'); return; }
    setDraft(current => ({ ...current, caseType: preview.caseType }));
    window.location.hash = '/intake/new';
  }
  const domain = preview?.caseType || draft.caseType;
  return <main className="intake-shell"><div className="classification-layout"><div className="classification-main"><Steps/><section className="intake-panel classification-form"><div className="page-intro"><h1>사건 유형을 잘 모르셔도 괜찮습니다.</h1><p>가까운 항목을 선택하거나 상황을 짧게 적어 주세요.</p></div><form onSubmit={classify} noValidate><ChoiceGroup legend="1. 문의 목적을 선택해 주세요." options={PURPOSES} value={draft.inquiryPurpose} onChange={value => update('inquiryPurpose', value)}/><ChoiceGroup legend="2. 사건 유형을 선택해 주세요." hint="정확히 모르실 경우 ‘잘 모르겠어요’를 선택하셔도 됩니다." options={CASE_TYPES} value={draft.caseType} onChange={value => update('caseType', value)}/><div className="description-field"><label htmlFor="core-statement">3. 현재 상황을 간단히 적어 주세요. <span>(필수)</span></label><div className={`textarea-wrap ${validation ? 'invalid' : ''}`}><textarea id="core-statement" ref={description} required maxLength={MAX_CASE_STORY_CHARS} value={draft.coreStatement} onChange={event => update('coreStatement', event.target.value)} placeholder="예: 돈을 빌려줬는데 돌려받지 못하고 있어요." aria-invalid={!!validation} aria-describedby="description-hint description-count description-error"/><span id="description-count" className="character-count">{draft.coreStatement.length.toLocaleString('ko-KR')} / {MAX_CASE_STORY_CHARS.toLocaleString('ko-KR')}</span></div><p id="description-hint" className="field-hint">사건의 배경, 상대방, 금액, 현재 상황 등을 간단히 적어주시면 더 정확한 안내를 받을 수 있습니다.</p><p id="description-error" className="field-error" role={validation ? 'alert' : undefined}>{validation}</p></div><div className="classify-actions"><Button type="submit" busy={busy} disabled={busy}><Icon name="sparkles"/>{busy ? '사건 유형 확인 중' : '사건 유형 확인하기'}</Button></div></form></section></div><aside className="classification-aside"><Preview result={preview} busy={busy} error={error} retry={() => classify()}/><ProcedurePanel key={domain} api={api} domain={domain}/><div className="next-action"><Button type="button" disabled={busy || !preview} onClick={next}>다음: 문의 작성<Icon name="arrow"/></Button></div></aside></div><p className="intake-footnote"><Icon name="info"/>분류 결과는 입력 내용을 바탕으로 한 참고 안내입니다.</p></main>;
}
