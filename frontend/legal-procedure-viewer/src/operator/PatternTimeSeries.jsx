import { behaviorMetrics } from './observabilityMetrics.js';
import { chartTime, chartValue, patternSeries, linePath } from './prometheusChart.js';

export default function PatternTimeSeries({ pattern, resource }) {
  const data = resource.status === 'ok' ? resource.data : null;
  const series = patternSeries(pattern, data);
  const points = series.flatMap(row => row.points).filter(point => point.value != null);
  const max = Math.max(1, ...points.map(point => point.value));
  const label = `pattern-chart-${pattern.id}`;
  return <div className="obs-pattern-graph">
    <div className="obs-chart-caption"><span>최근 1시간 · 각 시점의 직전 5분 증가량</span></div>
    {points.length ? <svg viewBox="0 0 480 90" role="img" aria-labelledby={label} preserveAspectRatio="none">
      <title id={label}>{pattern.title} · {data.query === 'MOCK_ONLY' ? '모의 시계열 예시' : 'Prometheus 실제 시계열'} · {series.length}개 시리즈. 신호별 증가량이며 합산 패턴 건수가 아닙니다.</title>
      {[0, .5, 1].map(ratio => <g key={ratio}><line x1="30" x2="472" y1={7 + ratio * 66} y2={7 + ratio * 66} /><text x="25" y={10 + ratio * 66} textAnchor="end">{chartValue(max * (1 - ratio))}</text></g>)}
      {series.map((row, i) => <g key={i}><path className="obs-series-line" d={linePath(row.points, data, max)} stroke={row.color} strokeDasharray={i >= pattern.keys.length ? '4 2' : undefined}><title>{row.label} · {row.labels || '전체'}</title></path>{row.points.filter(point => point.value != null).length === 1 && row.points.filter(point => point.value != null).map(point => <circle key={point.time} cx={30 + (point.time - data.start) / (data.end - data.start) * 442} cy={7 + (1 - point.value / max) * 66} r="2" fill={row.color} />)}</g>)}
      <text x="30" y="87">{chartTime(data.start)}</text><text x="251" y="87" textAnchor="middle">{chartTime((data.start + data.end) / 2)}</text><text x="472" y="87" textAnchor="end">{chartTime(data.end)}</text>
    </svg> : <div className="obs-graph-empty" role="status">{resource.status === 'loading' ? '시계열 조회 중…' : resource.status === 'error' ? '시계열 연결 실패 · 다시 조회해 주세요' : resource.status === 'idle' ? '지표 연결 후 실제 시계열 표시' : '관측 없음 · 수집 후 두 번 이상의 샘플이 필요합니다'}</div>}
    <div className="obs-chart-legend">{pattern.keys.map(key => <span key={key} style={{ '--series-color': behaviorMetrics[key].color }} title={behaviorMetrics[key].name}><i />{behaviorMetrics[key].label}{data && !series.some(row => row.name === behaviorMetrics[key].name && row.points.some(point => point.value != null)) ? ' · 미관측' : ''}</span>)}</div>
  </div>;
}
