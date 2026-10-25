import Icon from './Icon.jsx';
import { Stat } from './components.jsx';
import { analyzeMarketingJourneys, marketingDemoJourneys, marketingDemoPeriod, marketingPercent } from './marketingDemo.js';

export default function MarketingDemoPanel({ journeys = marketingDemoJourneys }) {
  const analysis = analyzeMarketingJourneys(journeys);
  const largest = analysis.largestDecrease;
  const leadingReason = analysis.reasons[0];
  return <div className="marketing-demo" aria-label="시연 데이터 마케팅 분석">
    <div className="marketing-demo-notice" role="status">
      <span className="marketing-demo-badge">시연 데이터 기준</span>
      <p>{marketingDemoPeriod.cohort} · 기준일 {marketingDemoPeriod.asOf}<small>가상 문의의 연결된 진행 기록으로 계산합니다. 실제 실적·C13 집계와 분리됩니다.</small></p>
    </div>
    <div className="stat-grid marketing-stats">
      <Stat label="분석 대상 문의" value={analysis.stages[0].count} foot="같은 기간 접수한 가상 문의" icon="document" unit="건" />
      <Stat label="상담 완료" value={analysis.consultationCount} foot="실제 집계의 상담 준비와 구분" icon="users" unit="건" accent="amber" />
      <Stat label="수임 확정" value={analysis.retainedCount} foot="상담 후 수임 결과가 기록된 문의" icon="calendar" unit="건" accent="mint" />
      <article className="stat mint marketing-conversion-stat"><span className="stat-icon"><Icon name="pie" /></span><div className="stat-body"><span>상담 후 수임률</span><strong>{marketingPercent(analysis.consultationConversion)}</strong></div><small>수임 확정 ÷ 상담 완료</small></article>
    </div>
    <section className="marketing-journey marketing-demo-funnel" aria-label="시연 문의 전환 퍼널">
      <div className="marketing-demo-section-heading"><h2><Icon name="journey" />문의가 수임으로 이어지는 흐름</h2><span>각 문의의 단계별 고유 도달 수</span></div>
      <ol className="marketing-demo-stages">{analysis.stages.map((stage, index) => <li key={stage.key}>
        <div className="marketing-demo-stage-heading"><span className="marketing-demo-step">0{index + 1}</span><h3>{stage.label}</h3></div>
        <strong>{stage.count}<small>건</small></strong>
        <div className="marketing-demo-bar" aria-hidden="true"><i style={{ width: `${stage.share ?? 0}%` }} /></div>
        <p>{index ? <>직전 단계에서 <b>{marketingPercent(stage.conversion)}</b> 도달</> : '같은 기간 접수 문의 전체'}</p>
        {index > 0 && <small className="marketing-demo-decrease">미도달 {analysis.transitions[index - 1].notReached}건 · {marketingPercent(analysis.transitions[index - 1].decrease)}</small>}
      </li>)}</ol>
      <p className="marketing-demo-caveat">미도달은 기준일 현재 다음 단계 기록이 없는 문의입니다. 진행 중인 문의도 포함하므로 모두 이탈로 보지 않습니다.</p>
    </section>
    <div className="marketing-demo-bottom">
      <section className="panel marketing-demo-insights" aria-label="시연 마케팅 핵심 인사이트">
        <h2><Icon name="info" />지금 살펴볼 지점</h2>
        <div className="marketing-demo-insight"><span>전환</span><p>상담 완료 <b>{analysis.consultationCount}건</b> 중 수임 <b>{analysis.retainedCount}건</b><strong>상담 후 수임률 {marketingPercent(analysis.consultationConversion)}</strong></p></div>
        {largest && <div className="marketing-demo-insight"><span>감소</span><p>{largest.from} → {largest.to}<strong>미도달 비율 최대 {marketingPercent(largest.decrease)} · {largest.notReached}건</strong></p></div>}
        <p className="marketing-demo-action">{leadingReason ? <>점검 제안 · 가장 많은 미수임 사유는 <b>{leadingReason.label}</b>입니다. {leadingReason.label === '비용 부담' ? '상담에서 비용 범위·절차·제공 가치를 더 명확하게 안내해 보세요.' : '해당 사유의 상담 과정을 먼저 점검해 보세요.'}</> : '기록된 상담 결과가 없어 개선 제안은 아직 제공하지 않습니다.'}</p>
      </section>
      <section className="panel marketing-demo-reasons" aria-label="시연 미수임 사유">
        <h2><Icon name="pie" />상담 후 미수임 사유 <small>{analysis.lostCount}건</small></h2>
        <p>상담 완료 중 미수임 결과가 기록된 문의 기준</p>
        {analysis.reasons.length ? <ol>{analysis.reasons.map((reason, index) => <li key={reason.label}>
          <span>{index + 1}. {reason.label}</span><b>{reason.count}건 · {marketingPercent(reason.share)}</b>
          <div className="marketing-demo-bar" aria-hidden="true"><i style={{ width: `${reason.share ?? 0}%` }} /></div>
        </li>)}</ol> : <div className="state">기록된 미수임 사유가 없습니다.</div>}
        <p className="marketing-demo-outcomes">수임 {analysis.retainedCount}건 · 미수임 {analysis.lostCount}건 · 결과 대기 {analysis.pendingCount}건</p>
      </section>
    </div>
    <p className="marketing-demo-footer">이 분석은 시연용입니다. 실제 분석을 위해서는 여정 자동 수집과 상담·수임 결과 기록 연결이 추가로 필요합니다. LLM 호출 없이 계산합니다.</p>
  </div>;
}
