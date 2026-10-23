import { useEffect, useState } from 'react';
import Icon from './Icon.jsx';

export function Button({ children, className = '', busy = false, ...props }) {
  return <button className={`intake-button ${className}`} aria-busy={busy || undefined} {...props}>{busy ? <span className="intake-spinner" /> : null}{children}</button>;
}
export function Feedback({ error, retry }) {
  return <div className="intake-feedback" role="alert"><Icon name="info"/><div><p>{error.message || error}</p>{error.traceId && <small>문의 참조 번호: {error.traceId}</small>}{retry && <button type="button" onClick={retry}>다시 시도</button>}</div></div>;
}
export function Steps({ current = 1 }) {
  return <ol className="intake-steps" aria-label="사건 접수 진행 단계">{['사건 유형', '문의 작성', '안내 확인', '검색 요청'].map((label, index) => <li key={label} className={index + 1 <= current ? 'active' : ''} aria-current={current === index + 1 ? 'step' : undefined}><span>{index + 1 < current ? <Icon name="check"/> : index + 1}</span><b>{label}</b></li>)}</ol>;
}
export function ChoiceGroup({ legend, hint, options, value, onChange, disabled }) {
  return <fieldset className="intake-choices" disabled={disabled}><legend>{legend}{hint && <small><Icon name="info"/>{hint}</small>}</legend><div className="choice-grid">{options.map(option => <label key={option.value} className={`choice-card ${value === option.value ? 'selected' : ''}`}><input type="radio" name={options[0].value} value={option.value} checked={value === option.value} onChange={() => onChange(option.value)}/><Icon name={option.icon}/><strong>{option.label || option.value}</strong><span>{option.description}</span>{value === option.value && <i><Icon name="check"/></i>}</label>)}</div></fieldset>;
}
export function useResource(load, dependencies) {
  const [resource, setResource] = useState({ status: 'loading', data: null, error: null });
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setResource({ status: 'loading', data: null, error: null });
    Promise.resolve().then(() => load(controller.signal)).then(data => {
      if (!controller.signal.aborted) setResource({ status: 'success', data, error: null });
    }).catch(error => {
      if (!controller.signal.aborted) setResource({ status: 'error', data: null, error });
    });
    return () => controller.abort();
    // The caller supplies all values captured by load.
  }, [...dependencies, revision]);
  return { ...resource, retry: () => setRevision(value => value + 1) };
}
