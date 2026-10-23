import BrandMark from './BrandMark.jsx';
import Icon from './Icon.jsx';

export default function IntakeHeader({ route, user, onLogin, onLogout }) {
  const intakeActive = ['classify', 'write', 'receipt', 'review', 'search'].includes(route.name);
  const lawyer = ['intake-list', 'lawyer-detail', 'consultation'].includes(route.name);
  return <header className="intake-header">
    <a className="intake-brand" href="#/intake/type" aria-label="Legal Intake 사건 접수">
      <BrandMark/><span>Security-Aware<br/>Legal AI Intake System</span>
    </a>
    <nav aria-label="주 메뉴">
      {lawyer && user?.role !== 'ROLE_USER' ? <><a href="#/intake/type"><Icon name="user"/>의뢰인</a><a href="#/lawyer/intakes" className="active" aria-current="page"><Icon name="people"/>변호사</a>{user?.role === 'ROLE_ADMIN' && <a href="operator.html#/overview"><Icon name="people"/>운영자</a>}</> : <><a href="#/intake/type" className={intakeActive ? 'active' : ''} aria-current={intakeActive ? 'page' : undefined}><Icon name="document"/>사건 접수</a>
      <a href="#/lawyer/intakes"><Icon name="folder"/>{user?.role === 'ROLE_USER' ? '나의 상담' : '문의 목록'}</a>
      <a href="#/help" className={route.name === 'help' ? 'active' : ''} aria-current={route.name === 'help' ? 'page' : undefined}><Icon name="help"/>도움말</a></>}
    </nav>
    {lawyer && <a className="lawyer-header-help" href="#/help"><Icon name="help"/>도움말</a>}
    <div className="intake-account"><Icon name="user"/>{user
      ? <><a href="#/account" title="내 계정 정보 확인">{user.username}님</a><button type="button" onClick={onLogout}>로그아웃</button></>
      : <><span>비회원</span><button type="button" onClick={onLogin}>로그인</button></>}
    </div>
  </header>;
}
