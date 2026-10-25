import { useState } from 'react';
import * as api from './operatorApi.js';
import { useResource } from './useResource.js';
import { number, PageRefresh, Resource, Stat } from './components.jsx';
import Icon from './Icon.jsx';
import MarketingDemoPanel from './MarketingDemoPanel.jsx';

const stageLabels = {
  VISIT: '방문', INTAKE_STARTED: '문의 작성 시작', INTAKE_SUBMITTED: '문의 접수',
  SUMMARY_VIEWED: '안내 확인', LAWYER_SEARCH_REQUESTED: '변호사 검색 요청', CONSULTATION_READY: '상담 준비',
};
const measurementLabels = { REPORTED_EVENT: '보고된 이벤트', STORED_INTAKE: '저장된 문의', DISTINCT_INTAKE: '고유 문의', CURRENT_STATUS: '현재 상태' };
const caseLabels = { CIVIL: '민사', FAMILY: '가사', CRIMINAL: '형사', ELECTRONIC_LITIGATION: '전자소송', UNKNOWN: '미분류' };

function MarketingDistribution({ title, icon, values, total, labels = {} }) {
  const entries = Object.entries(values || {}).sort((a, b) => b[1] - a[1]);
  return <section className="panel marketing-distribution">
    <h2><Icon name={icon} /><span>{title} <small>(저장된 문의 기준)</small></span></h2>
    {entries.length ? <div className="marketing-distribution-rows">{entries.map(([key, count]) => {
      const share = total > 0 ? count / total * 100 : null;
      return <div className="marketing-distribution-row" key={key}>
        <span title={key}>{labels[key] || key || '미지정'}</span>
        <div className="bar-track"><i style={{ width: `${share ?? 0}%` }} /></div>
        <b>{number(count)}건{share != null && <small> ({Math.round(share)}%)</small>}</b>
      </div>;
    })}</div> : <div className="state">저장된 문의가 없어 분포가 없습니다.</div>}
  </section>;
}

export default function MarketingPage({ base }) {
  const [demo, setDemo] = useState(false);
  const marketing = useResource(api.getMarketingFunnel, base, { enabled: !demo });
  const basis = marketing.data?.measurementBasis;
  return <section className="marketing-page overview-page" aria-label="마케팅 집계">
    <div className="page-heading marketing-heading">
      <div><div className="eyebrow">운영 관리 <span aria-hidden="true">›</span> 마케팅 집계</div><h1 tabIndex="-1">마케팅 집계</h1><p>{demo ? '어디에서 줄어들고, 왜 수임으로 이어지지 않을까요?' : <>의뢰인의 문의 여정을 단계별로 확인할 수 있습니다. {basis === 'ALL_RECORDS_IN_CURRENT_DATABASE' && '(현재 DB 전체 기록 기준)'}</>}</p></div>
      <div className="marketing-actions"><a className="back-to-overview" href="#/overview"><Icon name="arrow-left" />운영 요약으로</a>{!demo && <PageRefresh item={marketing} />}</div>
    </div>
    <div className="marketing-mode" role="group" aria-label="마케팅 자료 선택"><button type="button" aria-pressed={!demo} onClick={() => setDemo(false)}>실제 집계</button><button type="button" aria-pressed={demo} onClick={() => setDemo(true)}>시연 분석</button><span>{demo ? '가상 문의 40건 · 전환 분석 시연' : '기존 C13 API · 현재 DB 기준'}</span></div>
    {demo ? <MarketingDemoPanel /> : <Resource item={marketing}>{value => <>
      <div className="stat-grid marketing-stats">
        <Stat label="접수된 문의" value={value.submittedIntakeCount} foot="저장된 문의 수입니다." icon="document" unit="건" />
        <Stat label="검색 요청 건수" value={value.searchRequestCount} foot="저장된 검색 요청 수입니다." icon="search" unit="건" accent="amber" />
        <Stat label="검색을 요청한 문의 수" value={value.searchRequestedIntakeCount} foot="검색을 요청한 고유 문의 수입니다." icon="users" unit="건" accent="mint" />
        <Stat label="현재 상담 준비 상태" value={value.consultationReadyIntakeCount} foot="현재 상담 준비 상태의 문의 수입니다." icon="calendar" unit="건" accent="danger" />
      </div>
      <section className="marketing-journey" aria-label="의뢰인 문의 여정 단계별 현황">
        <h2><Icon name="journey" />의뢰인 문의 여정 단계별 현황</h2>
        <ol className="marketing-journey-grid">{value.stages.map((item, index) => <li key={item.stage} className={`marketing-stage ${['INTAKE_STARTED', 'SUMMARY_VIEWED'].includes(item.stage) ? 'stage-amber' : item.stage === 'CONSULTATION_READY' ? 'stage-green' : ''}`}>
          <div className="marketing-stage-title"><span className="marketing-stage-number">{String(index + 1).padStart(2, '0')}</span><h3>{stageLabels[item.stage] || item.stage}</h3></div>
          <strong>{number(item.count)}<small>건</small></strong>
          <p>{measurementLabels[item.measurement] || item.measurement}<small>({item.stage})</small></p>
          {index < value.stages.length - 1 && <Icon name="chevron-right" className="stage-arrow" />}
        </li>)}</ol>
      </section>
      <div className="marketing-distributions">
        <MarketingDistribution title="사건 유형 분포" icon="pie" values={value.caseTypeDistribution} total={value.submittedIntakeCount} labels={caseLabels} />
        <MarketingDistribution title="원하는 다음 단계 분포" icon="users" values={value.desiredNextStepDistribution} total={value.submittedIntakeCount} />
        <MarketingDistribution title="선호 지역 분포" icon="pin" values={value.regionDistribution} total={value.submittedIntakeCount} />
      </div>
      <aside className="marketing-measurement-note" aria-label="집계 기준 안내">
        <Icon name="info" />
        <div><h2>{value.dropOffMeasured === false ? '단계별 집계 기준이 달라 전환율·이탈률은 아직 측정하지 않습니다.' : '단계별 집계 기준을 확인해 주세요.'}</h2><p>각 단계는 서로 다른 측정 기준(보고된 이벤트, 저장된 문의, 고유 문의, 현재 상태)을 사용합니다. 단계 간 전환율이나 이탈률을 계산하지 않습니다.</p></div>
        <p className="measurement-basis"><span>집계 기준</span>{basis === 'ALL_RECORDS_IN_CURRENT_DATABASE' ? '현재 DB 전체 기록 기준' : '서버 응답 기준'}<small>({basis || '집계 기준 미제공'})</small></p>
      </aside>
    </>}</Resource>}
  </section>;
}
