import { useEffect, useMemo, useRef, useState } from 'react';
import ClassificationPage from './ClassificationPage.jsx';
import IntakeHeader from './IntakeHeader.jsx';
import { IntakeHelpPage, IntakeNotFoundPage, IntakeWritePage } from './IntakePages.jsx';
import IntakeReceiptPage, { IntakeReviewPage } from './IntakeReceiptPage.jsx';
import IntakeSearchPage from './IntakeSearchPage.jsx';
import IntakeListPage from './IntakeListPage.jsx';
import IntakeDetailPage from './IntakeDetailPage.jsx';
import IntakeConsultationPage from './IntakeConsultationPage.jsx';
import { EMPTY_LIST_FILTERS } from './intakeListModel.js';
import { createIntakeApi } from './api.js';
import { INITIAL_DRAFT, INITIAL_INTAKE_FORM, routeFromHash } from './model.js';
import LoginDialog from './LoginDialog.jsx';
import LoginPage, { AccountPage } from './LoginPage.jsx';
import { isStaff, loginDestination } from './authModel.js';

const PAGE_TITLES = { login: '로그인', account: '내 계정 정보', classify: '사건 유형 확인', write: '문의 작성', receipt: '문의 안내', review: '접수 내용 확인', search: '검색 요청 준비', 'intake-list': '접수된 문의', 'lawyer-detail': '문의 상세', consultation: '상태 변경 · 상담 메모', help: '도움말', 'not-found': '페이지를 찾을 수 없습니다' };

export default function IntakeApp() {
  const [route, setRoute] = useState(() => routeFromHash(window.location.hash));
  const [draft, setDraft] = useState(INITIAL_DRAFT);
  const [preview, setPreview] = useState(null);
  const [intakeForm, setIntakeForm] = useState(INITIAL_INTAKE_FORM);
  const [user, setUser] = useState(null);
  const [loginOpen, setLoginOpen] = useState(false);
  const [listFilters, setListFilters] = useState(EMPTY_LIST_FILTERS);
  const token = useRef('');
  const previousClassification = useRef(null);
  const api = useMemo(() => createIntakeApi({
    base: import.meta.env.VITE_INTAKE_API_BASE || '',
    getToken: () => token.current,
    onUnauthorized: () => { token.current = ''; setUser(null); },
  }), []);

  useEffect(() => {
    const change = () => { setRoute(routeFromHash(window.location.hash)); window.scrollTo({ top: 0 }); };
    window.addEventListener('hashchange', change);
    return () => window.removeEventListener('hashchange', change);
  }, []);
  useEffect(() => {
    setLoginOpen(false);
    document.title = PAGE_TITLES[route.name] + ' | Legal Intake';
    const main = document.querySelector('main');
    main?.setAttribute('tabindex', '-1');
    main?.focus({ preventScroll: true });
  }, [route]);

  function created(id) {
    setDraft(INITIAL_DRAFT); setIntakeForm(INITIAL_INTAKE_FORM); setPreview(null);
    previousClassification.current = null;
    window.location.hash = '/intake/' + id;
  }
  function classified(result) {
    setPreview(result);
    if (!result) return;
    const source = { inquiryPurpose: draft.inquiryPurpose, caseType: result.caseType, coreStatement: draft.coreStatement };
    // Repeating the same preview must not discard the longer Screen03 edits.
    // A value explicitly changed in Screen02 replaces only that corresponding field.
    const previous = previousClassification.current;
    const changed = Object.fromEntries(Object.entries(source).filter(([key, value]) => !previous || previous[key] !== value));
    setIntakeForm(current => ({ ...current, ...changed }));
    previousClassification.current = source;
  }
  function logout() { token.current = ''; setUser(null); setDraft(INITIAL_DRAFT); setIntakeForm(INITIAL_INTAKE_FORM); setPreview(null); }
  function signedIn(data, redirect = false) {
    token.current = data.accessToken;
    setUser({ username: data.username, role: data.role });
    setLoginOpen(false);
    if (redirect) window.location.hash = loginDestination(data.role);
  }
  function newInquiry() {
    setDraft(INITIAL_DRAFT); setIntakeForm(INITIAL_INTAKE_FORM); setPreview(null);
    previousClassification.current = null;
    window.location.hash = '/intake/type';
  }

  const lawyer = ['intake-list', 'lawyer-detail', 'consultation'].includes(route.name);
  const showReady = () => { setListFilters({ ...EMPTY_LIST_FILTERS, status: 'CONSULTATION_READY' }); window.location.hash = '/lawyer/intakes'; };
  if (route.name === 'login') return <div className="intake-app screen01"><a href="#main-content" className="skip-link" onClick={event => { event.preventDefault(); document.querySelector('main')?.focus(); }}>본문 바로가기</a><div id="main-content"><LoginPage api={api} user={user} onLogin={data => signedIn(data, true)}/></div></div>;
  return <div className={'intake-app ' + (lawyer ? 'screen06' + (route.name === 'consultation' ? ' screen07' : route.name === 'lawyer-detail' ? ' screen08' : '') : route.name === 'write' ? 'screen03' : route.name === 'receipt' ? 'screen04' : route.name === 'search' ? 'screen05' : '')}>
    <a href="#main-content" className="skip-link" onClick={event => { event.preventDefault(); document.querySelector('main')?.focus(); }}>본문 바로가기</a>
    <IntakeHeader route={route} user={user} onLogin={() => setLoginOpen(true)} onLogout={logout}/>
    <div id="main-content" className="intake-route" key={user ? user.username + user.role : 'anonymous'}>
      {!user && ['receipt', 'review', 'search', 'intake-list', 'lawyer-detail', 'consultation'].includes(route.name) ? <main className="intake-shell compact"><section className="intake-panel"><h1>로그인이 필요합니다.</h1><p>본인 문의와 배정된 문의만 확인할 수 있습니다. 작성 중인 내용은 로그인 후 이어서 사용할 수 있습니다.</p><button className="intake-button" onClick={() => setLoginOpen(true)}>로그인하고 계속하기</button></section></main>
        : route.name === 'consultation' && !isStaff(user) ? <main className="intake-shell compact"><section className="intake-panel"><h1>담당 변호사 전용 기능입니다.</h1><p>내부 상담 메모와 상태 변경은 담당 변호사와 사무소 관리자만 사용할 수 있습니다.</p><a href="#/lawyer/intakes">내 문의 목록으로</a></section></main>
        : route.name === 'account' ? <AccountPage api={api} user={user}/>
        : route.name === 'intake-list' ? <IntakeListPage user={user} api={api} filters={listFilters} setFilters={setListFilters}/>
        : route.name === 'lawyer-detail' ? <IntakeDetailPage key={route.id} api={api} id={route.id} user={user} onLogin={() => setLoginOpen(true)} onReady={showReady}/>
        : route.name === 'consultation' ? <IntakeConsultationPage key={route.id} api={api} id={route.id} onReady={showReady}/>
        : route.name === 'classify' ? <ClassificationPage api={api} draft={draft} setDraft={setDraft} preview={preview} setPreview={classified}/>
        : route.name === 'write' ? <IntakeWritePage api={api} draft={draft} preview={preview} form={intakeForm} setForm={setIntakeForm} onCreated={created}/>
          : route.name === 'receipt' ? <IntakeReceiptPage key={route.id} api={api} id={route.id} onNewInquiry={newInquiry}/>
            : route.name === 'review' ? <IntakeReviewPage key={route.id} api={api} id={route.id} onNewInquiry={newInquiry}/>
              : route.name === 'search' ? <IntakeSearchPage key={route.id} api={api} id={route.id} requestId={route.searchRequestId} user={user} onLogin={() => setLoginOpen(true)}/>
            : route.name === 'help' ? <IntakeHelpPage/> : <IntakeNotFoundPage/>}
    </div>
    {loginOpen && <LoginDialog api={api} onClose={() => setLoginOpen(false)} onLogin={signedIn}/>}
  </div>;
}
