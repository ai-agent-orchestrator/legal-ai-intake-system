import { useEffect, useRef, useState } from 'react';
import * as api from './operatorApi.js';
import { date, PageRefresh, Resource } from './components.jsx';
import Icon from './Icon.jsx';
import { useResource } from './useResource.js';
import { canChangeIncident, changeIncidentAndReload, decisions, displayCode, filterIncidents, incidentId, incidentStatuses, recommendedActions, riskId, riskLevels, riskQuery, riskSignals } from './auditModel.js';

function Code({ value, names }) { return <span title={value || undefined}>{displayCode(names, value)}</span>; }
function Level({ value }) { return <span className={`audit-badge level-${String(value).toLowerCase()}`} title={value || undefined}>{displayCode(riskLevels, value)}</span>; }
function Status({ value }) { return <span className={`audit-badge status-${String(value).toLowerCase()}`} title={value || undefined}>{displayCode(incidentStatuses, value)}</span>; }
function Score({ value }) { return <span title="서버 위험 점수 · 0~100">{value == null ? '—' : value}</span>; }
function SelectOptions({ options }) { return <><option value="ALL">전체</option>{Object.entries(options).map(([value, name]) => <option key={value} value={value}>{name}</option>)}</>; }
function DetailHeader({ title, onClose }) { return <div className="audit-detail-heading"><h3><Icon name="document" />{title}</h3><button className="audit-close" onClick={onClose} aria-label={`${title} 닫기`}><Icon name="close" /></button></div>; }

function RiskDetail({ item, onClose }) {
  return <section className="audit-detail" aria-label="위험 이력 상세"><DetailHeader title="위험 이력 상세" onClose={onClose} /><div className="audit-detail-body risk-detail-grid"><dl>
    <dt>위험 이력 번호</dt><dd>{riskId(item.id)}</dd><dt>시각</dt><dd>{date(item.createdAt)}</dd><dt>도구</dt><dd>{item.toolName || '—'}</dd><dt>위험 수준</dt><dd><Level value={item.riskLevel} /></dd><dt>점수</dt><dd><Score value={item.riskScore} /></dd><dt>판정</dt><dd><Code names={decisions} value={item.decision} /></dd><dt>권고 조치</dt><dd><Code names={recommendedActions} value={item.recommendedAction} /></dd>
  </dl><div className="audit-risk-evidence"><section className="audit-signals"><h4>감지 신호 (signals)</h4>{item.signals?.length ? <ul>{item.signals.map((signal, index) => <li key={`${signal}:${index}`}><Code names={riskSignals} value={signal} /></li>)}</ul> : <p>기록된 감지 신호가 없습니다.</p>}</section><dl><dt>가드레일 준비 상태</dt><dd>{item.guardrailReady == null ? '—' : item.guardrailReady ? '준비됨' : '미준비'}</dd><dt>Trace ID</dt><dd className="audit-trace">{item.traceId || '—'}</dd></dl></div></div></section>;
}

function IncidentDetail({ item, linkedRisk, onClose, onSelectRisk, onChange, pending, filteredOut }) {
  return <section className="audit-detail" aria-label="Incident 상세"><DetailHeader title="Incident 상세" onClose={onClose} /><div className="audit-detail-body incident-detail-grid"><dl>
    <dt>Incident 번호</dt><dd>{incidentId(item.id)}</dd><dt>발생 시각</dt><dd>{date(item.createdAt)}</dd><dt>유형</dt><dd>{item.incidentType === 'AI_AGENT_RISK' ? <span title={item.incidentType}>AI Agent 위험</span> : item.incidentType || '—'}</dd><dt>심각도</dt><dd><Level value={item.severity} /></dd><dt>상태</dt><dd><Status value={item.status} /></dd><dt>위험 점수</dt><dd><Score value={item.riskScore} /></dd><dt>판정</dt><dd><Code names={decisions} value={item.decision} /></dd><dt>권고 조치</dt><dd><Code names={recommendedActions} value={item.recommendedAction} /></dd>
  </dl><div className="audit-incident-context"><h4>메시지</h4><p className="audit-message">{item.message || '—'}</p><dl><dt>연관 위험 이력 번호</dt><dd>{linkedRisk ? <button className="audit-linked-risk" onClick={onSelectRisk} disabled={Boolean(pending)}>{riskId(item.riskHistoryId)} <Icon name="arrow-left" /></button> : riskId(item.riskHistoryId)}</dd><dt>Trace ID</dt><dd className="audit-trace">{item.traceId || '—'}</dd><dt>생성 시각</dt><dd>{date(item.createdAt)}</dd><dt>최종 수정 시각</dt><dd>{date(item.updatedAt)}</dd></dl>{!linkedRisk && <p className="audit-detail-note">연관 위험 이력이 현재 왼쪽 조회 범위에 없습니다.</p>}{filteredOut && <p className="audit-detail-note">선택한 기록은 현재 목록 필터에 표시되지 않습니다.</p>}<div className="audit-incident-actions"><button className="audit-ack" disabled={Boolean(pending) || !canChangeIncident(item, 'ack')} onClick={() => onChange('ack')}><Icon name="check-circle" />{pending === 'ack' ? '저장 중…' : '확인 처리'}</button><button className="audit-resolve" disabled={Boolean(pending) || !canChangeIncident(item, 'resolve')} onClick={() => onChange('resolve')}><Icon name="check-circle" />{pending === 'resolve' ? '저장 중…' : '해결 처리'}</button></div></div></div></section>;
}

export default function AuditPage({ base, refreshSummary, setLocked, requestedRiskId = null }) {
  const [riskDraft, setRiskDraft] = useState({ mode: 'riskLevel', value: 'ALL' });
  const [query, setQuery] = useState('');
  const history = useResource(api.getRiskHistory, base, { query });
  const incidents = useResource(api.getIncidents, base);
  const [riskSelection, setRiskSelection] = useState(requestedRiskId ?? undefined);
  const [incidentSelection, setIncidentSelection] = useState(undefined);
  const [incidentDraft, setIncidentDraft] = useState({ status: 'ALL', severity: 'ALL' });
  const [incidentFilter, setIncidentFilter] = useState(incidentDraft);
  const [pending, setPending] = useState(null);
  const [notice, setNotice] = useState(null);
  const mutation = useRef(null);
  useEffect(() => () => { mutation.current?.abort(); setLocked(false); }, [setLocked]);
  useEffect(() => { if (requestedRiskId != null) setRiskSelection(requestedRiskId); }, [requestedRiskId]);
  const riskRows = history.data || [];
  const allIncidents = incidents.data || [];
  const incidentRows = filterIncidents(allIncidents, incidentFilter);
  // Auto-open the first returned row only on initial load; closing a detail stays closed.
  const selectedRisk = riskRows.find(row => row.id === riskSelection) || (riskSelection === undefined ? riskRows[0] : null);
  const selected = allIncidents.find(row => row.id === incidentSelection) || (incidentSelection === undefined ? incidentRows[0] : null);
  const linkedRisk = selected && riskRows.find(row => row.id === selected.riskHistoryId);
  const busy = Boolean(pending) || history.status === 'loading' || incidents.status === 'loading';
  const timestamps = [history.at, incidents.at].filter(Boolean);
  const latest = timestamps.length ? new Date(Math.max(...timestamps.map(at => at.getTime()))) : null;

  function submitRisk(event) {
    event.preventDefault(); if (pending) return;
    const nextQuery = riskQuery(riskDraft.mode, riskDraft.value);
    setRiskSelection(undefined);
    if (nextQuery === query) history.reload(); else setQuery(nextQuery);
  }
  function submitIncidents(event) {
    event.preventDefault(); if (pending) return;
    setIncidentFilter({ ...incidentDraft }); setIncidentSelection(undefined); incidents.reload();
  }
  function refresh() { if (!pending) { history.reload(); incidents.reload(); } }
  async function changeIncident(action) {
    if (mutation.current || !canChangeIncident(selected, action)) return;
    const incident = selected, controller = new AbortController();
    mutation.current = controller; setIncidentSelection(incident.id);
    setPending(action); setLocked(true); setNotice(null);
    try {
      const result = await changeIncidentAndReload({ base, incident, action, signal: controller.signal, refreshIncidents: incidents.reload, refreshSummary });
      if (result.cancelled) return;
      setNotice({ error: !result.saved || !result.refreshed, text: result.saved ? result.refreshed ? `${incidentId(incident.id)} ${action === 'ack' ? '확인' : '해결'} 처리가 저장됐습니다. Incident 목록과 운영 요약을 갱신했습니다.` : '상태 변경은 저장됐지만 일부 재조회에 실패했습니다. 새로고침으로 최신 상태를 확인하세요.' : `${result.error} 저장 여부를 단정할 수 없어 목록과 운영 요약을 재조회했습니다.${result.refreshed ? ' 최신 상태를 확인한 후 다시 처리하세요.' : ' 재조회에도 실패했습니다. 새로고침 후 최신 상태를 확인하세요.'}` });
    } catch (error) { if (!controller.signal.aborted) setNotice({ error: true, text: error.message }); }
    finally { if (!controller.signal.aborted) { mutation.current = null; setPending(null); setLocked(false); } }
  }

  return <section className="audit-page" aria-label="위험 이력·Incident 대응">
    <div className="page-heading audit-heading"><div><div className="eyebrow">운영 관리 <span aria-hidden="true">›</span> 위험 이력·Incident</div><h1 tabIndex="-1">위험 판단을 추적하고 대응하세요.</h1><p>저장된 위험 판단 이력과 보안 Incident를 확인하고, 필요한 조치를 수행할 수 있습니다.</p></div><div className="audit-page-actions"><a className="audit-back" href="#/security" aria-disabled={Boolean(pending)} onClick={event => { if (pending) event.preventDefault(); }}><Icon name="arrow-left" />보안·비용으로 돌아가기</a><PageRefresh item={{ status: busy ? 'loading' : 'ok', at: latest, reload: refresh }} /></div></div>
    {notice && <div className={`notice ${notice.error ? 'error' : ''}`} role={notice.error ? 'alert' : 'status'}>{notice.text}</div>}
    {requestedRiskId != null && riskSelection === requestedRiskId && history.status === 'ok' && !riskRows.some(row => row.id === requestedRiskId) && <div className="notice" role="status">요청한 위험 이력 RH-{requestedRiskId}는 현재 조회 범위에 없습니다. 조회 조건을 확인해 주세요.</div>}
    <div className="audit-columns">
      <section className="audit-column panel" aria-label="위험 판단 이력"><header className="audit-panel-heading"><h2><Icon name="document" />위험 판단 이력</h2><small>{query ? '서버 조건 조회' : '최근 최대 50건'}</small></header>
        <form className="audit-filters risk-filters" onSubmit={submitRisk}><fieldset disabled={Boolean(pending)}><legend>필터 기준</legend><label><input type="radio" name="risk-mode" value="riskLevel" checked={riskDraft.mode === 'riskLevel'} onChange={() => setRiskDraft({ mode: 'riskLevel', value: 'ALL' })} />위험 수준</label><label><input type="radio" name="risk-mode" value="decision" checked={riskDraft.mode === 'decision'} onChange={() => setRiskDraft({ mode: 'decision', value: 'ALL' })} />판정</label></fieldset><label className="sr-only" htmlFor="audit-risk-value">위험 이력 조회 값</label><select id="audit-risk-value" value={riskDraft.value} disabled={Boolean(pending)} onChange={event => setRiskDraft({ ...riskDraft, value: event.target.value })}><SelectOptions options={riskDraft.mode === 'riskLevel' ? riskLevels : decisions} /></select><button className="audit-query" disabled={Boolean(pending) || history.status === 'loading'}>조회</button></form>
        <div className="audit-table-wrap"><table aria-label="위험 판단 이력 목록"><thead><tr><th>시각</th><th>위험 이력 번호</th><th>도구</th><th>위험<br />수준</th><th>점수</th><th>판정</th><th>권고 조치</th><th>Trace ID</th></tr></thead><tbody>{history.status === 'ok' && riskRows.map(item => <tr key={item.id} className={selectedRisk?.id === item.id ? 'selected' : ''}><td className="audit-date">{date(item.createdAt)}</td><td><button className="audit-row-select" aria-label={`위험 이력 ${item.id} 상세`} aria-pressed={selectedRisk?.id === item.id} disabled={Boolean(pending)} onClick={() => setRiskSelection(item.id)}>{riskId(item.id)}</button></td><td title={item.toolName}>{item.toolName || '—'}</td><td><Level value={item.riskLevel} /></td><td><Score value={item.riskScore} /></td><td><Code names={decisions} value={item.decision} /></td><td><Code names={recommendedActions} value={item.recommendedAction} /></td><td className="audit-trace-cell" title={item.traceId}>{item.traceId || '—'}</td></tr>)}</tbody></table>{(history.status !== 'ok' || !riskRows.length) && <Resource item={history} empty={history.status === 'ok' && !riskRows.length}>{() => null}</Resource>}</div>
        {selectedRisk ? <RiskDetail item={selectedRisk} onClose={() => setRiskSelection(null)} /> : <div className="audit-selection-empty">{history.status === 'loading' ? '최신 위험 이력을 조회하고 있습니다.' : '목록의 위험 이력 번호를 선택하면 상세 내용을 확인할 수 있습니다.'}</div>}
      </section>
      <section className="audit-column panel" aria-label="보안 Incident"><header className="audit-panel-heading"><h2><Icon name="alert" />보안 Incident</h2><small>최근 최대 50건</small></header>
        <form className="audit-filters incident-filters" onSubmit={submitIncidents}><label htmlFor="audit-incident-status">상태</label><select id="audit-incident-status" disabled={Boolean(pending)} value={incidentDraft.status} onChange={event => setIncidentDraft({ ...incidentDraft, status: event.target.value })}><SelectOptions options={incidentStatuses} /></select><label htmlFor="audit-incident-severity">심각도</label><select id="audit-incident-severity" disabled={Boolean(pending)} value={incidentDraft.severity} onChange={event => setIncidentDraft({ ...incidentDraft, severity: event.target.value })}><SelectOptions options={riskLevels} /></select><button className="audit-query" disabled={Boolean(pending) || incidents.status === 'loading'}>조회</button></form>
        <div className="audit-table-wrap"><table aria-label="보안 Incident 목록"><thead><tr><th>Incident 번호</th><th>발생 시각</th><th>유형</th><th>심각도</th><th>상태</th><th>위험 점수</th></tr></thead><tbody>{incidents.status === 'ok' && incidentRows.map(item => <tr key={item.id} className={selected?.id === item.id ? 'selected' : ''}><td><button className="audit-row-select" aria-label={`Incident ${item.id} 상세`} aria-pressed={selected?.id === item.id} disabled={Boolean(pending)} onClick={() => { setIncidentSelection(item.id); if (riskRows.some(row => row.id === item.riskHistoryId)) setRiskSelection(item.riskHistoryId); }}>{incidentId(item.id)}</button></td><td className="audit-date">{date(item.createdAt)}</td><td title={item.incidentType}>{item.incidentType === 'AI_AGENT_RISK' ? 'AI Agent 위험' : item.incidentType || '—'}</td><td><Level value={item.severity} /></td><td><Status value={item.status} /></td><td><Score value={item.riskScore} /></td></tr>)}</tbody></table>{(incidents.status !== 'ok' || !incidentRows.length) && <Resource item={incidents} empty={incidents.status === 'ok' && !incidentRows.length}>{() => null}</Resource>}</div>
        {selected ? <IncidentDetail item={selected} linkedRisk={linkedRisk} pending={pending} filteredOut={!incidentRows.some(row => row.id === selected.id)} onClose={() => { if (!pending) setIncidentSelection(null); }} onSelectRisk={() => setRiskSelection(selected.riskHistoryId)} onChange={changeIncident} /> : <div className="audit-selection-empty">{incidents.status === 'loading' ? '최신 상태를 확인하고 있습니다.' : '목록의 Incident 번호를 선택하면 상세 내용과 대응 버튼을 확인할 수 있습니다.'}</div>}
      </section>
    </div>
    <p className="audit-page-note">점수는 서버의 0~100 기준입니다. 위험 이력은 조건 없이 최근 최대 50건, 조건 지정 시 해당 조건으로 조회합니다. Incident 필터는 최근 최대 50건 안에서 적용합니다.</p>
  </section>;
}
