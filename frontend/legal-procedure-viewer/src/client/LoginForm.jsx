import { useEffect, useRef, useState } from 'react';
import { authenticate } from './authModel.js';
import { Button, Feedback } from './components.jsx';
import Icon from './Icon.jsx';

export default function LoginForm({ api, onLogin, onCancel, variant = 'page', children }) {
  const request = useRef(null);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [visible, setVisible] = useState(false);
  const [phase, setPhase] = useState('');
  const [error, setError] = useState(null);
  const prefix = variant === 'page' ? 'screen01' : 'dialog';
  useEffect(() => () => request.current?.abort(), []);
  async function submit(event) {
    event.preventDefault(); if (request.current) return;
    const fields = new FormData(event.currentTarget);
    const name = String(fields.get('username') || ''); const secret = String(fields.get('password') || '');
    const controller = new AbortController(); request.current = controller;
    setPhase('login'); setError(null);
    try {
      const data = await authenticate(api, name, secret, controller.signal, next => { setPhase(next); setPassword(''); });
      if (!controller.signal.aborted) { setPassword(''); setVisible(false); onLogin(data); }
    } catch (failure) { if (!controller.signal.aborted) { setUsername(name); setPassword(''); setError(failure); } }
    finally { if (request.current === controller) { request.current = null; if (!controller.signal.aborted) setPhase(''); } }
  }
  const submitButton = <Button type="submit" disabled={!!phase} busy={!!phase}>{phase === 'profile' ? '계정 정보 확인 중' : phase ? '로그인 중' : '로그인'}</Button>;
  return <form className={`login-form login-form-${variant}`} aria-label="로그인" onSubmit={submit} noValidate>
    <div className="login-fields"><label htmlFor={`${prefix}-username`}><span className={variant === 'page' ? 'visually-hidden' : ''}>아이디</span><div className="login-input"><Icon name="user"/><input id={`${prefix}-username`} name="username" value={username} onChange={event => { setUsername(event.target.value); setError(null); }} required autoComplete="username" autoCapitalize="none" spellCheck={false} autoFocus disabled={!!phase} placeholder="이메일 또는 아이디 입력" aria-invalid={!!error?.fieldErrors?.username}/></div></label>
    <label htmlFor={`${prefix}-password`}><span className={variant === 'page' ? 'visually-hidden' : ''}>비밀번호</span><div className="login-input"><Icon name="lock"/><input id={`${prefix}-password`} name="password" aria-label="비밀번호" type={visible ? 'text' : 'password'} value={password} onChange={event => { setPassword(event.target.value); setError(null); }} required autoComplete="current-password" disabled={!!phase} placeholder="비밀번호 입력" aria-invalid={!!error?.fieldErrors?.password}/><button type="button" className="login-password-toggle" aria-label={visible ? '비밀번호 숨기기' : '비밀번호 표시'} aria-pressed={visible} onClick={() => setVisible(value => !value)} disabled={!!phase}><Icon name={visible ? 'eye' : 'eyeOff'}/></button></div></label></div>
    {variant === 'page' && submitButton}{children}
    <div className="login-feedback" aria-live="polite">{error && <Feedback error={error}/>}</div>
    {variant === 'dialog' && <div className="button-row"><Button type="button" className="secondary" onClick={onCancel}>닫기</Button>{submitButton}</div>}
  </form>;
}
