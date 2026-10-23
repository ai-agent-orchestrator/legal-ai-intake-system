import React from 'react';
import { createRoot } from 'react-dom/client';
import IntakeApp from './IntakeApp.jsx';
import './intake.css';
import './intake-reference.css';
import './intake-write.css';
import './intake-receipt.css';
import './intake-list.css';
import './intake-consultation.css';
import './intake-detail.css';
import './intake-search.css';
import './intake-login.css';

class ErrorBoundary extends React.Component {
  state = { error: false };
  static getDerivedStateFromError() { return { error: true }; }
  render() {
    return this.state.error ? <main className="intake-shell compact"><section className="intake-panel"><h1>화면을 표시하지 못했습니다.</h1><p>새로고침 후 다시 시도해 주세요.</p><button className="intake-button" onClick={() => window.location.reload()}>새로고침</button></section></main> : this.props.children;
  }
}

createRoot(document.getElementById('root')).render(<React.StrictMode><ErrorBoundary><IntakeApp/></ErrorBoundary></React.StrictMode>);
