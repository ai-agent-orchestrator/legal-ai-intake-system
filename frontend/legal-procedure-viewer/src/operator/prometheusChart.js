import { behaviorMetrics } from './observabilityMetrics.js';

export const chartTime = seconds => new Date(seconds * 1000).toLocaleTimeString('ko-KR', {
  timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', hour12: false,
});
export const chartValue = value => Number(value.toFixed(2)).toLocaleString('ko-KR');

export function patternSeries(pattern, response) {
  if (!response) return [];
  return response.series.filter(row => pattern.keys.includes(row.metric.signal)).map(row => ({
    ...behaviorMetrics[row.metric.signal],
    labels: Object.entries(row.metric).filter(([key]) => !['signal', '__name__', 'job', 'instance', 'application'].includes(key))
      .map(([key, value]) => `${key}=${value}`).join(' · '),
    points: row.values.map(([time, text]) => ({ time, value: text.trim() && Number.isFinite(Number(text)) && Number(text) >= 0 ? Number(text) : null }))
      .filter(point => point.time >= response.start && point.time <= response.end).sort((a, b) => a.time - b.time),
  }));
}

export function linePath(points, { start, end, stepSeconds }, max, width = 480, height = 90) {
  let path = '', previous = null;
  for (const point of points) {
    if (point.value == null) { previous = null; continue; }
    const x = 30 + (point.time - start) / (end - start) * (width - 38);
    const y = 7 + (1 - point.value / max) * (height - 24);
    const move = previous == null || point.time - previous > stepSeconds * 1.5;
    path += `${move ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)} `;
    previous = point.time;
  }
  return path.trim();
}

export function scrapeState(response) {
  if (!response) return '미연결';
  const values = response.series.filter(row => row.metric.signal === 'scrape_up')
    .map(row => row.values.at(-1)).filter(Boolean);
  if (!values.length || values.some(([at]) => response.end - at > 45)) return '수집 대상 미확인';
  return values.every(([, value]) => value === '1') ? '수집 중' : '수집 대상 연결 오류';
}
