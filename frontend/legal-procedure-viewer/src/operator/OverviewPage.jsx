import Icon from './Icon.jsx';
import { number, Resource, Stat } from './components.jsx';
import { buildOverviewDistribution } from './overviewDistribution.js';

function OverviewBar({ label, count, total, tone, code }) {
  const percentage = count == null ? null : total > 0 ? Math.round(count / total * 100) : total === 0 ? 0 : null;
  return <div className="overview-bar-row">
    <span title={code}>{label}</span>
    <div className="bar-track"><i className={tone} style={{ width: `${total > 0 ? count / total * 100 : 0}%` }} /></div>
    <b>{number(count)}건{percentage != null && <small> ({percentage}%)</small>}</b>
  </div>;
}

function DistributionPanel({ title, icon, values, total, kind }) {
  const rows = buildOverviewDistribution(values, kind);
  return <section className={`panel overview-chart overview-${kind}-chart`}>
    <div className="overview-chart-heading"><h2><Icon name={icon} />{title}</h2></div>
    <div className="overview-bar-list">{rows.map(row => <OverviewBar key={row.id} code={row.title} label={row.label} count={row.count} total={total} />)}</div>
  </section>;
}

export default function OverviewPage({ summary }) {
  return <Resource item={summary}>{value => <div className="overview-page">
    <div className="stat-grid">
      <Stat label="Agent 실행 이력" value={value.agentRunCount} foot="저장된 Agent 실행 기록 수입니다." icon="document" unit="건" />
      <Stat label="위험 판단 이력" value={value.riskHistoryCount} foot="저장된 위험 판단 기록 수입니다." icon="alert" unit="건" accent="amber" />
      <Stat label="보안 이벤트" value={value.securityEventCount} foot="저장된 보안 이벤트 기록 수입니다." icon="shield" unit="건" />
      <Stat label="미확인 Incident" value={value.openIncidentCount} foot="현재 OPEN 상태의 Incident 수입니다." icon="flame" unit="건" accent="danger" href="#/audit" />
    </div>
    <div className="overview-charts">
      <section className="panel overview-chart">
        <div className="overview-chart-heading"><h2><Icon name="chart" />위험 등급 분포</h2><span>전체 <b>{number(value.riskSummary.total)}</b>건</span></div>
        <div className="overview-bar-list risk-level-bars">{[['높음 (HIGH)', value.riskSummary.high, 'high'], ['보통 (MEDIUM)', value.riskSummary.medium, 'medium'], ['낮음 (LOW)', value.riskSummary.low, 'low']].map(([label, count, tone]) => <OverviewBar key={tone} label={label} count={count} total={value.riskSummary.total} tone={tone} />)}</div>
      </section>
      <DistributionPanel title="판단 결과 분포" icon="clipboard" values={value.riskSummary.decisionCounts} total={value.riskSummary.total} kind="decision" />
      <DistributionPanel title="권장 조치 분포" icon="settings" values={value.riskSummary.recommendedActionCounts} total={value.riskSummary.total} kind="action" />
    </div>
    <nav className="overview-shortcuts" aria-label="운영 업무 바로가기">
      <a href="#/marketing" className="shortcut-blue"><span className="shortcut-icon"><Icon name="chart" /></span><div><h2>마케팅 흐름 파악</h2><p>문의 접수와 검색 현황을<br />확인할 수 있습니다.</p></div><Icon name="chevron-right" /></a>
      <a href="#/security" className="shortcut-green"><span className="shortcut-icon"><Icon name="shield" /></span><div><h2>보안·비용 확인</h2><p>보안 이벤트와 운영 비용 현황을<br />확인할 수 있습니다.</p></div><Icon name="chevron-right" /></a>
      <a href="#/audit" className="shortcut-amber"><span className="shortcut-icon"><Icon name="alert" /></span><div><h2>위험 이력·대응 관리</h2><p>위험 판단 이력과 Incident를<br />확인하고 대응할 수 있습니다.</p></div><Icon name="chevron-right" /></a>
    </nav>
  </div>}</Resource>;
}
