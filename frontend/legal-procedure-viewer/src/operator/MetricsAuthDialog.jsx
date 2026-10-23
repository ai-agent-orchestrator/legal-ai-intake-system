import { useEffect, useRef, useState } from 'react';

export default function MetricsAuthDialog({ state, username, onSubmit, onClose, onDisconnect, connected, title = 'AI 비용·성능 지표 연결 설정', description = 'C16 지표 조회는 ADMIN Basic 인증을 사용합니다. 계정은 현재 화면의 메모리에만 보관하며 화면을 이동하면 지웁니다.' }) {
  const dialog = useRef(null);
  const [draft, setDraft] = useState({ username: username || '', password: '' });
  useEffect(() => { const element = dialog.current; element.showModal(); return () => element.close(); }, []);
  return <dialog ref={dialog} className="security-auth-dialog" aria-labelledby="security-auth-title" onCancel={event => { event.preventDefault(); onClose(); }}>
    <h2 id="security-auth-title">{title}</h2><p>{description}</p>
    <form onSubmit={event => { event.preventDefault(); onSubmit({ username: draft.username.trim(), password: draft.password }); }}>
      <label htmlFor="metrics-user">관리자 계정</label><input id="metrics-user" autoFocus autoComplete="off" required pattern="[^:]+" value={draft.username} onChange={event => setDraft({ ...draft, username: event.target.value })} />
      <label htmlFor="metrics-password">비밀번호</label><input id="metrics-password" type="password" autoComplete="off" required value={draft.password} onChange={event => setDraft({ ...draft, password: event.target.value })} />
      {state.status === 'error' && <p className="security-auth-error" role="alert">{state.error}</p>}
      <div className="security-auth-actions"><button className="secondary" type="button" onClick={onClose}>닫기</button>{connected && <button className="secondary" type="button" onClick={onDisconnect}>연결 해제</button>}<button className="primary" disabled={state.status === 'loading'}>{state.status === 'loading' ? '조회 중…' : '연결하고 지표 조회'}</button></div>
    </form>
  </dialog>;
}
