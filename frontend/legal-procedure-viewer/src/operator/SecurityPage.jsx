import { useEffect, useRef, useState } from 'react';
import * as api from './operatorApi.js';
import { date, number, PageRefresh, Resource } from './components.jsx';
import Icon from './Icon.jsx';
import MetricsAuthDialog from './MetricsAuthDialog.jsx';
import { buildOverviewDistribution } from './overviewDistribution.js';
import { formatSeconds, formatUsd, readSecurityMetrics } from './securityMetrics.js';
import { useResource } from './useResource.js';

function QueryTime({ item }) {
  return <p className="security-query-time"><Icon name="clock" />{item.at ? `조회 ${item.at.toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })}` : item.status === 'error' ? '조회 실패' : item.status === 'idle' ? '관리자 인증 후 별도 조회' : '조회 중'}</p>;
}

function RiskRow({ label, count, total, tone, title }) {
  const percentage = count == null ? null : total > 0 ? Math.round(count / total * 100) : total === 0 ? 0 : null;
  return <div className="security-risk-row"><span title={title}>{label}</span><div className="bar-track"><i className={tone} style={{ width: `${count != null && total > 0 ? count / total * 100 : 0}%` }} /></div><b>{number(count)}건{percentage != null && <small> ({percentage}%)</small>}</b></div>;
}

function RiskGroup({ data, kind, title, icon }) {
  return <section className="security-risk-group"><h3><Icon name={icon} />{title}</h3>{buildOverviewDistribution(kind === 'decision' ? data.decisionCounts : data.recommendedActionCounts, kind).map(row => <RiskRow key={row.id} label={row.label} count={row.count} total={data.total} title={row.title} />)}</section>;
}

function RiskPanel({ item }) {
  return <section className="panel security-column security-risk-panel" aria-label="위험 판단 요약">
    <h2><Icon name="clipboard" />위험 판단 요약 <small>(저장된 위험 분석 기록, C15)</small></h2><QueryTime item={item} />
    <Resource item={item}>{data => <><div className="security-risk-overview"><div className="risk-total"><span>전체 위험 판단 기록</span><strong>{number(data.total)}<small>건</small></strong></div><div className="security-risk-levels">{[['높음 (HIGH)', data.high, 'high'], ['보통 (MEDIUM)', data.medium, 'medium'], ['낮음 (LOW)', data.low, 'low']].map(([label, count, tone]) => <RiskRow key={tone} label={label} count={count} total={data.total} tone={tone} />)}</div></div><div className="security-risk-groups"><RiskGroup data={data} kind="decision" title="판단 결과 분포" icon="pie" /><RiskGroup data={data} kind="action" title="권장 조치 분포" icon="clipboard" /></div></>}</Resource>
  </section>;
}

function EventsPanel({ item }) {
  const eventLabels = { AUTH_FAILURE: '인증 실패', ACCESS_DENIED: '접근 거절' };
  return <section className="panel security-column security-events-panel" aria-label="보안 이벤트">
    <h2><Icon name="clipboard" />보안 이벤트 <small>(최근 이벤트, 최대 50건, C14)</small></h2><QueryTime item={item} />
    <Resource item={item} empty={item.status === 'ok' && !item.data.length}>{rows => <div className="security-events-scroll"><table><thead><tr><th>발생 시각</th><th>이벤트 유형</th><th>요청 메서드·경로</th><th>상태</th><th>사용자</th></tr></thead><tbody>{rows.map(row => <tr key={row.id}><td className="event-time">{date(row.createdAt).split(' ').map((part, index) => <span key={index}>{part}</span>)}</td><td title={row.eventType}>{eventLabels[row.eventType] || row.eventType}</td><td className="event-request"><span>{row.method}</span><span>{row.uri}</span></td><td><span className={`event-status ${/^2/.test(row.status) ? 'ok' : row.status === '429' ? 'warning' : /^[45]/.test(row.status) ? 'denied' : ''}`}>{row.status}</span></td><td>{row.username || '—'}</td></tr>)}</tbody></table></div>}</Resource>
    <a className="security-audit-link" href="#/audit">위험 이력·Incident로 이동 <Icon name="arrow-right" /></a>
  </section>;
}

function MetricCards({ items, className = '' }) {
  return <div className={`security-metric-cards ${className}`}>{items.map(item => <div key={item.key}><span>{item.label}</span><strong title={formatUsd(item.value)}>{formatUsd(item.value)}</strong><small className="metric-key" title={item.key}>({item.key})</small></div>)}</div>;
}

function MetricsContent({ state }) {
  const data = readSecurityMetrics(state.status === 'ok' ? state.data : []);
  const maxStage = Math.max(0, ...data.stages.map(item => item.value || 0));
  return <div className="security-metrics-content">
    <h3>비용 요약 <small>(USD)</small></h3><MetricCards items={data.cost} />
    <h3>설정된 예산 <small>(USD)</small></h3><MetricCards items={data.budgets} className="budget-cards" />
    <div className="security-metric-breakdown"><section><h3>단계별 사용 비용 <small>(USD)</small></h3>{data.stages.map(item => <div className="security-stage-cost" key={item.stage}><span>{item.label}</span><div className="bar-track"><i style={{ width: `${maxStage > 0 && item.value != null ? item.value / maxStage * 100 : 0}%` }} /></div><b title="단계별 정산 합계">{formatUsd(item.value)}</b></div>)}</section><section><h3>토큰 사용량 <small>(누적)</small></h3>{data.tokens.map(item => <div className="security-metric-line" key={item.label}><span>{item.label}</span><b>{number(item.value)}</b></div>)}<h3>처리 시간 <small>(평균, 초)</small></h3>{data.timings.slice(0, 2).map(item => <div className="security-metric-line" key={item.label}><span>{item.label}</span><b>{formatSeconds(item.value)}</b></div>)}<details className="security-stage-timings"><summary>입력·출력 검사 시간</summary>{data.timings.slice(2).map(item => <div className="security-metric-line" key={item.label}><span>{item.label}</span><b>{formatSeconds(item.value)}</b></div>)}</details></section></div>
    <details className="security-metric-notes"><summary>지표 기준 확인</summary><p>정산 합계에는 추정 사용량 정산이 포함됩니다. 단계별 비용도 정산 합계 기준입니다. 처리 시간은 동기 Chat Timer의 합계÷횟수이며 SSE 전체 지연을 뜻하지 않습니다. 세션 예산은 요청 1회 예산이 아닙니다. 아직 반환되지 않은 지표는 —로 표시합니다.</p></details>
  </div>;
}

export default function SecurityPage({ base }) {
  const events = useResource(api.getSecurityEvents, base);
  const risk = useResource(api.getRiskSummary, base);
  const [auth, setAuth] = useState(null);
  const [settings, setSettings] = useState(false);
  const [metrics, setMetrics] = useState({ status: 'idle', data: null });
  const active = useRef(null);
  useEffect(() => () => active.current?.abort(), []);
  async function loadMetrics(credentials) {
    active.current?.abort();
    const controller = new AbortController(); active.current = controller;
    setMetrics({ status: 'loading', data: null });
    try {
      const text = await api.getPrometheusMetrics(base, credentials.username, credentials.password, controller.signal);
      if (!controller.signal.aborted) { setMetrics({ status: 'ok', data: api.parseMetrics(text), at: new Date() }); setAuth(credentials); setSettings(false); }
    } catch (error) {
      if (!controller.signal.aborted) { setMetrics({ status: 'error', error: error.message, data: null }); setAuth(null); }
    }
  }
  function disconnect() { active.current?.abort(); setAuth(null); setMetrics({ status: 'idle', data: null }); setSettings(false); }
  function refresh() { events.reload(); risk.reload(); if (auth) loadMetrics(auth); }
  const busy = events.status === 'loading' || risk.status === 'loading' || metrics.status === 'loading';
  const timestamps = [events.at, risk.at, metrics.at].filter(Boolean);
  const latest = timestamps.length ? new Date(Math.max(...timestamps.map(item => item.getTime()))) : null;
  return <section className="security-page" aria-label="보안·위험·비용 지표">
    <div className="page-heading security-heading"><div><div className="eyebrow">운영 관리 <span aria-hidden="true">›</span> 보안·비용</div><h1 tabIndex="-1">보안 상태와 AI 사용 비용</h1><p>저장된 보안·위험 기록과 현재 서버의 AI 사용 비용 현황을 확인할 수 있습니다.</p></div><div className="security-actions"><a className="back-to-overview" href="#/overview"><Icon name="arrow-left" />운영 요약으로</a><PageRefresh item={{ status: busy ? 'loading' : 'ok', at: latest, reload: refresh }} /></div></div>
    <div className="security-grid"><RiskPanel item={risk} /><EventsPanel item={events} /><section className="panel security-column security-metrics-panel" aria-label="AI 사용 비용 및 성능"><div className="security-metrics-heading"><h2><Icon name="document" />AI 사용 비용 및 성능 <small>(현재 서버 메트릭, C16)</small></h2><button className="metric-connect-button" onClick={() => setSettings(true)}><Icon name="settings" />연결 설정</button></div><QueryTime item={metrics} />
      {metrics.status === 'idle' && <p className="security-metrics-state">연결 설정에서 관리자 인증 후 실제 지표를 조회하세요.</p>}
      {metrics.status === 'loading' && <p className="security-metrics-state" role="status">서버 메트릭을 조회하고 있습니다.</p>}
      {metrics.status === 'error' && <p className="security-metrics-state error" role="alert">{metrics.error} 연결 설정을 확인해 주세요.</p>}
      <MetricsContent state={metrics} />
    </section></div>
    <div className="security-footnotes"><aside><Icon name="info" /><div><h2>저장된 기록과 현재 서버 메트릭을 별도로 보여줍니다.</h2><p>C14·C15는 DB 조회, C16은 현재 서버의 메모리 지표이며 각각 별도로 조회됩니다.</p></div></aside><aside><Icon name="alert" /><div><h2>단계별 집계 기준이 달라 전환율·이탈률은 아직 측정하지 않습니다.</h2><p>보고된 행동 이벤트·저장된 문의·고유 문의·현재 상태의 집계 기준은 서로 다릅니다.</p></div></aside></div>
    {settings && <MetricsAuthDialog state={metrics} username={auth?.username} connected={Boolean(auth)} onSubmit={loadMetrics} onClose={() => setSettings(false)} onDisconnect={disconnect} />}
  </section>;
}
