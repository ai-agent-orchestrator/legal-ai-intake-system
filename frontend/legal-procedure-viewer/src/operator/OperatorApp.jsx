import { useEffect, useRef, useState } from 'react';
import * as api from './operatorApi.js';
import { useResource } from './useResource.js';
import { AuditPage, MarketingPage, ObservabilityPage, OverviewPage, PageRefresh, SecurityPage } from './pages.jsx';
import { readOperatorRoute } from './operatorRoute.js';
import Icon from './Icon.jsx';
import BrandMark from './BrandMark.jsx';
import OperatorAuthGate from './OperatorAuthGate.jsx';

const pages = [
  { id: 'overview', n: '09', name: '운영 요약', icon: 'home', sub: 'Overview', title: '운영 현황을 한눈에 확인하세요.', description: '안전하고 투명한 법률 상담 운영을 위한 주요 지표입니다.' },
  { id: 'marketing', n: '10', name: '마케팅 집계', sub: 'Intake journey', title: '접수 여정과 관심 분야.', description: '행동 이벤트와 저장된 문의를 각각의 집계 기준으로 읽습니다.' },
  { id: 'security', n: '11', name: '보안·위험·비용 지표', sub: 'Security & cost', title: '안전한 운영을 위한 관측.', description: '보안 이벤트와 위험 집계, AI 비용과 처리 시간을 살펴봅니다.' },
  { id: 'audit', n: '12', name: '위험 이력·Incident 대응', sub: 'Audit & response', title: '판단 근거에서 후속 조치까지.', description: '위험 이력을 추적하고 Incident의 확인·해결 상태를 갱신합니다.' },
  { id: 'observability', n: '13', name: 'AI 위험 요약', icon: 'chart', sub: 'Observability', title: 'Dynamic Guard', description: '위험한 요청과 반복 행동을 두 가지 기준으로 확인합니다.' },
];
// Operator pages always use the main server, independent of the intake demo build.
const defaultBase = 'http://127.0.0.1:8080';

export default function OperatorApp() {
  return <OperatorAuthGate defaultBase={defaultBase} fixedBase>{props => <OperatorWorkspace key={`${props.base}:${props.user?.revision || 'preview'}`} {...props} />}</OperatorAuthGate>;
}

function OperatorWorkspace({ base: initialBase, user, preview, logout, reconnect, requireLogin }) {
  const [hash, setHash] = useState(() => location.hash);
  const { route, riskId: requestedRiskId } = readOperatorRoute(hash);
  const baseInput = initialBase;
  const base = initialBase;
  const [baseRevision, setBaseRevision] = useState(0);
  const [settings, setSettings] = useState(false);
  const [error, setError] = useState(null);
  const [locked, setLocked] = useState(false);
  const accountMenu = useRef(null);
  const page = pages.find(item => item.id === route);
  const summary = useResource(api.getAdminDashboardSummary, base, { enabled: route === 'overview' });
  useEffect(() => {
    if (!location.hash || location.hash === '#/') location.replace('#/overview');
    const change = () => {
      if (readOperatorRoute(location.hash).redirect) location.replace('#/observability');
      else setHash(location.hash);
    };
    change();
    window.addEventListener('hashchange', change);
    return () => window.removeEventListener('hashchange', change);
  }, []);
  useEffect(() => {
    if (accountMenu.current) accountMenu.current.open = false;
    const frame = window.requestAnimationFrame(() => {
      window.scrollTo(0, 0);
      document.querySelector('main h1')?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [route, requestedRiskId]);
  useEffect(() => {
    const closeOutside = event => {
      const menu = accountMenu.current;
      if (menu?.open && !menu.contains(event.target)) menu.open = false;
    };
    const closeOnEscape = event => {
      const menu = accountMenu.current;
      if (event.key === 'Escape' && menu?.open) {
        menu.open = false;
        menu.querySelector('summary')?.focus();
      }
    };
    document.addEventListener('pointerdown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    document.addEventListener('scroll', closeOutside, true);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
      document.removeEventListener('scroll', closeOutside, true);
    };
  }, []);
  function connect(event) {
    event.preventDefault();
    if (locked) return;
    try {
      const target = api.normalizeApiBase(baseInput);
      setError(null);
      if (target === base) {
        if (route === 'overview') summary.reload();
        else setBaseRevision(value => value + 1);
      } else reconnect(target);
      setSettings(false);
    } catch (cause) { setError(cause.message); }
  }
  return <div className={`operator-app operator-draft operator-screen-${route}`}>
    <div className="operator-masthead"><picture><source media="(max-width: 900px)" srcSet={new URL('./assets/operator-thumbs-up-source.png', import.meta.url).href} /><img src={new URL('./assets/operator-approved-reference.png', import.meta.url).href} alt="법률 사무실에서 상담자와 의뢰인이 접수 화면을 함께 확인하는 모습" /></picture></div>
    <aside className="sidebar">
      <a className="brand" href="#/overview" aria-label="Legal Intake 운영 요약" aria-disabled={locked} onClick={event => { if (locked) event.preventDefault(); }}><BrandMark /><span>Security-Aware<br />Legal AI Intake System</span></a>
      <nav aria-label="운영자 메뉴">{pages.map(item => <a key={item.id} href={`#/${item.id}`} className={route === item.id ? 'active' : ''} aria-current={route === item.id ? 'page' : undefined} aria-disabled={locked} onClick={event => { if (locked) event.preventDefault(); }}><Icon name={item.icon || ({ marketing: 'chart', security: 'shield', audit: 'audit' })[item.id]} /><span>{item.id === 'security' ? '보안·비용' : item.id === 'audit' ? '위험 이력·Incident' : item.name}</span></a>)}</nav>
      <div className="sidebar-bottom"><a href="./" aria-disabled={locked} onClick={event => { if (locked) event.preventDefault(); }}>법률 절차 Previewer ↗</a></div>
    </aside>
    <main>
      <header className="topbar"><details ref={accountMenu} className="operator-menu" onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) event.currentTarget.open = false; }}><summary><Icon name="user" /><span>{preview ? '모의 화면' : `${user.username} · 관리자`}</span><Icon name="chevron-down" /></summary><div className="operator-menu-content"><p>운영자 워크스페이스</p><small>{preview ? '서버 자료를 조회하지 않는 모의 화면입니다.' : '관리자 계정으로 연결되어 있습니다.'}</small><button className="quiet-button" disabled={locked} aria-expanded={settings} onClick={() => { accountMenu.current.open = false; setSettings(!settings); }}>고급 설정 · 서버 주소</button><button className="quiet-button" onClick={logout}>{preview ? '관리자 로그인' : '로그아웃'}</button></div></details></header>
      {settings && <section className="settings"><form onSubmit={connect}><label htmlFor="api-base">Spring Boot API 서버 · 8080 고정</label><div className="input-row"><input id="api-base" type="url" required value={baseInput} disabled={locked} readOnly /><button className="primary" disabled={locked}>새로 조회</button></div></form><p>운영자 API 서버는 8080으로 고정됩니다. 업무 API는 ADMIN JWT, 지표 조회는 별도 ADMIN Basic 인증을 사용합니다.</p></section>}
      <div className="content">
        {error && <div className="notice error" role="alert">{error}</div>}
        {page ? <>{!['marketing', 'security', 'audit', 'observability'].includes(route) && <div className="page-heading" key={page.id}><div><div className="eyebrow">{route === 'overview' ? '운영 대시보드' : `운영자 · ${page.n}`}</div><h1 tabIndex="-1">{page.title}</h1><p>{page.description}</p></div>{route === 'overview' && <PageRefresh item={summary} />}</div>}
          {route === 'overview' && <OverviewPage summary={summary} />}
          {route === 'marketing' && <MarketingPage key={`${base}:${baseRevision}`} base={base} />}
          {route === 'security' && <SecurityPage key={`${base}:${baseRevision}`} base={base} />}
          {route === 'audit' && <AuditPage key={`${base}:${baseRevision}`} base={base} refreshSummary={summary.reload} setLocked={setLocked} requestedRiskId={requestedRiskId} />}
          {route === 'observability' && <ObservabilityPage key={`${base}:${baseRevision}`} base={base} onRequireLogin={preview ? requireLogin : undefined} />}
        </> : <section className="panel"><h1>페이지를 찾을 수 없습니다.</h1><p>운영자 메뉴에서 화면을 선택해 주세요.</p><a className="text-button" href="#/overview">운영 요약으로 이동 →</a></section>}
        <footer><span>LEGAL INTAKE / OPERATOR WORKSPACE</span><span>저장 기록·서버 지표 기반</span></footer>
      </div>
    </main>
  </div>;
}
