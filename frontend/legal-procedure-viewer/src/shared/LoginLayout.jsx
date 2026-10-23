import BrandMark from '../client/BrandMark.jsx';
import Icon from '../client/Icon.jsx';

const benefits = [
  ['document', '체계적인 사건 접수', '상담에 필요한 내용을 차근차근 정리'],
  ['shieldCheck', '보안 중심의 설계', '민감한 정보의 보호 처리 결과 확인'],
  ['people', '업무에 맞춘 화면', '의뢰인·변호사 업무·운영 현황 확인'],
  ['chart', '운영 현황과 비용 관리', '서버 지표를 바탕으로 운영 현황 파악'],
];

export default function LoginLayout({ children, titleId = 'screen01-title', roleTitle, description = '안전한 데이터로 더 나은 법률 상담을 지원합니다.' }) {
  return <main className="login-layout"><section className="login-introduction" aria-label="서비스 소개"><h2>Security-Aware<br/>Legal AI Intake System</h2><span/><p>안전하고 체계적인 법률 상담의 시작,<br/>필요한 정보를 더 명확하게</p></section>
    <section className="login-card" aria-labelledby={titleId}><header><BrandMark/><h1 id={titleId}>Security-Aware<br/>Legal AI Intake <em>System</em></h1>{roleTitle && <h2 className="login-role-title">{roleTitle}</h2>}<p>{description}</p></header>{children}</section>
    <aside className="login-benefits" aria-label="주요 기능">{benefits.map(([icon, title, description], index) => <div key={icon} className={`login-benefit benefit-${index}`}><span><Icon name={icon}/></span><div><h2>{title}</h2><p>{description}</p></div></div>)}</aside></main>;
}
