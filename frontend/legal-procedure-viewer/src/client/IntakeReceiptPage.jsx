import { useState } from 'react';
import { Button, Feedback, Steps, useResource } from './components.jsx';
import { CASE_TYPES, INTAKE_STATUSES, selectProcedureCode } from './model.js';
import Icon from './Icon.jsx';

const display = value => typeof value === 'string' && value.trim() ? value : '입력하지 않음';
const formatDate = value => /^\d{4}-\d{2}-\d{2}$/.test(value || '') ? value.replaceAll('-', '. ') + '.' : display(value);

export function IntakeSummary({ intake }) {
  const type = CASE_TYPES.find(item => item.value === intake.caseType);
  const rows = [
    ['사건 유형', <><b>{type?.label || intake.caseTypeLabel}</b>{intake.disputeArea && <span className="receipt-tag">{intake.disputeArea}</span>}</>, '현재 진행 단계', display(intake.currentStage)],
    ['문의 목적', intake.inquiryPurpose, '원하는 다음 단계', intake.desiredNextStep],
    ['당사자 위치', intake.partyPosition, '보유 자료', intake.selectedDocuments.length ? <span className="receipt-tags">{intake.selectedDocuments.map((item, index) => <span key={`${index}:${item}`}>{item}</span>)}</span> : '선택 자료 없음'],
    ['사건 발생일', formatDate(intake.incidentDate), '이름', display(intake.clientNameMasked)],
    ['상대방 (개인/회사명)', display(intake.counterpartMasked), '연락처', display(intake.clientPhoneMasked)],
    ['금액·피해 규모', display(intake.amountOrDamage), '선호 지역', display(intake.preferredRegion)],
  ];
  return <section className="receipt-summary"><h2><Icon name="document"/>접수된 문의 내용</h2><div className="receipt-table-wrap"><table><caption className="visually-hidden">접수된 문의의 유형과 상담 정보</caption><colgroup><col/><col/><col/><col/></colgroup><tbody>{rows.map(([label, value, label2, value2]) => <tr key={label}><th scope="row">{label}</th><td>{value}</td><th scope="row">{label2}</th><td>{value2}</td></tr>)}</tbody></table></div></section>;
}

function ProtectedStatement({ intake }) {
  const [expanded, setExpanded] = useState(false);
  return <section className="receipt-statement"><div><h2><Icon name="document"/>작성하신 핵심 내용 <small>(보호 처리 결과)</small></h2><button type="button" aria-expanded={expanded} aria-controls="receipt-core" onClick={() => setExpanded(value => !value)}><Icon name="eye"/>{expanded ? '접기' : '더 보기'}<Icon name="chevron" className={expanded ? 'rotate' : ''}/></button></div><p id="receipt-core" className={expanded ? 'expanded' : ''}>{intake.coreStatementMasked}</p></section>;
}

function ProcedureDetail({ api, code, domain }) {
  const result = useResource(signal => api.procedure(code, signal, domain), [api, code, domain]);
  if (result.status === 'loading') return <div className="receipt-reference-state" role="status"><span className="intake-spinner"/>절차 안내를 불러오는 중입니다.</div>;
  if (result.error) return <Feedback error={result.error} retry={result.retry}/>;
  return <div className="receipt-procedure-detail"><p>{result.data.summary}</p>{result.data.steps.length ? <ol>{[...result.data.steps].sort((a, b) => a.stepOrder - b.stepOrder).map(step => <li key={step.stepCode}><b>{step.title}</b><span>{step.description}</span></li>)}</ol> : <p>등록된 절차 단계가 없습니다.</p>}</div>;
}

function RelatedProcedures({ api, domain }) {
  const result = useResource(signal => api.procedures(domain, signal), [api, domain]);
  const [selected, setSelected] = useState(null);
  const [expanded, setExpanded] = useState(false);
  const code = selectProcedureCode(result.data, selected);
  return <section className="receipt-procedures"><h2><Icon name="document"/>관련 절차 살펴보기</h2>{domain === 'UNKNOWN' && <p className="receipt-reference-note">저장된 사건 유형이 미분류여서 전체 참고 절차를 표시합니다.</p>}{result.status === 'loading' ? <div className="receipt-reference-state" role="status"><span className="intake-spinner"/>관련 절차를 불러오는 중입니다.</div> : result.error ? <Feedback error={result.error} retry={result.retry}/> : !result.data.length ? <p className="receipt-reference-state">이 사건 유형에 등록된 참고 절차가 없습니다.</p> : result.data.map(procedure => <div className="receipt-procedure" key={procedure.code}><button type="button" className={code === procedure.code ? 'selected' : ''} aria-expanded={code === procedure.code && expanded} aria-controls={`receipt-procedure-${encodeURIComponent(procedure.code)}`} onClick={() => { setSelected(procedure.code); setExpanded(current => code !== procedure.code || !current); }}><Icon name="document"/><strong>{procedure.title}</strong><Icon name="chevron" className={code === procedure.code && expanded ? 'rotate' : ''}/></button>{code === procedure.code && expanded && <div id={`receipt-procedure-${encodeURIComponent(procedure.code)}`}><ProcedureDetail key={code} api={api} code={code} domain={procedure.domain}/></div>}</div>)}</section>;
}

function CriminalChecklist({ api }) {
  const result = useResource(signal => api.criminalChecklist(signal), [api]);
  return <section className="receipt-criminal"><h2><Icon name="clipboard"/>형사 보완수사 참고 체크리스트</h2>{result.status === 'loading' ? <div className="receipt-reference-state" role="status"><span className="intake-spinner"/>체크리스트를 불러오는 중입니다.</div> : result.error ? <Feedback error={result.error} retry={result.retry}/> : <><p>{result.data.stageLabel} · 고소인 참고용</p><p className="receipt-reference-note">검찰 송치 후 보완수사 단계의 참고 자료이며, 현재 문의의 진행 단계를 판정한 내용은 아닙니다.</p>{result.data.checklist.length ? <ul>{result.data.checklist.map((item, index) => <li key={index}>{item}</li>)}</ul> : <p>등록된 준비 항목이 없습니다.</p>}</>}</section>;
}

function SimpleGuide({ api, intake }) {
  const result = useResource(signal => api.minimal(intake, signal), [api, intake]);
  return <section className="receipt-guide"><h2><Icon name="bulb"/>간단 안내</h2><p className="receipt-guide-caption">입력하신 내용을 바탕으로 준비에 도움이 되는 내용을 안내해 드립니다.</p>{result.status === 'loading' ? <div className="receipt-reference-state" role="status"><span className="intake-spinner"/>간단 안내를 준비하고 있습니다.</div> : result.error ? <Feedback error={result.error} retry={result.retry}/> : <>
    <div className="guide-card facts"><Icon name="document"/><div><h3>확인된 내용</h3>{result.data.checkedFacts.length ? <ul tabIndex={0} aria-label="확인된 내용 목록">{result.data.checkedFacts.map((fact, index) => <li key={index}>{fact}</li>)}</ul> : <p>정리할 내용이 없습니다.</p>}</div></div>
    <div className="guide-card documents"><Icon name="folder"/><div><h3>준비하면 좋은 자료</h3>{result.data.recommendedDocuments.length ? <ul tabIndex={0} aria-label="준비하면 좋은 자료 목록">{result.data.recommendedDocuments.map((document, index) => <li key={index}><Icon name="circleCheck"/>{document}</li>)}</ul> : <p>추천 준비 자료가 없습니다.</p>}</div></div>
    <div className="guide-card direction"><Icon name="chart"/><div><h3>다음 정리 방향</h3><p>{result.data.clientGuideMessage}</p><p className="desired-step"><Icon name="circleCheck"/>상담에서 확인하고 싶은 내용: {result.data.desiredNextStep}</p></div></div>
  </>}</section>;
}

export default function IntakeReceiptPage({ api, id, onNewInquiry }) {
  const result = useResource(signal => api.receiptIntake(id, signal), [api, id]);
  const intake = result.data;
  return <main className="intake-shell receipt-shell"><div className="receipt-layout"><div className="receipt-main"><Steps current={3}/><section className="intake-panel receipt-panel">
    {result.status === 'loading' ? <div className="intake-state" role="status"><span className="intake-spinner"/>등록된 문의를 확인하고 있습니다.</div> : result.error ? <><Feedback error={result.error} retry={result.retry}/><Button type="button" className="secondary" onClick={onNewInquiry}>새 문의 작성</Button></> : <>
      <div className="receipt-hero"><span className="receipt-success-icon"><Icon name="check"/></span><div className="receipt-intro"><h1>문의가 접수되었습니다.</h1><p>입력하신 내용을 바탕으로 주요 내용을 정리하고,<br/>준비에 도움이 되는 안내를 제공해 드립니다.</p></div><div className="receipt-number"><dl><div><dt>문의 번호</dt><dd>No. {intake.id}</dd></div><div><dt>현재 상태</dt><dd><span className={`receipt-status status-${intake.status.toLowerCase()}`}>{INTAKE_STATUSES[intake.status] || intake.status}</span></dd></div></dl><p><Icon name="info"/>접수한 문의는 아래 내용으로 확인하실 수 있습니다.</p></div></div>
      <IntakeSummary intake={intake}/><ProtectedStatement intake={intake}/>
      <div className="receipt-notices"><section><Icon name="lock"/><div><h3>개인정보 보호 안내</h3><p>{intake.privacyNotice}</p></div></section><section><Icon name="shieldCheck"/><div><h3>내용 표시 안내</h3><p>가려진 원문을 제외한 보호 처리 결과만 표시합니다.</p></div></section></div>
    </>}
  </section></div><aside className="receipt-aside" aria-label="접수 문의 안내">{intake && result.status === 'success' && <>
    <div className="intake-panel receipt-aside-panel"><SimpleGuide key={String(intake.id)} api={api} intake={intake}/><RelatedProcedures key={intake.caseType} api={api} domain={intake.caseType}/>{intake.caseType === 'CRIMINAL' && <CriminalChecklist api={api}/>}</div>
    <div className="receipt-actions"><a className="intake-button secondary" href={`#/intake/${intake.id}/review`} aria-label="이전: 접수 내용 확인"><Icon name="back"/>이전</a><a className="intake-button" href={`#/intake/${intake.id}/search`}>검색 요청 준비하기<Icon name="arrow"/></a><Button type="button" className="secondary" onClick={onNewInquiry}><Icon name="plus"/>새 문의 작성</Button></div>
  </>}</aside></div></main>;
}

export function IntakeReviewPage({ api, id, onNewInquiry }) {
  const result = useResource(signal => api.receiptIntake(id, signal), [api, id]);
  return <main className="intake-shell compact receipt-review"><Steps current={2}/><section className="intake-panel"><h1>접수된 문의 내용을 확인해 주세요.</h1><p className="muted">접수된 내용을 확인하는 화면입니다. 추가 문의는 새 문의 작성으로 진행해 주세요.</p>{result.status === 'loading' ? <div className="intake-state" role="status"><span className="intake-spinner"/>접수 내용을 불러오는 중입니다.</div> : result.error ? <Feedback error={result.error} retry={result.retry}/> : <><p className="muted">문의 번호 {result.data.id}</p><IntakeSummary intake={result.data}/><ProtectedStatement intake={result.data}/></>}<div className="button-row"><a className="intake-button" href={`#/intake/${id}`}>안내 확인으로 돌아가기</a><Button type="button" className="secondary" onClick={onNewInquiry}>새 문의 작성</Button></div></section></main>;
}
