import BrandMark from './BrandMark.jsx';
import Icon from './Icon.jsx';
import LoginForm from './LoginForm.jsx';
import { AUTH_ROLES } from './authModel.js';
import { Feedback, useResource } from './components.jsx';

const benefits = [
  ['document', '체계적인 사건 접수', '상담에 필요한 내용을 차근차근 정리'],
  ['shieldCheck', '보안 중심의 설계', '민감한 정보의 보호 처리 결과 확인'],
  ['people', '업무에 맞춘 화면', '의뢰인·변호사 업무·운영 현황 확인'],
  ['chart', '운영 현황과 비용 관리', '서버 지표를 바탕으로 운영 현황 파악'],
];
export default function LoginPage({ api, user, onLogin }) {
  return <main className="login-layout"><section className="login-introduction" aria-label="서비스 소개"><h2>Security-Aware<br/>Legal AI Intake System</h2><span/><p>안전하고 체계적인 법률 상담의 시작,<br/>필요한 정보를 더 명확하게</p></section>
    <section className="login-card" aria-labelledby="screen01-title"><header><BrandMark/><h1 id="screen01-title">Security-Aware<br/>Legal AI Intake <em>System</em></h1><p>안전한 데이터로 더 나은 법률 상담을 지원합니다.</p></header>
      {user && <p className="login-session">{user.username}님으로 로그인되어 있습니다. <a href="#/account">내 정보 확인</a></p>}
      <LoginForm api={api} onLogin={onLogin}><div className="login-demo-divider"><span>또는 데모로 체험하기</span></div><nav className="login-demo-links" aria-label="공개 화면 체험"><a href="#/intake/type"><Icon name="people"/><span>의뢰인 데모<Icon name="chevron"/></span></a><a href="#/lawyer/intakes"><Icon name="user"/><span>변호사 데모<Icon name="chevron"/></span></a><a href="operator.html#/overview" target="_blank" rel="noopener noreferrer"><Icon name="settings"/><span>관리자 데모<Icon name="chevron"/></span></a></nav><footer className="login-security"><p><Icon name="lock"/>JWT 인증 및 계정 정보 확인</p><small>데모는 공개 화면 체험이며 계정이나 역할 권한을 부여하지 않습니다.</small></footer></LoginForm>
    </section><aside className="login-benefits" aria-label="주요 기능">{benefits.map(([icon, title, description], index) => <div key={icon} className={`login-benefit benefit-${index}`}><span><Icon name={icon}/></span><div><h2>{title}</h2><p>{description}</p></div></div>)}</aside></main>;
}

function VerifiedAccount({ api, user }) {
  const resource = useResource(signal => api.currentUser(undefined, signal), [api, user.username, user.role]);
  return <section className="intake-panel"><h1>내 계정 정보</h1>{resource.status === 'loading' ? <p className="intake-state" role="status">로그인한 계정을 확인하고 있습니다.</p> : resource.error ? <Feedback error={resource.error} retry={resource.retry}/> : resource.data.username !== user.username || resource.data.role !== user.role ? <Feedback error={new Error('현재 로그인한 계정과 조회 결과가 다릅니다. 다시 로그인해 주세요.')}/> : <><dl className="result-fields"><div><dt>아이디</dt><dd>{resource.data.username}</dd></div><div><dt>계정 역할</dt><dd>{AUTH_ROLES[resource.data.role]} ({resource.data.role})</dd></div></dl><div className="button-row"><a className="intake-button" href="#/intake/type">사건 접수하기<Icon name="arrow"/></a><a className="intake-button secondary" href="#/lawyer/intakes">문의 목록 보기</a></div>{resource.data.role === 'ROLE_ADMIN' && <div className="account-operator"><a className="intake-button" href="operator.html#/overview" target="_blank" rel="noopener noreferrer">운영 대시보드 열기<Icon name="external"/></a><p className="field-hint">별도 탭에서 열립니다. 지표 조회 인증은 운영 화면에서 진행합니다.</p></div>}<p className="field-hint">새로고침하면 다시 로그인해야 합니다.</p></>}</section>;
}
export function AccountPage({ api, user }) {
  return <main className="intake-shell compact account-shell">{user ? <VerifiedAccount key={`${user.username}:${user.role}`} api={api} user={user}/> : <section className="intake-panel"><h1>로그인이 필요합니다.</h1><p>로그인한 계정의 정보를 확인할 수 있습니다.</p><a className="intake-button" href="#/login">로그인 화면으로</a></section>}</main>;
}
