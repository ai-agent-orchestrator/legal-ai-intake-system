import { useEffect, useRef, useState } from 'react';
import { Button, Feedback, useResource } from './components.jsx';
import { LawyerShell } from './IntakeListPage.jsx';
import { CASE_TYPES, DOCUMENT_OPTIONS } from './model.js';
import { LIST_STATUSES } from './intakeListModel.js';
import { consultationTime, FOLLOW_UP_ITEMS, recentMemos } from './consultationModel.js';
import { documentTypeLabel, fileSize, saveDocument } from './intakeDetailModel.js';
import Icon from './Icon.jsx';
import IntakeAssignment from './IntakeAssignment.jsx';

const display = value => typeof value === 'string' && value.trim() ? value : '미입력';
const incidentDate = value => /^\d{4}-\d{2}-\d{2}$/.test(value || '') ? value.replaceAll('-', '. ') + '.' : display(value);

function DetailOverview({ intake }) {
  return <section className="detail-card detail-overview"><h2><Icon name="document"/>사건 개요</h2><table><caption className="visually-hidden">접수 문의의 사건 유형과 목적</caption><colgroup>{['16%', '28%', '18%', '38%'].map((width, index) => <col key={index} style={{ width }}/>)}</colgroup><tbody>
    <tr><th scope="row">사건 유형</th><td><div className="detail-overview-type"><span className="inquiry-type">{CASE_TYPES.find(type => type.value === intake.caseType)?.label}</span><span title={intake.disputeArea || ''}>{intake.disputeArea || ''}</span></div></td><th scope="row">분쟁 영역</th><td>{display(intake.disputeArea)}</td></tr>
    <tr><th scope="row">문의 목적</th><td>{intake.inquiryPurpose}</td><th scope="row">원하는 다음 단계</th><td>{intake.desiredNextStep}</td></tr>
    <tr><th scope="row">당사자 위치</th><td>{intake.partyPosition}</td><th scope="row">현재 진행 단계</th><td>{display(intake.currentStage)}</td></tr>
  </tbody></table></section>;
}
function DetailFacts({ intake }) {
  return <section className="detail-card detail-facts"><h2><Icon name="document"/>사실관계 <small>(의뢰인 입력 내용)</small></h2><dl><div><dt>사건 발생일</dt><dd>{incidentDate(intake.incidentDate)}</dd></div><div><dt>상대방 (개인/회사명)</dt><dd>{display(intake.counterpartMasked)}</dd></div><div><dt>금액·피해 규모</dt><dd>{display(intake.amountOrDamage)}</dd></div></dl><section className="detail-core"><h3>핵심 내용</h3><p tabIndex="0" aria-label="보호 처리된 핵심 내용 전문">{intake.coreStatementMasked}</p></section></section>;
}
function DetailDocuments({ intake }) {
  const selected = new Set(intake.selectedDocuments.filter(value => value.trim() && !['없음', '선택 자료 없음'].includes(value)));
  const options = [...new Set([...DOCUMENT_OPTIONS.filter(value => value !== '없음'), ...selected])];
  return <div className="detail-document-columns"><section className="detail-card"><h2><Icon name="folder"/>보유 자료 <small>(의뢰인 선택)</small></h2><ul className="detail-document-list">{[...options, '없음'].map(value => {
    const checked = value === '없음' ? !selected.size : selected.has(value);
    return <li key={value}><span className={`detail-document-check ${checked ? 'selected' : ''}`} aria-label={checked ? '선택됨' : '선택 안 됨'}>{checked && <Icon name="check"/>}</span>{value}</li>;
  })}</ul><p className="detail-note">등록된 자료 종류이며, 아래 첨부파일 목록과 다를 수 있습니다.</p></section><section className="detail-card"><h2><Icon name="document"/>추천 준비 자료 <small>(안내 내용)</small></h2>{intake.recommendedDocuments.length ? <ul className="detail-document-list">{intake.recommendedDocuments.map((item, index) => <li key={index}><Icon name="document"/>{item}</li>)}</ul> : <p className="detail-empty">추천 준비 자료가 없습니다.</p>}<p className="detail-note">추천 항목을 표시하며 준비 완료 여부를 저장하는 기능은 아닙니다.</p></section></div>;
}
function SearchCondition({ condition }) {
  return <section className="detail-card detail-search-condition"><h2><Icon name="document"/>변호사 탐색 조건 <small>(저장된 내용)</small></h2>{condition ? <><table><caption className="visually-hidden">저장된 변호사 탐색 조건</caption><colgroup>{['18%', '30%', '20%', '32%'].map((width, index) => <col key={index} style={{ width }}/>)}</colgroup><tbody>
    <tr><th scope="row">법률 분야</th><td>{condition.legalField}</td><th scope="row">사무소 유형</th><td>{condition.officeType}</td></tr>
    <tr><th scope="row">분쟁 유형</th><td>{condition.disputeType}</td><th scope="row">경력 선호</th><td>{condition.experienceLevel}</td></tr>
    <tr><th scope="row">선호 지역</th><td>{condition.preferredRegion}</td><th scope="row">저장된 검색어</th><td className="detail-search-query"><span title={condition.searchQuery}>{condition.searchQuery}</span></td></tr>
  </tbody></table><details><summary>추가 탐색 조건 보기</summary><dl><div><dt>저장된 검색어 전체</dt><dd>{condition.searchQuery}</dd></div><div><dt>공직·수사기관 경력</dt><dd>{condition.publicCareerPreference}</dd></div><div><dt>상담 방식</dt><dd>{condition.consultationStyle}</dd></div><div><dt>출처 정책</dt><dd>{condition.sourcePolicy}</dd></div></dl></details></> : <p className="detail-empty">저장된 변호사 탐색 조건이 없습니다.</p>}</section>;
}
function Attachments({ api, intake, user, onLogin }) {
  const [pending, setPending] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const request = useRef(null);
  useEffect(() => () => request.current?.abort(), [api, intake.id, user?.username]);
  async function download(file) {
    if (request.current) return;
    if (!user) { onLogin(); return; }
    const controller = new AbortController(); request.current = controller;
    setPending(file.id); setError(null); setNotice('');
    try {
      const blob = await api.downloadDocument(intake.id, file, controller.signal);
      if (controller.signal.aborted) return;
      saveDocument(blob, file.originalFilename);
      setNotice(`${file.originalFilename} 다운로드를 시작했습니다.`);
    } catch (failure) { if (!controller.signal.aborted) setError(failure); }
    finally { if (!controller.signal.aborted) setPending(null); if (request.current === controller) request.current = null; }
  }
  // A logout also ends an in-flight download without keeping a disabled button.
  useEffect(() => { setPending(null); }, [user?.username]);
  return <section className="detail-card detail-attachments"><h2><Icon name="attachment"/>첨부 파일 <small>(접수 후 업로드된 자료)</small><span className="detail-count">{intake.uploadedDocuments.length}개</span></h2>{intake.uploadedDocuments.length ? <><div className="detail-attachment-scroll" tabIndex="0" role="region" aria-label="첨부파일 목록"><table><caption className="visually-hidden">실제 업로드된 첨부파일</caption><thead><tr><th scope="col">파일명</th><th scope="col">유형</th><th scope="col">크기</th><th scope="col">업로드 일시</th><th scope="col">작업</th></tr></thead><tbody>{intake.uploadedDocuments.map(file => <tr key={file.id}><td title={file.originalFilename}>{file.originalFilename}</td><td>{documentTypeLabel(file.contentType)}</td><td>{fileSize(file.sizeBytes)}</td><td><time dateTime={file.createdAt}>{consultationTime(file.createdAt)}</time></td><td><Button type="button" className="secondary" disabled={pending !== null} busy={pending === file.id} aria-label={`${file.originalFilename} 다운로드`} onClick={() => download(file)}>다운로드</Button></td></tr>)}</tbody></table></div><p className="detail-note">로그인한 문의 소유자 또는 관리자만 다운로드할 수 있습니다.</p></> : <p className="detail-empty">업로드된 첨부파일이 없습니다.</p>}{pending !== null && <p className="detail-download-state" role="status"><span className="intake-spinner"/>파일을 준비하고 있습니다.</p>}{notice && <p className="detail-download-state" role="status">{notice}</p>}{error && <Feedback error={error}/>}</section>;
}
function DetailMemos({ intake }) {
  return <section className="detail-card detail-memos"><h2><Icon name="memo"/>상담 메모 <small>(보호 처리 결과)</small><a className="detail-memo-link" href={`#/lawyer/intakes/${intake.id}/consultation`}>메모 관리하기<Icon name="arrow"/></a></h2>{intake.consultationMemos.length ? <ul tabIndex="0" aria-label="최근 상담 메모">{recentMemos(intake.consultationMemos).map(memo => <li key={memo.id}><div><b>{memo.createdBy || '기존 작성자 미기록'} · 상담 메모 #{memo.id}</b><time dateTime={memo.createdAt}>{consultationTime(memo.createdAt)}</time></div><p>{memo.contentMasked}</p>{memo.followUpItems.length > 0 && <span>{memo.followUpItems.map(value => FOLLOW_UP_ITEMS.find(item => item.value === value)?.label).join(' · ')}</span>}</li>)}</ul> : <div className="detail-memo-empty"><Icon name="document"/><p>아직 작성된 상담 메모가 없습니다.<br/>[상태 · 상담 메모 관리]에서 상담 내용과 후속 업무를 기록할 수 있습니다.</p></div>}</section>;
}
export default function IntakeDetailPage({ api, id, user, onLogin, onReady }) {
  const resource = useResource(signal => api.detailIntake(id, signal), [api, id]);
  const [assignmentNotice, setAssignmentNotice] = useState('');
  const intake = resource.data;
  return <LawyerShell user={user} detail onReady={onReady}><main className="inquiry-panel intake-detail-panel"><div className="detail-heading"><div><nav className="consultation-breadcrumb" aria-label="현재 위치"><a href="#/lawyer/intakes">문의 목록</a><Icon name="chevron"/><span>문의 상세</span></nav><h1>문의 #{id} 상세 {intake && <span className={`inquiry-status status-${intake.status.toLowerCase()}`}>{LIST_STATUSES[intake.status]}</span>}</h1><p>접수일시 <time>{intake ? consultationTime(intake.createdAt) : '확인 중'}</time></p></div><div className="detail-heading-actions"><a className="intake-button secondary" href="#/lawyer/intakes"><Icon name="back"/>목록으로</a>{intake?.permissions?.canManage && <a className="intake-button" href={`#/lawyer/intakes/${id}/consultation`}>상태 · 상담 메모 관리<Icon name="arrow"/></a>}</div></div>
    {assignmentNotice && <p className="intake-save-notice" role="status">{assignmentNotice}</p>}
    {resource.status === 'loading' ? <div className="inquiry-empty" role="status"><span className="intake-spinner"/>문의 내용을 불러오는 중입니다.</div> : resource.error ? <Feedback error={resource.error} retry={resource.retry}/> : <div className="intake-detail-columns"><div className="detail-main"><DetailOverview intake={intake}/><DetailFacts intake={intake}/><DetailDocuments intake={intake}/></div><aside className="detail-aside" aria-label="접수 안내와 저장 자료"><section className="detail-card detail-client-guide"><Icon name="chat"/><div><h2>접수 안내 <small>(의뢰인용 안내 내용)</small></h2><p>{intake.clientGuideMessage}</p></div></section>{intake.permissions?.canAssign && <IntakeAssignment key={intake.id} api={api} intake={intake} reload={resource.retry} onSaved={data => setAssignmentNotice(data.assignedLawyerUsername ? '담당 변호사를 배정했습니다.' : '담당 배정을 해제했습니다.')}/>}<SearchCondition condition={intake.lawyerSearchCondition}/><Attachments key={intake.id} api={api} intake={intake} user={user} onLogin={onLogin}/>{intake.permissions?.canManage && <DetailMemos intake={intake}/>}</aside></div>}
  </main></LawyerShell>;
}
