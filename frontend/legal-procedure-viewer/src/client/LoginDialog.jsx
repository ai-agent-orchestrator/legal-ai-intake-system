import { useEffect, useRef } from 'react';
import LoginForm from './LoginForm.jsx';

export default function LoginDialog({ api, onLogin, onClose }) {
  const dialog = useRef(null);
  useEffect(() => { dialog.current.showModal(); }, []);
  return <dialog ref={dialog} className="intake-dialog" aria-labelledby="login-title" onCancel={onClose}><h2 id="login-title">로그인</h2><p>로그인 후 등록한 문의에 계정 정보가 연결됩니다.</p><LoginForm api={api} onLogin={onLogin} onCancel={onClose} variant="dialog"/></dialog>;
}
