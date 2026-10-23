import { useEffect, useRef, useState } from 'react';
import { Button, Feedback } from './components.jsx';
import Icon from './Icon.jsx';

export default function IntakeAssignment({ api, intake, reload, onSaved }) {
  const [username, setUsername] = useState(intake.assignedLawyerUsername || '');
  const [pending, setPending] = useState(false), [error, setError] = useState(null), [notice, setNotice] = useState('');
  const active = useRef(null);
  useEffect(() => () => active.current?.abort(), []);
  async function save(event) {
    event.preventDefault(); if (active.current) return;
    const controller = new AbortController(); active.current = controller;
    setPending(true); setError(null); setNotice('');
    try {
      const result = await api.assignLawyer(intake.id, username.trim() || null, controller.signal);
      if (controller.signal.aborted) return;
      setUsername(result.assignedLawyerUsername || '');
      setNotice(result.assignedLawyerUsername ? '담당 변호사를 배정했습니다.' : '담당 배정을 해제했습니다.');
      onSaved?.(result); reload();
    } catch (failure) { if (!controller.signal.aborted) { setError(failure); if ([401, 403].includes(failure.status)) reload(); } }
    finally { if (!controller.signal.aborted) { active.current = null; setPending(false); } }
  }
  return <section className="detail-card intake-assignment"><h2><Icon name="user"/>담당 변호사 배정 <small>관리자 전용</small></h2>
    <p>현재 담당: <b>{intake.assignedLawyerUsername || '미배정'}</b></p>
    <form onSubmit={save}><label htmlFor="assigned-lawyer">변호사 계정</label><input id="assigned-lawyer" autoComplete="off" maxLength={100} pattern="[A-Za-z0-9_.-]+" placeholder="등록된 변호사 아이디" value={username} onChange={event => setUsername(event.target.value)} disabled={pending}/><p className="field-hint">LAWYER 계정 1명을 배정합니다. 비워서 저장하면 배정이 해제됩니다.</p><Button disabled={pending}>{pending ? '배정 저장 중…' : '담당 배정 저장'}</Button></form>
    {error && <Feedback error={error} retry={reload}/>}{notice && <p role="status">{notice}</p>}
  </section>;
}
