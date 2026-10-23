import * as api from './operatorApi.js';
import Icon from './Icon.jsx';

export const number = value => value == null ? '—' : Number(value).toLocaleString('ko-KR');
export const date = value => value ? String(value).replace('T', ' ').slice(0, 19) : '—';
export const statusNames = { OPEN: '미확인', ACKNOWLEDGED: '확인됨', RESOLVED: '해결됨', HIGH: '높음', MEDIUM: '보통', LOW: '낮음' };
export function PageRefresh({ item }) {
  return <div className="page-tools"><p><Icon name="clock" />{item.at ? `조회 시각 ${item.at.toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })}` : '저장 기록 조회 중'}</p><button className="refresh-button" disabled={item.status === 'loading'} onClick={() => item.reload()}><Icon name="refresh" />새로고침</button></div>;
}
export function Badge({ children }) {
  return <span className={`badge tone-${String(children).toLowerCase()}`}>{statusNames[children] || children || '—'}</span>;
}
export function Panel({ title, subtitle, children, className = '' }) {
  return <section className={`panel ${className}`}><div className="panel-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div></div>{children}</section>;
}
export function Resource({ item, children, empty = false }) {
  if (item.status === 'idle' || item.status === 'loading') return <div className="state" role="status"><span className="spinner" />기록을 불러오는 중입니다.</div>;
  if (item.status === 'error') return <div className="state error" role="alert"><p>{item.error}</p><button className="secondary" onClick={() => item.reload()}>다시 조회</button></div>;
  if (empty) return <div className="state">조회 조건에 맞는 기록이 없습니다.</div>;
  return children(item.data);
}
export function Stat({ label, value, foot, accent, icon, unit, href }) {
  const content = <>{icon && <span className="stat-icon"><Icon name={icon} /></span>}<div className="stat-body"><span>{label}</span><strong>{number(value)}{value != null && unit && <span className="stat-unit">{unit}</span>}</strong></div><small title={foot}>{foot}</small>{href && <Icon name="chevron-right" className="stat-link-icon" />}</>;
  return href ? <a className={`stat ${accent || ''}`} href={href}>{content}</a> : <article className={`stat ${accent || ''}`}>{content}</article>;
}
export function RiskBars({ data }) {
  return <div className="risk-bars">{[['HIGH', data.high, 'high'], ['MEDIUM', data.medium, 'medium'], ['LOW', data.low, 'low']].map(([label, value, color]) => <div key={label} className="risk-row"><Badge>{label}</Badge><div className="bar-track"><i className={color} style={{ width: `${data.total ? value / data.total * 100 : 0}%` }} /></div><b>{number(value)}</b></div>)}<p className="caption">전체 위험 판단 {number(data.total)}건 · 현재 DB 전체 기준</p><div className="distribution-grid"><Distribution title="판정별" data={data.decisionCounts} /><Distribution title="권고 조치별" data={data.recommendedActionCounts} /></div></div>;
}
export function Distribution({ title, data }) {
  const entries = Object.entries(data || {}).sort((a, b) => b[1] - a[1]);
  const max = Math.max(1, ...entries.map(([, count]) => count));
  return <div className="distribution"><h3>{title}</h3>{entries.length ? entries.map(([label, count]) => <div className="distribution-row" key={label}><div><span>{label || '미지정'}</span><b>{number(count)}</b></div><div className="bar-track"><i style={{ width: `${count / max * 100}%` }} /></div></div>) : <p className="caption">집계된 값이 없습니다.</p>}</div>;
}
export function Metrics({ samples, at }) {
  const value = (name, labels) => api.metricValue(samples, name, labels);
  const money = amount => amount == null ? '—' : `$${amount.toFixed(4)}`;
  const average = stage => {
    const prefix = stage ? 'ai_chat_stage_duration_seconds' : 'ai_chat_duration_seconds';
    const labels = stage ? { stage } : {};
    const count = value(`${prefix}_count`, labels), sum = value(`${prefix}_sum`, labels);
    return count > 0 && sum != null ? `${(sum / count * 1000).toFixed(0)} ms` : '—';
  };
  return <div className="metric-results"><div className="metric-grid">{[
    ['정산 합계', money(value('ai_cost_accounted_usd'))], ['보고된 실제 비용', money(value('ai_cost_actual_usd'))],
    ['사용량 미확인 추정 비용', money(value('ai_cost_estimated_usage_usd'))], ['현재 예약 비용', money(value('ai_cost_reserved_usd'))],
    ['월 예산', money(value('ai_cost_budget_usd'))], ['일 예산', money(value('ai_cost_daily_budget_usd'))], ['세션 예산', money(value('ai_cost_session_budget_usd'))],
  ].map(([label, amount]) => <div key={label}><small>{label}</small><b>{amount}</b></div>)}</div><h3>동기 Chat 평균 처리 시간</h3><div className="timing-row">{[['전체', null], ['입력 검사', 'input_guardrail'], ['LLM', 'llm'], ['출력 검사', 'output_guardrail']].map(([label, stage]) => <div key={label}><small>{label}</small><b>{average(stage)}</b></div>)}</div><p className="caption">조회 {at.toLocaleTimeString('ko-KR')} · 누적 Timer의 합계÷횟수 · 결측 또는 호출 0회는 —<br />정산 합계에는 추정 비용이 포함됩니다. 세션 예산은 요청 1회 한도가 아니며 SSE 전체 지연이나 P95를 뜻하지 않습니다.</p></div>;
}
