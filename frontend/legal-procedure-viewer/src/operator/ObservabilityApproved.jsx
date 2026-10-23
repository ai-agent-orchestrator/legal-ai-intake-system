import { useEffect, useRef, useState } from 'react';
import * as api from './operatorApi.js';
import Icon from './Icon.jsx';
import MetricsAuthDialog from './MetricsAuthDialog.jsx';
import PatternTimeSeries from './PatternTimeSeries.jsx';
import { scrapeState } from './prometheusChart.js';
import { createObservabilityMock } from './observabilityMock.js';
import { number } from './components.jsx';
import { useResource } from './useResource.js';
import { operatorRiskPatterns, dynamicScoreRules, readPatternOccurrences, readPatternRisk, readDynamicMetrics } from './observabilityMetrics.js';

const requestWeights = [['정책 위반', '+40점'], ['외부 접근', '+25점'], ['도구 오류', '+15점'], ['승인 필요', '+20점'], ['DB 쓰기', '+15점'], ['재시도', '회당 +8점 · 최대 +25점']];
const atTime = at => at?.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });

function RequestCheck({ pattern, index, history, timeline, mock }) {
  const occurrence = readPatternOccurrences(pattern, history.status === 'ok' ? history.data : null);
  const risk = readPatternRisk(pattern, history.status === 'ok' ? history.data : []);
  const waiting = history.status === 'loading' || history.status === 'idle';
  const outcome = risk.row ? risk.block ? '차단 권고' : risk.row.recommendedAction === 'DISABLE_TOOL_TEMPORARILY' ? '도구 점검' : risk.total >= 40 || risk.row.recommendedAction === 'REQUIRE_APPROVAL' ? '승인 검토' : '관찰' : waiting ? '조회 중' : history.status === 'error' ? '조회 실패' : '기록 없음';
  const reference = risk.row ? `#/audit?riskId=${encodeURIComponent(risk.row.id)}` : '#/audit';
  return <article className={`draft-check draft-${pattern.tone}`} aria-labelledby={`risk-title-${index}`}>
    <header><span className="draft-check-number">{index + 1}</span><h3 id={`risk-title-${index}`}>{pattern.title}</h3><span className={`draft-status ${risk.block ? 'status-high' : 'status-review'}`}>{outcome}</span><a className="obs-history-link" href={mock ? undefined : reference} aria-disabled={mock || undefined} aria-label={risk.row ? `위험 이력 RH-${risk.row.id} 확인` : '관련 위험 이력 확인'}>{mock ? '모의 이력' : risk.row ? `RH-${risk.row.id}` : '기록 확인'}<Icon name="arrow-right" /></a></header>
    <div className="draft-check-body"><div className="obs-pattern-observation"><div className="obs-pattern-count" title={`최근 조회 ${number(occurrence.sourceCount)}건에서 같은 요청의 위험 신호와 재시도가 함께 있는 기록`}><span>반복 패턴 기록</span><strong>{number(occurrence.count)}<small>건</small></strong><div className="obs-card-score"><span>행동 {number(risk.behaviorPoints)} + 재시도 {number(risk.retryPoints)}</span><b>{number(risk.total)}<small> / 100점</small></b></div></div><PatternTimeSeries pattern={pattern} resource={timeline} /></div></div>
  </article>;
}

function SessionTrack({ dynamic, connected }) {
  return <section className="draft-panel draft-conversation-panel" aria-labelledby="obs-session-title"><header className="draft-panel-heading"><h2 id="obs-session-title"><Icon name="shield" />Dynamic Guard 2 <small>대화 누적 제한</small></h2><span>같은 대화 기준</span></header><div className="draft-conversation-body"><div className="draft-session-intro"><div><h3>위험한 요청이 반복되나요?</h3><p>차단된 요청의 점수를 쌓아 반복 시도를 제한합니다.</p></div><span className="draft-session-icon"><Icon name="shield" /></span></div><div className="draft-session-score"><div><span>대화별 누적 점수 중 최댓값</span><strong>{number(dynamic[3].value)}<small>점</small></strong></div><div><b>100점부터 이용 제한</b><small>{connected ? '전체 대화 중 최댓값 · 100점 초과 가능' : '지표 연결 후 실제 관측값 표시'}</small></div></div><div className="draft-session-flow"><span>차단 사유</span><Icon name="arrow-right" /><span>대화에 누적</span><Icon name="arrow-right" /><b>반복 시 이용 제한</b></div><div className="draft-session-summary"><span>누적 위험 이벤트 <b>{number(dynamic[0].value)}회</b></span><span>차단 상태 전환 <b>{number(dynamic[1].value)}회</b></span></div><section className="draft-rules"><header><h3>차단 사유별 누적 점수</h3><small>Fixed Guard 등의 차단 사유를 누적</small></header><table><tbody>{dynamicScoreRules.slice(0, 5).map(([label,, score]) => <tr key={label}><th scope="row">{label}</th><td>+{score}점</td></tr>)}</tbody></table><p><b>누적 100점</b> 또는 <b>반복 기준 도달</b> 시 이용을 제한합니다.</p></section></div></section>;
}

function OperationsGuide() {
  const [expanded, setExpanded] = useState(false);
  return <section className="draft-operations" aria-labelledby="obs-operations-title"><header><h2 id="obs-operations-title"><Icon name="info" />위험점수 운영 설명</h2><button className="obs-calculation-toggle" aria-expanded={expanded} aria-controls="obs-calculation" onClick={() => setExpanded(value => !value)}>위험점수는 어떻게 계산하나요?<Icon name={expanded ? 'chevron-down' : 'chevron-right'} /></button></header><div className="draft-operations-grid"><article><b><span>1</span>요청 점수</b><p>행동 점수 + 재시도 점수, 최대 100점.<br /><strong>80점 이상은 차단·상위 검토 권고</strong>입니다.</p></article><article><b><span>2</span>대화 누적 점수</b><p>차단 사유를 같은 대화에 쌓습니다.<br /><strong>100점 또는 반복 기준 도달 시 실제 제한</strong>합니다.</p></article><article><b><span>3</span>운영자 확인</b><p>지표 증가 → 관련 위험 이력 → Incident 검토.<br />서로 다른 요청이나 두 점수를 합산하지 않습니다.</p></article></div>
    <div id="obs-calculation" className="obs-calculation" hidden={!expanded}><section><h3>요청 1건의 계산</h3><table><tbody>{requestWeights.map(([label, score]) => <tr key={label}><th scope="row">{label}</th><td>{score}</td></tr>)}</tbody></table><p>같은 요청에서 확인한 신호를 더해 최대100점으로 표시합니다. 카드의 행동 점수에는 재시도 외 신호가 포함됩니다. 예: 정책 위반40 + 외부 접근25 + 재시도2회16 = 81점.</p><p>80점 이상은 차단·상위 검토 권고입니다. 40~79점은 승인 검토가 기본이며, 승인 경계·도구 오류에는 별도 조치가 유지됩니다. 도구 호출 횟수 자체에는 가산점이 없습니다.</p></section><section><h3>대화 누적 제한·운영 기준</h3><ul><li>누적100점 이상 또는 전체 차단 이벤트3회에 제한합니다.</li><li>같은 위해·탈옥·주입·비밀 요청·긴 입력은2회, 범위 밖 주제는3회 기준도 적용합니다.</li><li>오른쪽 표 외 그 밖의 차단 사유는 기본 +25점입니다.</li></ul><p>처음 차단 사유를 만드는 Fixed Guard·입력 검사·비용 제어와, 그 사유를 누적하는 Dynamic Guard는 역할이 다릅니다. 실제 누적 경로를 거친 사유만 반영하며 서버 메모리 기준입니다.</p><p>누적 지표만으로 같은 요청의 행동이나 정보 유출을 확정하지 않습니다. 요청80점은 권고이며 이 화면이 외부 도구·LLM을 직접 중단시키는 것은 아닙니다. C16 지표와 C17 요청 기록을 구분해 읽고, 모호한 과거 세부 점수는 —로 표시합니다.</p></section></div>
  </section>;
}

export default function ObservabilityApproved({ base }) {
  const [mock, setMock] = useState(() => new URLSearchParams(location.search).get('mock') === '1' ? createObservabilityMock() : null);
  const liveHistory = useResource(api.getRiskHistory, base, { enabled: !mock });
  const history = mock?.history || liveHistory;
  const [auth, setAuth] = useState(null), [settings, setSettings] = useState(false);
  const [liveMetrics, setMetrics] = useState({ status: 'idle', data: null, previous: null });
  const [liveTimeline, setTimeline] = useState({ status: 'idle', data: null });
  const timeline = mock?.timeline || liveTimeline;
  const metrics = mock ? { status: 'ok', data: mock.metrics, at: mock.timeline.at } : liveMetrics;
  const [autoRefresh, setAutoRefresh] = useState(true);
  const active = useRef(null), baseline = useRef(null);
  useEffect(() => {
    if (!auth || !autoRefresh || mock) return;
    const timer = setInterval(() => { if (!active.current) loadMetrics(auth); }, 30000);
    return () => clearInterval(timer);
  }, [auth, autoRefresh, base, mock]);
  useEffect(() => () => active.current?.abort(), []);
  async function loadMetrics(credentials) {
    active.current?.abort();
    const controller = new AbortController(); active.current = controller;
    setMetrics({ status: 'loading', data: null, previous: null });
    setTimeline({ status: 'loading', data: null });
    const results = await Promise.allSettled([
      api.getPrometheusMetrics(base, credentials.username, credentials.password, controller.signal),
      api.getGuardTimeSeries(base, credentials.username, credentials.password, controller.signal),
    ]);
    if (controller.signal.aborted) return;
    const at = new Date();
    const [snapshot, range] = results;
    const denied = results.some(result => result.status === 'rejected' && [401, 403].includes(result.reason.status));
    if (denied) {
      const error = results.find(result => result.status === 'rejected' && [401, 403].includes(result.reason.status)).reason.message;
      setMetrics({ status: 'error', data: null, error }); setTimeline({ status: 'error', data: null, error });
      setAuth(null); baseline.current = null;
    } else {
      if (snapshot.status === 'fulfilled') {
        const data = api.parseMetrics(snapshot.value);
        setMetrics({ status: 'ok', data, previous: baseline.current?.data || null, at });
        baseline.current = { data, at };
      } else setMetrics({ status: 'error', data: null, error: snapshot.reason.message });
      if (range.status === 'fulfilled') setTimeline({ status: 'ok', data: range.value, at });
      else setTimeline({ status: 'error', data: null, error: range.reason.message });
      if (results.some(result => result.status === 'fulfilled')) { setAuth(credentials); setSettings(false); }
    }
    active.current = null;
  }
  function disconnect() { active.current?.abort(); baseline.current = null; setAuth(null); setMetrics({ status: 'idle', data: null, previous: null }); setTimeline({ status: 'idle', data: null }); active.current = null; setSettings(false); }
  function toggleMock() {
    disconnect();
    const next = !mock;
    setMock(next ? createObservabilityMock() : null);
    const url = new URL(location.href);
    if (next) url.searchParams.set('mock', '1'); else url.searchParams.delete('mock');
    window.history.replaceState(null, '', url);
  }
  function refresh() { if (mock) { setMock(createObservabilityMock()); return; } history.reload(); if (auth) loadMetrics(auth); }
  const busy = history.status === 'loading' || metrics.status === 'loading' || timeline.status === 'loading';
  const dynamic = readDynamicMetrics(metrics.data || []);
  const at = [metrics.at, timeline.at, history.at].filter(Boolean).sort((a, b) => b - a)[0];
  return <section className="observability-page obs-approved" aria-label="화면13 AI 위험 요약">
    <header className="draft-page-heading"><div><div className="draft-breadcrumb">운영 관리 <Icon name="chevron-right" />AI 위험 요약 · Observability</div><h1 tabIndex="-1">AI 위험을 한눈에 확인하세요.</h1><p>요청별 위험 신호와 대화 누적 점수를 함께 보고, 필요한 대응을 확인합니다.</p></div><div className="draft-page-actions"><button className="draft-outline" disabled={Boolean(mock)} onClick={() => setSettings(true)}><Icon name="settings" />{auth ? '지표 연결 설정' : '지표 연결'}</button><span><Icon name="clock" />{at ? `조회 시각 ${atTime(at)}` : '조회 시각 —'}</span><button className="draft-outline" disabled={busy} onClick={refresh}><Icon name="refresh" />새로고침</button></div></header>
    {mock && <div className="obs-mock-notice" role="status"><b>모의 자료</b> 그래프·건수·점수 모두 화면 검증용 예시입니다. 서버 저장·LLM 호출·과금은 없습니다.</div>}
    {metrics.status === 'error' && <div className="notice error" role="alert">{metrics.error} <button className="text-button" onClick={() => setSettings(true)}>연결 설정</button></div>}
    {timeline.status === 'error' && <div className="notice error" role="alert">시계열 연결 실패 · Prometheus 실행·수집 대상·서버 설정을 확인하세요. <button className="text-button" disabled={busy} onClick={() => auth ? loadMetrics(auth) : setSettings(true)}>다시 조회</button></div>}
    {history.status === 'error' && <div className="notice error" role="alert">위험 기록을 조회하지 못했습니다. <button className="text-button" onClick={history.reload}>다시 조회</button></div>}
    {metrics.status === 'loading' && <p className="obs-load-status" role="status">지표를 불러오는 중입니다.</p>}
    <div className="draft-tracks"><section className="draft-panel draft-request-panel" aria-labelledby="obs-request-title"><header className="draft-panel-heading"><h2 id="obs-request-title"><Icon name="document" />Dynamic Guard 1 <small>위험 행동 반복 시도</small></h2><span>같은 요청의 신호 조합</span></header><div className="draft-request-caption"><span>위험 행동과 재시도가 함께 기록된 패턴을 확인하세요.</span><small>최근 조회 최대 50건 기준</small></div><div className="obs-timeline-tools"><span className={timeline.status === 'ok' && scrapeState(timeline.data) !== '수집 중' ? 'obs-scrape-warning' : ''}>{mock ? '모의 시계열 · 예시' : 'Prometheus'}{!mock && <> · {timeline.status === 'loading' ? '조회 중' : timeline.status === 'error' ? '연결 실패' : scrapeState(timeline.data)}</>}</span><button className="obs-mock-toggle" onClick={toggleMock}>{mock ? '실제 자료로 돌아가기' : '모의 자료 보기'}</button><label><input type="checkbox" disabled={Boolean(mock)} checked={!mock && autoRefresh} onChange={event => setAutoRefresh(event.target.checked)} />30초 자동 갱신</label></div><div className="draft-check-list">{operatorRiskPatterns.map((pattern, index) => <RequestCheck key={pattern.id} pattern={pattern} index={index} history={history} timeline={timeline} mock={Boolean(mock)} />)}</div><div className="draft-panel-foot"><Icon name="info" />{mock ? '모의 자료 · 실제 관측·사건·점수가 아닙니다.' : '건수: 최근 기록 최대50건 · 그래프: 신호별 실제 증가량 · 점수: 최근 관련 요청 1건'}</div></section><SessionTrack dynamic={dynamic} connected={metrics.status === 'ok'} /></div>
    {timeline.status === 'ok' && timeline.data.warnings.length > 0 && <p className="obs-load-status" role="status">시계열 조회에 경고가 있어 일부 관측이 누락될 수 있습니다.</p>}
    <OperationsGuide />
    <div className="draft-footer"><span>{mock ? '모의 자료 미리보기 · ' : metrics.at ? `지표 조회 ${atTime(metrics.at)} · ` : '지표 연결 후 실제 관측값 표시 · '}관측이 없으면 — · 실제 0은 0</span><a href="#/audit">위험 이력·Incident 확인<Icon name="arrow-right" /></a></div>
    {settings && <MetricsAuthDialog description="현재 지표(C16)와 실제 시계열을 ADMIN Basic으로 조회합니다. 계정은 이 화면 메모리에만 보관하며 Prometheus 서버로 전달하지 않습니다." title="지표 연결" state={metrics} username={auth?.username} connected={Boolean(auth)} onSubmit={loadMetrics} onClose={() => setSettings(false)} onDisconnect={disconnect} />}
  </section>;
}
