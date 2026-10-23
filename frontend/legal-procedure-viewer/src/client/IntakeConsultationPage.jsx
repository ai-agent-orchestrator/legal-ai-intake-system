import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Feedback } from './components.jsx';
import { LawyerShell } from './IntakeListPage.jsx';
import { CASE_TYPES } from './model.js';
import { LIST_STATUSES } from './intakeListModel.js';
import { allowedStatuses, consultationMemoPayload, consultationTime, FOLLOW_UP_ITEMS, recentMemos } from './consultationModel.js';
import Icon from './Icon.jsx';
import { prepareWriteAttempt, validVersion } from '../shared/writeContracts.js';

export default function IntakeConsultationPage({ api, id, onReady }) {
  const [record, setRecord] = useState({ data: null, loading: true, error: null });
  const [nextStatus, setNextStatus] = useState('');
  const [content, setContent] = useState('');
  const [followUps, setFollowUps] = useState([]);
  const [pending, setPending] = useState('');
  const [failure, setFailure] = useState(null);
  const [notice, setNotice] = useState('');
  const [review, setReview] = useState(null);
  const [history, setHistory] = useState([]);
  const read = useRef(null);
  const write = useRef(null);
  const memoAttempt = useRef(null);
  const mounted = useRef(false);
  const load = useCallback(async () => {
    read.current?.abort();
    const controller = new AbortController(); read.current = controller;
    setRecord(current => ({ ...current, loading: true, error: null }));
    try {
      const data = await api.consultationIntake(id, controller.signal);
      if (controller.signal.aborted) return null;
      setRecord({ data, loading: false, error: null });
      return data;
    } catch (error) {
      if (!controller.signal.aborted) {
        const denied = [401, 403].includes(error.status);
        setRecord(current => ({ ...current, data: denied ? null : current.data, loading: false, error }));
        if (denied) { memoAttempt.current = null; setContent(''); setFollowUps([]); setNextStatus(''); setReview(null); }
      }
      return null;
    }
  }, [api, id]);
  useEffect(() => {
    mounted.current = true; load();
    return () => { mounted.current = false; read.current?.abort(); write.current?.abort(); };
  }, [load]);
  const intake = record.data;
  const choices = allowedStatuses(intake?.status);
  const versionReady = validVersion(intake?.version);
  const locked = !!pending || record.loading || !!review || !versionReady;

  async function reload() {
    if (write.current) return;
    const data = await load();
    if (data && validVersion(data.version) && mounted.current) setReview(current => current ? { ...current, checked: true } : null);
  }
  async function save(kind, event) {
    event.preventDefault();
    if (write.current || locked || !intake) return;
    setFailure(null); setNotice('');
    try {
      if (kind === 'status' && (!nextStatus || !choices.includes(nextStatus))) {
        const error = new Error('변경할 상태를 선택해 주세요.'); error.fieldErrors = { status: error.message }; throw error;
      }
      if (kind === 'memo') memoAttempt.current = prepareWriteAttempt(memoAttempt.current, String(id), consultationMemoPayload(content, followUps));
    } catch (error) { setFailure({ kind, error }); return; }
    const controller = new AbortController(); write.current = controller; setPending(kind);
    try {
      if (kind === 'memo') memoAttempt.current.uncertain = true;
      const result = kind === 'status' ? await api.updateStatus(id, nextStatus, controller.signal, { expectedVersion: intake.version })
        : await api.createMemo(id, content, followUps, controller.signal, { expectedVersion: intake.version, idempotencyKey: memoAttempt.current.key });
      if (controller.signal.aborted || !mounted.current) return;
      if (kind === 'status') {
        setRecord(current => ({ ...current, data: { ...current.data, status: result.status, version: result.version } }));
        if (result.changed) setHistory(current => [{ ...result, observedAt: new Date().toISOString() }, ...current].slice(0, 5));
        setNextStatus('');
        setNotice(result.changed ? '상태 변경을 저장했습니다.' : '현재와 같은 상태입니다. 상태는 변경되지 않았습니다.');
      } else {
        setRecord(current => ({ ...current, data: { ...current.data, version: result.intakeVersion, consultationMemos: [result, ...current.data.consultationMemos.filter(memo => String(memo.id) !== String(result.id))] } }));
        memoAttempt.current = null;
        setContent(''); setFollowUps([]); setNotice(result.replayed ? '기존에 저장된 메모를 확인했습니다. 중복으로 추가하지 않았습니다.' : '메모를 추가 저장했습니다.');
      }
      // The write already succeeded. A failed read must not become a failed save or resend.
      await load();
    } catch (error) {
      if ([401, 403].includes(error.status) && !controller.signal.aborted) {
        memoAttempt.current = null; setReview(null);
        setRecord({ data: null, loading: false, error }); setContent(''); setFollowUps([]); setNextStatus('');
      }
      if (controller.signal.aborted || !mounted.current) return;
      if (kind === 'memo' && memoAttempt.current && error.status && error.status < 500) memoAttempt.current.uncertain = false;
      setFailure({ kind, error });
      if (!error.status || error.status >= 500) setReview({ kind, checked: false });
      else if (error.status === 409) {
        setReview({ kind, checked: false, conflict: true, keyConflict: error.code === 'IDEMPOTENCY_KEY_REUSED' });
        const latest = await load();
        if (latest && validVersion(latest.version) && mounted.current) setReview(current => current ? { ...current, checked: true } : null);
      }
    } finally {
      if (mounted.current && !controller.signal.aborted) { setPending(''); write.current = null; }
    }
  }
  const statusLabel = LIST_STATUSES[intake?.status] || '확인 중';
  return <LawyerShell detail onReady={onReady}><main className="inquiry-panel consultation-panel">
    <div className="consultation-heading"><div><nav aria-label="현재 위치" className="consultation-breadcrumb"><a href="#/lawyer/intakes">문의 목록</a><Icon name="chevron"/><a href={`#/lawyer/intakes/${id}`}>문의 상세</a><Icon name="chevron"/><span>상태 변경 · 상담 메모</span></nav><h1>문의 #{id} 상태 변경 · 상담 메모 {intake && <span className={`inquiry-status status-${intake.status.toLowerCase()}`}>{statusLabel}</span>}</h1><p>접수일시 <time>{intake ? consultationTime(intake.createdAt) : '확인 중'}</time></p></div><a className="intake-button secondary consultation-return" href={`#/lawyer/intakes/${id}`}><Icon name="back"/>문의 상세로 돌아가기</a></div>
    {!intake ? record.error ? <Feedback error={record.error} retry={reload}/> : <div className="inquiry-empty" role="status"><span className="intake-spinner"/>문의 내용을 불러오는 중입니다.</div> : <>
      <section className="consultation-summary" aria-label="문의 요약"><dl><div><dt>사건 유형</dt><dd><span className="inquiry-type">{CASE_TYPES.find(item => item.value === intake.caseType)?.label}</span><span>{intake.disputeArea || '분쟁 영역 미정'}</span></dd></div><div><dt>당사자 위치</dt><dd>{intake.partyPosition}</dd></div></dl><dl><div><dt>분쟁 영역</dt><dd>{intake.disputeArea || '분쟁 영역 미정'}</dd></div><div><dt>상대방</dt><dd>{intake.counterpartMasked || '입력하지 않음'}</dd></div></dl><dl><div><dt>핵심 내용</dt><dd title={intake.coreStatementMasked}>{intake.coreStatementMasked}</dd></div><div><dt>원하는 다음 단계</dt><dd title={intake.desiredNextStep}>{intake.desiredNextStep}</dd></div></dl></section>
      {notice && <p className="consultation-success" role="status"><Icon name="circleCheck"/>{notice}</p>}
      {record.loading && <p className="consultation-sync" role="status"><span className="intake-spinner"/>최신 내용을 확인하고 있습니다.</p>}
      {record.error && <div className="consultation-sync-error"><p>{notice ? '저장은 완료됐지만 최신 내용을 다시 조회하지 못했습니다.' : '최신 내용을 조회하지 못했습니다. 기존에 확인한 내용을 표시합니다.'}</p><Feedback error={record.error} retry={reload}/></div>}
      {!versionReady && <div className="consultation-review" role="alert"><p>서버의 문의 버전 정보를 확인하지 못해 저장을 잠갔습니다. 최신 서버 연결과 조회 결과를 확인해 주세요.</p><Button type="button" className="secondary" disabled={record.loading} onClick={reload}>최신 문의 다시 조회</Button></div>}
      {review && <div className="consultation-review" role="alert"><p>{review.conflict ? '저장 요청이 충돌했습니다. 최신 상태를 확인한 뒤 다시 저장해 주세요. 입력한 메모와 후속 업무는 유지했습니다.' : '저장 결과를 확인하지 못했습니다. 중복 저장을 피하기 위해 최신 내용을 먼저 확인해 주세요. 입력한 메모는 유지했습니다.'}</p><Button type="button" className="secondary" disabled={record.loading} onClick={reload}>저장 결과 조회</Button>{review.checked && <><p>최신 상태와 저장된 메모를 확인한 뒤 편집을 계속할 수 있습니다. 저장 요청을 자동으로 다시 보내지 않습니다.</p><Button type="button" className="secondary" onClick={() => { if (review.keyConflict) memoAttempt.current = null; else if (memoAttempt.current) memoAttempt.current.uncertain = false; setReview(null); setFailure(null); }}>확인 후 편집 계속</Button></>}</div>}
      <div className="consultation-columns"><section className="consultation-card consultation-state"><h2><Icon name="document"/>문의 상태</h2><div className={`consultation-current current-${intake.status.toLowerCase()}`}><b>현재 상태</b><span className="consultation-current-icon"><Icon name={intake.status === 'CONSULTATION_READY' ? 'people' : 'clipboard'}/></span><div><span className={`inquiry-status status-${intake.status.toLowerCase()}`}>{statusLabel}</span><p>현재 조회된 상태입니다.</p></div></div>
        <form onSubmit={event => save('status', event)} noValidate><div className="consultation-status-field"><label htmlFor="consultation-status">다음 상태 선택 <em>*</em></label><select id="consultation-status" name="nextStatus" value={nextStatus} disabled={locked} aria-invalid={!!failure?.error.fieldErrors?.status} onChange={event => setNextStatus(event.target.value)}><option value="">상태를 선택하세요.</option>{choices.map(status => <option key={status} value={status}>{LIST_STATUSES[status]}{status === intake.status ? ' (현재와 동일)' : ''}</option>)}</select></div><div className="consultation-status-options"><b>현재 상태({statusLabel})에서 변경 가능한 상태</b><div>{choices.map(status => <label key={status}><input type="radio" name="statusChoice" value={status} checked={nextStatus === status} disabled={locked} onChange={() => setNextStatus(status)}/>{LIST_STATUSES[status]}{status === intake.status ? ' (현재와 동일)' : ''}</label>)}</div></div>{failure?.kind === 'status' && <Feedback error={failure.error}/>}<Button type="submit" className="secondary consultation-status-save" disabled={locked} busy={pending === 'status'}><Icon name="document"/>상태 변경 저장</Button></form>
        <div className="consultation-note"><Icon name="info"/><p>문의의 업무 진행 상태를 변경합니다.<br/>종료된 문의는 다른 상태로 변경할 수 없습니다.</p></div><section className="consultation-history"><h3><Icon name="history"/>이 화면에서 확인한 변경 <small>(최근 5건)</small></h3>{history.length ? <ul>{history.map((item, index) => <li key={index}><time>{new Date(item.observedAt).toLocaleString('ko-KR')}</time><span>{LIST_STATUSES[item.previousStatus]} → <b>{LIST_STATUSES[item.status]}</b></span></li>)}</ul> : <p className="consultation-empty">아직 이 화면에서 확인한 상태 변경이 없습니다.</p>}<p className="consultation-history-note">저장 응답을 확인한 시각입니다. 과거 변경 이력은 제공되지 않으며, 새로고침하면 이 표시는 초기화됩니다.</p></section>
      </section><section className="consultation-card consultation-memo"><h2><Icon name="memo"/>상담 준비 메모</h2><form onSubmit={event => save('memo', event)} noValidate><label className="visually-hidden" htmlFor="consultation-content">상담 준비 메모</label><div className="consultation-textarea"><textarea id="consultation-content" name="memoContent" value={content} maxLength={4000} disabled={locked} aria-invalid={!!failure?.error.fieldErrors?.content} onChange={event => setContent(event.target.value)} placeholder="상담 준비 내용, 검토 사항, 추가 확인이 필요한 내용을 입력하세요."/><span>{content.length.toLocaleString('ko-KR')} / 4,000</span></div><div className="consultation-memo-tools"><fieldset disabled={locked}><legend>후속 업무 체크 <small>(선택, 최대 4개)</small></legend><div>{FOLLOW_UP_ITEMS.map(item => <label key={item.value}><input type="checkbox" value={item.value} checked={followUps.includes(item.value)} onChange={() => setFollowUps(current => current.includes(item.value) ? current.filter(value => value !== item.value) : [...current, item.value])}/>{item.label}</label>)}</div></fieldset><Button type="submit" disabled={locked} busy={pending === 'memo'}><Icon name="save"/>메모 추가 저장</Button></div>{failure?.kind === 'memo' && <Feedback error={failure.error}/>}</form>
        <div className="consultation-note"><Icon name="info"/><p>선택 항목은 참고용으로 저장되며, 알림 전송이나 일정 생성은 하지 않습니다.<br/>내부 메모와 후속 업무는 담당 변호사·사무소 관리자에게만 공개되며 의뢰인에게 전달되지 않습니다.</p></div><section className="consultation-saved"><h3>저장된 상담 메모 <small>(최근 5건)</small></h3>{intake.consultationMemos.length ? <ul>{recentMemos(intake.consultationMemos).map(memo => <li key={memo.id}><div className="consultation-memo-author"><Icon name="user"/><div><b>{memo.createdBy || '기존 작성자 미기록'} · 상담 메모 #{memo.id}</b><time dateTime={memo.createdAt}>{consultationTime(memo.createdAt)}</time></div></div><div className="consultation-memo-body"><p>{memo.contentMasked}</p>{!!memo.followUpItems.length && <div>{memo.followUpItems.map(value => <span key={value}>{FOLLOW_UP_ITEMS.find(item => item.value === value)?.label}</span>)}</div>}</div></li>)}</ul> : <p className="consultation-empty">저장된 상담 메모가 없습니다.</p>}</section>
      </section></div>
    </>}
  </main></LawyerShell>;
}
