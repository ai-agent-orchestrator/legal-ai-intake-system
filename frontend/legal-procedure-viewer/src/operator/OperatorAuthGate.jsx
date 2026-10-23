import { useEffect, useRef, useState } from 'react';
import * as api from './operatorApi.js';
import LoginLayout from '../shared/LoginLayout.jsx';
import Icon from '../client/Icon.jsx';

export default function OperatorAuthGate({ defaultBase, children }) {
  const [base, setBase] = useState(defaultBase);
  const [user, setUser] = useState(() => api.operatorSession(defaultBase));
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [visible, setVisible] = useState(false);
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState(() => new URLSearchParams(location.search).get('mock') === '1' && location.hash === '#/observability');
  const active = useRef(null);
  useEffect(() => api.subscribeOperatorSession(reason => { setUser(api.operatorSession(base)); setNotice(reason); }), [base]);
  useEffect(() => () => active.current?.abort(), []);
  useEffect(() => {
    const change = () => { if (location.hash !== '#/observability') setPreview(false); };
    window.addEventListener('hashchange', change);
    return () => window.removeEventListener('hashchange', change);
  }, []);
  function requireLogin() {
    setPreview(false);
    const url = new URL(location.href); url.searchParams.delete('mock');
    window.history.replaceState(null, '', url);
  }
  function logout() {
    active.current?.abort(); active.current = null;
    api.clearOperatorSession(); setUser(null); setPassword(''); setVisible(false); setBusy(false); requireLogin();
  }
  function reconnect(target) {
    logout(); setBase(api.normalizeApiBase(target)); setNotice('변경한 서버의 관리자 계정으로 로그인해 주세요.');
  }
  async function submit(event) {
    event.preventDefault(); if (active.current) return;
    const controller = new AbortController(); active.current = controller;
    setBusy(true); setNotice('');
    try {
      const result = await api.loginOperator(base, username, password, controller.signal);
      if (!controller.signal.aborted) { setUser(result); setPassword(''); }
    } catch (error) { if (!controller.signal.aborted) { setNotice(error.message); setPassword(''); } }
    finally { if (active.current === controller) { active.current = null; if (!controller.signal.aborted) { setBusy(false); setVisible(false); } } }
  }
  if (user || preview) return children({ base, user, preview: !user && preview, logout, reconnect, requireLogin });
  return <div className="operator-auth screen01"><LoginLayout titleId="operator-login-title" roleTitle="관리자 로그인" description="사무소의 운영 현황과 AI 위험을 확인하세요.">
    <form onSubmit={submit} aria-label="관리자 로그인" className="login-form login-form-page">
      <div className="login-fields"><label><span className="sr-only">아이디</span><div className="login-input"><Icon name="user"/><input name="username" autoComplete="username" autoCapitalize="none" spellCheck={false} placeholder="관리자 아이디 입력" value={username} disabled={busy} onChange={event => setUsername(event.target.value)} required autoFocus /></div></label>
      <label><span className="sr-only">비밀번호</span><div className="login-input"><Icon name="lock"/><input name="password" type={visible ? 'text' : 'password'} autoComplete="current-password" placeholder="비밀번호 입력" value={password} disabled={busy} onChange={event => setPassword(event.target.value)} required /><button type="button" className="login-password-toggle" aria-label={visible ? '비밀번호 숨기기' : '비밀번호 표시'} aria-pressed={visible} disabled={busy} onClick={() => setVisible(value => !value)}><Icon name={visible ? 'eye' : 'eyeOff'}/></button></div></label></div>
      <button type="submit" className="intake-button primary" disabled={busy}>{busy ? '로그인 확인 중' : '관리자 로그인'}</button>
      <div className="login-feedback" aria-live="polite">{notice && <p className="operator-login-error" role="alert">{notice}</p>}</div>
      <details className="operator-login-server"><summary>서버 연결 설정</summary><label>API 서버<input aria-label="API 서버" type="url" value={base} disabled={busy} onChange={event => setBase(event.target.value)} required /></label></details>
    </form>
    <footer className="login-security"><p><Icon name="lock"/>JWT 인증 및 관리자 권한 확인</p><small>운영 현황과 위험 이력은 사무소 관리자만 조회할 수 있습니다.<br/>지표 연결은 로그인 후 별도 인증으로 진행합니다.</small></footer>
    <a className="operator-login-back" href="intake.html#/login">의뢰인·변호사 로그인으로 돌아가기</a>
  </LoginLayout></div>;
}
