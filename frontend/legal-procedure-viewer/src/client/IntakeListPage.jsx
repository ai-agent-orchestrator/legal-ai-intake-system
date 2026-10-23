import { useState } from 'react';
import { Button, Feedback, useResource } from './components.jsx';
import { CASE_TYPES } from './model.js';
import { declaredDocumentCount, EMPTY_LIST_FILTERS, filterIntakes, intakeDateParts, LIST_STATUSES, listCounts, listFilterError } from './intakeListModel.js';
import Icon from './Icon.jsx';

export function LawyerShell({ children, onReady, detail = false, user }) {
  if (user?.role === 'ROLE_USER') return <div className="lawyer-workspace"><aside className="lawyer-sidebar" aria-label="의뢰인 문의 메뉴"><nav><a href="#/lawyer/intakes" className="active"><Icon name="document"/>나의 상담</a><a href="#/intake/type"><Icon name="user"/>새 문의 작성</a><a href="#/account"><Icon name="user"/>내 계정</a></nav></aside>{children}</div>;
  return <div className="lawyer-workspace"><aside className="lawyer-sidebar" aria-label="변호사 업무 메뉴"><nav>
    <span className="lawyer-unavailable" title="변호사 홈은 준비 중입니다."><Icon name="home"/>변호사 홈 <small>준비 중</small></span>
    <a href="#/lawyer/intakes" className="active" aria-current={detail ? undefined : 'page'}><Icon name="document"/>문의 목록</a>
    {onReady ? <button type="button" onClick={onReady}><Icon name="calendar"/>상담 준비</button> : <a href="#/lawyer/intakes"><Icon name="calendar"/>상담 준비</a>}
    <span className="lawyer-unavailable" title="자료 관리는 준비 중입니다."><Icon name="folder"/>자료 관리 <small>준비 중</small></span>
    <span className="lawyer-unavailable" title="내 정보는 준비 중입니다."><Icon name="user"/>내 정보 <small>준비 중</small></span>
  </nav></aside>{children}</div>;
}

function ListStats({ counts }) {
  return <div className="inquiry-stats" aria-label="조회한 전체 목록 통계">{[
    ['total', 'document', '조회된 문의', 'blue'], ['pending', 'clock', '검토 대기', 'amber'],
    ['needsInfo', 'alert', '보완 필요', 'red'], ['ready', 'people', '상담 준비', 'green'],
  ].map(([key, icon, label, color]) => <section key={key} className={`inquiry-stat ${color}`}><span><Icon name={icon}/></span><div><h2>{label}</h2><strong>{counts ? `${counts[key]}건` : '—'}</strong></div></section>)}</div>;
}

function ListFilters({ filters, setFilters, items, error }) {
  const update = event => setFilters(current => ({ ...current, [event.target.name]: event.target.value }));
  const steps = [...new Set(items.map(item => item.desiredNextStep))].sort((a, b) => a.localeCompare(b, 'ko'));
  if (filters.desiredNextStep && !steps.includes(filters.desiredNextStep)) steps.push(filters.desiredNextStep);
  return <section className="inquiry-filters" aria-label="문의 목록 필터"><div className="inquiry-filter-grid">
    <label>사건 유형<select name="caseType" value={filters.caseType} onChange={update}><option value="">전체</option>{CASE_TYPES.map(type => <option key={type.value} value={type.value}>{type.label}</option>)}</select></label>
    <label>현재 상태<select name="status" value={filters.status} onChange={update}><option value="">전체</option>{Object.entries(LIST_STATUSES).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
    <label>원하는 다음 단계<select name="desiredNextStep" value={filters.desiredNextStep} onChange={update}><option value="">전체</option>{steps.map(step => <option key={step} value={step}>{step}</option>)}</select></label>
    <fieldset className="inquiry-period"><legend>기간 <span>(접수 시각)</span></legend><div><Icon name="calendar"/><input aria-label="접수 시작일" type="date" name="startDate" value={filters.startDate} onChange={update} aria-invalid={!!error} aria-describedby={error ? 'inquiry-filter-error' : undefined}/><span>~</span><input aria-label="접수 종료일" type="date" name="endDate" value={filters.endDate} onChange={update} aria-invalid={!!error} aria-describedby={error ? 'inquiry-filter-error' : undefined}/></div></fieldset>
    <label className="inquiry-query">검색어<div><Icon name="search"/><input type="search" name="query" value={filters.query} onChange={update} maxLength={100} placeholder="이름, 사건 내용, 상대방 등으로 검색"/></div></label>
  </div>{error && <p id="inquiry-filter-error" className="inquiry-filter-error" role="alert">{error}</p>}</section>;
}

function IntakeRows({ items }) {
  return <tbody>{items.map(item => {
    const date = intakeDateParts(item.createdAt);
    return <tr key={item.id}><td className="inquiry-id">No. {item.id}</td><td><time dateTime={item.createdAt}>{date.date}<br/>{date.time}</time></td>
      <td><span className="inquiry-type">{CASE_TYPES.find(type => type.value === item.caseType)?.label || item.caseTypeLabel}</span><span className="inquiry-dispute" title={item.disputeArea || ''}>{item.disputeArea || '분쟁 영역 미정'}</span></td>
      <td className="inquiry-preview"><p title={item.coreStatementMasked}>{item.coreStatementMasked}</p><small title={`의뢰인: ${item.clientNameMasked || '-'} · 상대방: ${item.counterpartMasked || '-'}`}>의뢰인: {item.clientNameMasked || '-'}<i/>상대방: {item.counterpartMasked || '-'}</small></td>
      <td className="inquiry-document-count">{declaredDocumentCount(item)}</td><td className="inquiry-next" title={item.desiredNextStep}>{item.desiredNextStep}</td>
      <td><span className={`inquiry-status status-${item.status.toLowerCase().replaceAll(/[^a-z_]/g, '')}`}>{LIST_STATUSES[item.status] || '알 수 없는 상태'}</span></td>
      <td><a className="inquiry-detail-link" href={`#/lawyer/intakes/${item.id}`} aria-label={`문의 ${item.id} 상세 보기`}>상세 보기<Icon name="arrow"/></a></td></tr>;
  })}</tbody>;
}

export default function IntakeListPage({ api, filters, setFilters, user }) {
  const resource = useResource(async signal => ({ items: await api.intakes(signal), receivedAt: new Date() }), [api]);
  const [descending, setDescending] = useState(true);
  const items = resource.data?.items || [];
  const error = listFilterError(filters);
  const filtered = filterIntakes(items, filters, descending);
  const hasFilters = Object.values(filters).some(Boolean);
  const ready = resource.status === 'success';
  const stamp = resource.data?.receivedAt;
  const stampText = stamp ? `${stamp.getFullYear()}. ${String(stamp.getMonth() + 1).padStart(2, '0')}. ${String(stamp.getDate()).padStart(2, '0')}. ${String(stamp.getHours()).padStart(2, '0')}:${String(stamp.getMinutes()).padStart(2, '0')}` : '조회 전';
  return <LawyerShell user={user} onReady={() => setFilters({ ...EMPTY_LIST_FILTERS, status: 'CONSULTATION_READY' })}><main className="inquiry-panel" id="inquiry-list">
    <div className="inquiry-heading"><div><p className="inquiry-eyebrow">문의 관리</p><h1>{user?.role === 'ROLE_USER' ? '나의 문의' : '접수된 문의'}</h1><p>본인 또는 배정된 문의만 최대 50건 조회합니다. 사무소 관리자는 전체 문의를 확인합니다.</p></div><div className="inquiry-refresh"><span><Icon name="clock"/>조회 시각 <time>{stampText}</time></span><Button type="button" className="secondary" onClick={resource.retry} disabled={resource.status === 'loading'} busy={resource.status === 'loading'}><Icon name="refresh"/>새로고침</Button></div></div>
    <ListStats counts={ready ? listCounts(items) : null}/><ListFilters filters={filters} setFilters={setFilters} items={items} error={error}/>
    {resource.status === 'loading' ? <div className="inquiry-empty" aria-busy="true"><span className="intake-spinner"/>접수된 문의를 확인하고 있습니다.</div>
      : resource.error ? <Feedback error={resource.error} retry={resource.retry}/>
        : <><div className="inquiry-table-scroll" tabIndex="0" role="region" aria-label="접수 문의 표, 좁은 화면에서는 좌우로 스크롤할 수 있습니다"><table className="inquiry-table"><caption className="visually-hidden">최근 접수 문의 목록</caption><colgroup>{['11%', '10%', '14%', '23%', '8%', '13%', '11%', '10%'].map((width, index) => <col key={index} style={{ width }}/>)}</colgroup><thead><tr><th scope="col">문의 번호</th><th scope="col" aria-sort={descending ? 'descending' : 'ascending'}><button type="button" onClick={() => setDescending(value => !value)}>접수 시각 <Icon name={descending ? 'down' : 'up'}/><span className="visually-hidden">{descending ? '오름차순으로 정렬' : '내림차순으로 정렬'}</span></button></th><th scope="col">사건 유형 · 분쟁 영역</th><th scope="col">핵심 내용 미리보기</th><th scope="col">보유 자료 수</th><th scope="col">원하는 다음 단계</th><th scope="col">상태</th><th scope="col">작업</th></tr></thead><IntakeRows items={filtered}/></table></div>
        {!filtered.length && <div className="inquiry-empty"><Icon name={hasFilters ? 'search' : 'folder'}/><h2>{error ? '조회 기간과 검색어를 확인해 주세요.' : items.length ? '조건에 맞는 문의가 없습니다.' : '아직 접수된 문의가 없습니다.'}</h2><p>{items.length ? '필터를 조정하거나 초기화하여 다시 확인해 주세요.' : '새 문의가 등록되면 이 목록에 표시됩니다.'}</p></div>}
        <p className="inquiry-footnote">보유 자료 수는 등록된 자료 종류 수이며 첨부파일 수와 다를 수 있습니다. 검색은 서버가 보호 처리하여 전달한 이름·내용을 기준으로 합니다.</p></>}
    <div className="inquiry-results-meta"><p role="status">{ready ? `표시 ${filtered.length}건 / 조회 ${items.length}건 · 통계는 조회한 전체 목록 기준` : resource.status === 'loading' ? '문의 목록을 불러오는 중입니다.' : '목록을 조회하지 못했습니다.'}</p>{hasFilters && <button type="button" onClick={() => setFilters({ ...EMPTY_LIST_FILTERS })}>필터 초기화</button>}</div>
  </main></LawyerShell>;
}
