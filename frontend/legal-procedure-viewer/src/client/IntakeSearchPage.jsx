import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Feedback, Steps, useResource } from './components.jsx';
import { consultationTime } from './consultationModel.js';
import { EMPTY_RESULT_FORM, RESULT_SOURCE_TYPES, SEARCH_STAGES, safeSearchUrl, searchRequestCopy, searchResultPayload, searchSourceLabel, searchSourceWarningLabel, searchStatusLabel, searchFailureLabel, searchRunning, sortSearchResults } from './searchModel.js';
import Icon from './Icon.jsx';

const policyLabel = value => value === 'KBA_NAVER_PUBLIC_WEB' ? '대한변협 공개 페이지 + 일반 공개 웹' : value;

// Keep acknowledged C08 data if the following GET fails, and hide old IDs immediately on navigation.
function useSelection(api, intakeId, requestId) {
  const key = String(requestId || '');
  const [state, setState] = useState({ key: '', loading: false, data: null, error: null });
  const controller = useRef(null);
  const reload = useCallback(async () => {
    controller.current?.abort();
    const current = new AbortController(); controller.current = current;
    if (!key) { setState({ key, loading: false, data: null, error: null }); return; }
    setState(old => ({ key, loading: true, data: old.key === key ? old.data : null, error: null }));
    try {
      const data = await api.searchRequest(intakeId, key, current.signal);
      if (!current.signal.aborted) setState({ key, loading: false, data, error: null });
      return data;
    } catch (error) {
      if (!current.signal.aborted) setState(old => ({ key, loading: false, data: old.key === key ? old.data : null, error }));
    }
  }, [api, intakeId, key]);
  useEffect(() => { reload(); return () => controller.current?.abort(); }, [reload]);
  useEffect(() => {
    if (state.key !== key || state.loading || state.error || !searchRunning(state.data)) return;
    const timer = setTimeout(reload, 1500);
    return () => clearTimeout(timer);
  }, [key, state, reload]);
  return { ...(state.key === key ? state : { loading: !!key, data: null, error: null }), reload,
    apply: data => { if (String(data.id) === key) setState({ key, loading: false, data, error: null }); } };
}

function ExecutionProgress({ request }) {
  if (!request.execution) return null;
  const { stage, errorCode, sourceWarningCode } = request.execution;
  return <section className={`search05-progress ${errorCode ? 'failed' : ''}`} aria-label="외부 검색 실행 상태" aria-live="polite">
    <strong>{searchStatusLabel(request.status)}</strong>
    <ol>{Object.entries(SEARCH_STAGES).filter(([key]) => key !== 'FINISHED').map(([key, label]) => <li key={key} aria-current={stage === key ? 'step' : undefined}>{label}</li>)}</ol>
    {searchRunning(request) && <p role="status"><span className="intake-spinner"/>{SEARCH_STAGES[stage]} 중입니다. 화면을 다시 열어 진행 상황을 확인할 수 있습니다.</p>}
    {errorCode && <p role="alert">{searchFailureLabel(errorCode)} {request.status === 'PARTIAL' ? '저장된 결과는 유지됩니다. 설명만 다시 생성할 수 있습니다.' : '다시 검색하려면 새 검색 요청을 기록해 주세요.'}</p>}
    {request.status === 'NO_RESULTS' && <p>조건에 맞는 공개 자료를 찾지 못했습니다. 검색 조건을 조정해 새 요청을 기록해 주세요.</p>}
    {sourceWarningCode && <p role="status">{searchSourceWarningLabel(sourceWarningCode)}</p>}
  </section>;
}

function SearchHistory({ api, intakeId, selectedId, revision, locked }) {
  const resource = useResource(signal => api.searchHistory(intakeId, signal), [api, intakeId, revision]);
  if (resource.status === 'loading') return <div className="intake-state" role="status"><span className="intake-spinner"/>검색 요청 이력을 불러오는 중입니다.</div>;
  if (resource.error) return <Feedback error={resource.error} retry={resource.retry}/>;
  if (!resource.data.length) return <p className="search05-empty">저장된 검색 요청이 없습니다. 위에서 첫 요청을 기록해 주세요.</p>;
  return <div className="search05-history-scroll" tabIndex={0} aria-label="검색 요청 이력 표"><table><colgroup><col/><col/><col/><col/></colgroup><thead><tr><th>요청 번호</th><th>생성일시</th><th>저장된 결과</th><th>검색 목적</th></tr></thead><tbody>{resource.data.map(item => <tr key={item.id} className={String(selectedId) === String(item.id) ? 'selected' : ''}><td><label><input type="radio" name="searchRequest" aria-label={`검색 요청 ${item.id} 선택`} checked={String(selectedId) === String(item.id)} disabled={locked} onChange={() => { window.location.hash = `/intake/${intakeId}/search/${item.id}`; }}/><span>SR-{item.id}</span></label></td><td><time>{consultationTime(item.createdAt)}</time></td><td><span className={`search05-count ${item.results.length ? 'has-results' : ''}`}>{item.results.length ? `${item.results.length}건` : '결과 없음'}</span></td><td>{item.searchPurpose}</td></tr>)}</tbody></table></div>;
}

function ResultLinkForm({ request, locked, saving, failure, onSave }) {
  const [form, setForm] = useState({ ...EMPTY_RESULT_FORM });
  const [error, setError] = useState(null);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const change = event => { setForm(old => ({ ...old, [event.target.name]: event.target.value })); setError(null); };
  async function submit(event) {
    event.preventDefault(); if (locked) return;
    try { searchResultPayload(form); } catch (invalid) { setError(invalid); return; }
    if (await onSave(form) && mounted.current) { setForm({ ...EMPTY_RESULT_FORM }); setError(null); }
  }
  const invalid = key => !!(error || failure)?.fieldErrors?.[key];
  return <details className="search05-link-form" open><summary><Icon name="document"/>결과 링크 기록 <small>(직접 입력)</small><Icon name="chevron"/></summary><form aria-label={`검색 요청 ${request.id} 결과 링크 기록`} onSubmit={submit} noValidate><fieldset disabled={locked}><div className="search05-link-fields"><label>출처 유형 <b>*</b><select name="sourceType" value={form.sourceType} onChange={change} aria-invalid={invalid('sourceType')}><option value="">선택하세요</option>{RESULT_SOURCE_TYPES.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><label>제목 <b>*</b><input name="title" value={form.title} maxLength={300} onChange={change} placeholder="자료 제목을 입력하세요." aria-invalid={invalid('title')}/></label><label>URL <b>*</b><input name="url" type="url" value={form.url} maxLength={1000} onChange={change} placeholder="https://" aria-invalid={invalid('url')}/></label></div><div className="search05-link-bottom"><label>짧은 설명 <small>(선택)</small><div className="search05-textarea"><textarea name="snippet" value={form.snippet} maxLength={1000} onChange={change} placeholder="이 자료의 주요 내용을 간단히 작성하세요." aria-invalid={invalid('snippet')}/><small>{form.snippet.length} / 1,000</small></div></label><div><label>표시 순서 <small>(선택)</small><input name="displayOrder" type="number" min="1" max="2147483647" step="1" value={form.displayOrder} onChange={change} placeholder="비우면 마지막에 추가" aria-invalid={invalid('displayOrder')}/></label><Button type="submit" className="secondary" busy={saving} disabled={locked}><Icon name="plus"/>{saving ? '결과 저장 중' : '이 결과 추가하기'}</Button></div></div></fieldset>{(error || failure) && <Feedback error={error || failure}/>}</form></details>;
}

function SelectedRequest({ request, conditionId, locked, saving, failure, onSave }) {
  const [sort, setSort] = useState('order');
  const [copied, setCopied] = useState(false);
  const [manualCopy, setManualCopy] = useState(false);
  async function copy() {
    try { await navigator.clipboard.writeText(searchRequestCopy(request)); setCopied(true); setManualCopy(false); }
    catch { setManualCopy(true); setCopied(false); }
  }
  return <><ExecutionProgress request={request}/><h2><Icon name="badge"/>선택한 검색 요청 상세 <span className="search05-count has-results">결과 {request.results.length}건</span></h2><section className="search05-request-info"><div className="search05-section-title"><h3>요청 정보</h3><button className="search05-small-button" type="button" onClick={copy}><Icon name="clipboard"/>{copied ? '복사 완료' : '요청 내용 복사'}</button></div><dl>{[['요청 번호', `SR-${request.id}`], ['생성일시', consultationTime(request.createdAt)], ['상태', searchStatusLabel(request.status)], ['검색 목적', request.searchPurpose], ['검색 질의어', request.searchQuery], ['공개 정보 출처', policyLabel(request.sourcePolicy)]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>{manualCopy && <label className="search05-copy-fallback">아래 내용을 선택해 복사해 주세요.<textarea readOnly value={searchRequestCopy(request)}/></label>}{conditionId && String(conditionId) !== String(request.conditionId) && <p className="field-hint">이 요청은 이전에 저장된 조건 #{request.conditionId}를 사용합니다.</p>}</section><div className="search05-section-title search05-results-heading"><h2><Icon name="badge"/>저장된 검색 결과 <small>({request.results.length}건)</small></h2><label className="search05-sort"><span className="search05-sr-only">결과 정렬</span><select aria-label="결과 정렬" value={sort} onChange={event => setSort(event.target.value)}><option value="order">표시 순서순</option><option value="newest">최근 기록순</option></select></label></div>{request.results.length ? <ol className="search05-results">{sortSearchResults(request.results, sort).map((item, index) => { const url = safeSearchUrl(item.url); return <li key={item.id}><span className="search05-result-number">{index + 1}</span><div><div className="search05-result-heading"><h3>{item.title}</h3><span>{searchSourceLabel(item.sourceType, item.url)}</span><time>기록 {consultationTime(item.createdAt)}</time></div>{item.snippet && <p>{item.snippet}</p>}{item.explanation && <div className="search05-ai-explanation"><strong>AI 관련성 설명 · 원문 확인 필요</strong><p>{item.explanation}</p></div>}{url ? <a href={url} target="_blank" rel="noopener noreferrer">{item.url}<Icon name="external"/></a> : <p className="search05-unsafe-url">연결할 수 없는 URL: {item.url}</p>}</div></li>; })}</ol> : <p className="search05-empty">저장된 결과 링크가 없습니다. 확인한 공개 자료를 아래에서 직접 기록할 수 있습니다.</p>}<ResultLinkForm key={request.id} request={request} locked={locked} saving={saving} failure={failure} onSave={onSave}/></>;
}

export default function IntakeSearchPage({ api, id, requestId, user, onLogin }) {
  const resource = useResource(signal => api.intake(id, signal), [api, id]);
  const selection = useSelection(api, id, requestId);
  const [purpose, setPurpose] = useState('');
  const [pending, setPending] = useState('');
  const [failure, setFailure] = useState(null);
  const [notice, setNotice] = useState('');
  const [historyRevision, setHistoryRevision] = useState(0);
  const lastExecutionState = useRef('');
  useEffect(() => {
    const state = `${requestId}:${selection.data?.status}`;
    if (lastExecutionState.current !== state && selection.data?.execution && !searchRunning(selection.data)) setHistoryRevision(value => value + 1);
    lastExecutionState.current = state;
  }, [requestId, selection.data?.status, selection.data?.execution]);
  const [review, setReview] = useState(null);
  const writer = useRef(null);
  const mounted = useRef(true);
  const currentId = useRef(requestId); currentId.current = requestId;
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; writer.current?.abort(); }; }, []);
  const intake = resource.data;
  const condition = intake?.lawyerSearchCondition;
  async function save(kind, form) {
    if (writer.current || review) return false;
    if (kind === 'create' && (!condition || purpose.length > 300)) { setFailure({ kind, error: new Error('저장된 검색 조건과 검색 목적 300자 제한을 확인해 주세요.') }); return false; }
    const controller = new AbortController(); writer.current = controller;
    const targetId = requestId;
    setPending(kind); setFailure(null); setNotice('');
    try {
      const data = kind === 'create' ? await api.createSearch(id, purpose, controller.signal, condition.sourcePolicy) : await api.addSearchResult(id, targetId, form, controller.signal);
      if (controller.signal.aborted || !mounted.current) return false;
      setNotice(kind === 'create' ? '검색 요청을 기록했습니다. 외부 검색은 실행되지 않았습니다.' : '결과 링크를 저장했습니다.');
      setHistoryRevision(value => value + 1);
      if (kind === 'create') { setPurpose(''); window.location.hash = `/intake/${id}/search/${data.id}`; }
      else if (String(currentId.current) === String(targetId)) { selection.apply(data); await selection.reload(); }
      return true;
    } catch (error) {
      if (!controller.signal.aborted && mounted.current) {
        setFailure({ kind, error });
        if (!error.status || error.status >= 500) setReview({ kind, requestId: targetId, checked: false });
      }
      return false;
    } finally { if (writer.current === controller) { writer.current = null; if (mounted.current) setPending(''); } }
  }
  async function checkLatest() {
    if (writer.current || !review) return;
    if (review.kind === 'create' && !user) { onLogin(); return; }
    const controller = new AbortController(); writer.current = controller; setPending('review');
    try {
      if (review.kind === 'create') await api.searchHistory(id, controller.signal);
      else {
        const data = await api.searchRequest(id, review.requestId, controller.signal);
        if (!controller.signal.aborted && String(currentId.current) === String(review.requestId)) selection.apply(data);
      }
      if (!controller.signal.aborted && mounted.current) { setHistoryRevision(value => value + 1); setFailure(null); }
    } catch (error) { if (!controller.signal.aborted && mounted.current) setFailure({ kind: review.kind, error }); }
    finally {
      if (writer.current === controller) { writer.current = null; if (mounted.current) { setPending(''); setReview(old => old ? { ...old, checked: true } : old); } }
    }
  }
  async function execute() {
    if (writer.current || review || !selection.data || searchRunning(selection.data)) return;
    if (!user) { onLogin(); return; }
    const targetId = requestId;
    const controller = new AbortController(); writer.current = controller;
    setPending('execute'); setFailure(null); setNotice('');
    try {
      const data = await api.executeSearch(id, targetId, selection.data.status === 'PARTIAL', controller.signal);
      if (controller.signal.aborted || !mounted.current) return;
      if (String(currentId.current) === String(targetId)) selection.apply(data);
      setHistoryRevision(value => value + 1);
      setNotice('검색 실행 상태를 확인했습니다. 진행 중인 작업은 자동으로 갱신됩니다.');
    } catch (error) {
      if (!controller.signal.aborted && mounted.current) {
        setFailure({ kind: 'execute', error });
        if (!error.status || error.status >= 500 && error.status !== 503) setReview({ kind: 'execute', requestId: targetId, checked: false });
      }
    } finally { if (writer.current === controller) { writer.current = null; if (mounted.current) setPending(''); } }
  }
  const locked = !!pending || !!review;
  return <main className="intake-shell search05-shell"><div className="search05-layout"><div className="search05-main"><Steps current={4}/><section className="intake-panel search05-panel"><header className="search05-intro"><span><Icon name="search"/></span><div><h1>변호사 탐색을 위한 조건을 확인하세요.</h1><p>저장된 사건 내용을 바탕으로 변호사 탐색에 사용할 조건을 확인하고, 검색 요청을 기록할 수 있습니다.</p></div></header>{resource.status === 'loading' ? <div className="intake-state" role="status"><span className="intake-spinner"/>접수된 문의와 검색 조건을 불러오는 중입니다.</div> : resource.error ? <Feedback error={resource.error} retry={resource.retry}/> : <><section className="search05-intake-info"><div className="search05-section-title"><h2><Icon name="document"/>접수된 문의 정보</h2><a className={`search05-small-button ${pending ? 'disabled' : ''}`} href={pending ? undefined : `#/intake/${id}/review`} aria-disabled={!!pending}><Icon name="clipboard"/>문의 내용 보기</a></div><dl><div><dt>문의 번호</dt><dd>INQ-{intake.id}</dd></div><div><dt>상대방</dt><dd>{intake.counterpartMasked || '미입력'}</dd></div><div><dt>사건 유형</dt><dd>{condition?.legalField || intake.caseTypeLabel} <span className="search05-pill">{condition?.disputeType || intake.disputeArea}</span></dd></div><div><dt>지역</dt><dd>{condition?.preferredRegion || intake.preferredRegion || '미입력'}</dd></div></dl></section><h2 className="search05-subheading"><Icon name="document"/>변호사 탐색 조건 <small>(저장된 내용)</small></h2>{condition ? <><dl className="search05-conditions">{[['scales', '법률 분야', condition.legalField], ['document', '분쟁 유형', condition.disputeType], ['pin', '선호 지역', condition.preferredRegion], ['building', '사무소 유형', condition.officeType], ['user', '경력 선호', condition.experienceLevel]].map(([icon, label, value]) => <div key={label}><Icon name={icon}/><div><dt>{label}</dt><dd title={value}>{value}</dd></div></div>)}</dl><div className="search05-query"><span>저장된 검색 질의어</span><p>{condition.searchQuery}</p></div><form className="search05-create-form" aria-label="검색 요청 추가" onSubmit={event => { event.preventDefault(); return save('create'); }} noValidate><h2><Icon name="badge"/>검색 요청 추가</h2><fieldset disabled={locked}><div className="search05-create-fields"><label htmlFor="search-purpose">검색 목적 <small>(선택)</small><div className="search05-textarea"><textarea id="search-purpose" name="searchPurpose" value={purpose} maxLength={300} onChange={event => { setPurpose(event.target.value); if (!review) setFailure(null); }} placeholder="예) 서울 지역 대여금 사건 경험이 있는 변호사를 찾고 싶습니다."/><small>{purpose.length} / 300</small></div></label><label>공개 정보 출처 정책<select aria-label="공개 정보 출처 정책" value={condition.sourcePolicy} onChange={() => {}}><option value={condition.sourcePolicy}>{policyLabel(condition.sourcePolicy)}</option></select></label></div><div className="search05-create-actions"><Button type="submit" busy={pending === 'create'} disabled={locked}><Icon name="document"/>{pending === 'create' ? '검색 요청 저장 중' : '검색 요청 기록하기'}</Button><Button type="button" className="search05-execute" busy={pending === 'execute' || searchRunning(selection.data)} disabled={locked || !selection.data || selection.loading || !!selection.error || !['PREVIEW_READY', 'PARTIAL'].includes(selection.data?.status)} onClick={execute}><Icon name="search"/>{pending === 'execute' || searchRunning(selection.data) ? '외부 검색 진행 중' : selection.data?.status === 'PARTIAL' ? '저장된 결과 설명 다시 생성' : '외부 검색 실행'}</Button></div></fieldset><p className="field-hint">공개 웹 검색으로 변협 공개 페이지와 일반 웹 자료를 함께 찾습니다. 문의 원문·이름·연락처는 외부 검색에 보내지 않습니다. <a href="https://www.klaw.or.kr/" target="_blank" rel="noopener noreferrer">변협에서 직접 확인 ↗</a></p>{['create', 'execute'].includes(failure?.kind) && <Feedback error={failure.error}/>}</form></> : <p className="search05-empty">이 문의에 저장된 검색 조건이 없어 검색 요청을 생성할 수 없습니다.</p>}</>}{notice && <p className="search05-notice" role="status"><Icon name="circleCheck"/>{notice}</p>}{review && <div className="search05-review" role="alert"><p>서버의 저장 여부를 확인할 수 없습니다. 다시 저장하기 전에 최신 이력·결과에서 중복 여부를 확인해 주세요.</p><Button type="button" className="secondary" disabled={!!pending} onClick={checkLatest}>{pending === 'review' ? '최신 정보 확인 중' : '최신 저장 정보 조회'}</Button>{review.checked && <Button type="button" className="secondary" disabled={!!pending} onClick={() => { setReview(null); setFailure(null); }}>중복 가능성 확인 후 편집 계속</Button>}</div>}<section className="search05-history"><div className="search05-section-title"><div className="search05-history-title"><h2><Icon name="history"/>검색 요청 이력</h2><p>이전 요청을 선택하면 저장된 조건과 결과를 확인할 수 있습니다.</p></div>{user && <button className="search05-small-button" type="button" disabled={locked} onClick={() => setHistoryRevision(value => value + 1)}><Icon name="refresh"/>새로고침</button>}</div>{user ? <SearchHistory key={user.username} api={api} intakeId={id} selectedId={requestId} revision={historyRevision} locked={locked}/> : <div className="search05-login"><p>이력 조회는 문의 소유자 또는 관리자 로그인이 필요합니다.</p><Button type="button" className="secondary" onClick={onLogin} disabled={!!pending}>로그인하여 이력 보기</Button></div>}</section></section></div><aside className="intake-panel search05-aside" aria-label="선택한 검색 요청"><div className="search05-aside-content">{!requestId ? <div className="search05-empty-selection"><Icon name="search"/><h2>검색 요청을 선택해 주세요.</h2><p>새 요청을 기록하거나 이력에서 요청을 선택하면 상세 정보와 저장된 결과가 표시됩니다.</p></div> : <>{selection.loading && <div className="intake-state" role="status"><span className="intake-spinner"/>선택한 검색 요청을 불러오는 중입니다.</div>}{selection.error && <><Feedback error={selection.error} retry={selection.reload}/>{selection.data && <p className="field-hint">마지막으로 확인한 저장 결과입니다. 최신 정보 조회를 다시 시도해 주세요.</p>}</>}{selection.data && <SelectedRequest key={selection.data.id} request={selection.data} conditionId={condition?.id} locked={locked || selection.loading || searchRunning(selection.data)} saving={pending === 'result'} failure={failure?.kind === 'result' ? failure.error : null} onSave={form => save('result', form)}/>}</>}</div><footer className="search05-footer"><a className={`intake-button secondary ${pending ? 'disabled' : ''}`} aria-disabled={!!pending} href={pending ? undefined : `#/intake/${id}`}><Icon name="back"/>이전: 안내 확인</a><p><Icon name="info"/>공개 검색·직접 기록한 참고 자료입니다. AI 설명은 제목·요약을 바탕으로 생성되며, 자격·경력·법률 판단은 원문에서 확인해 주세요.</p></footer></aside></div></main>;
}
