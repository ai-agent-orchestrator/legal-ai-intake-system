export { default as IntakeWritePage } from './IntakeWritePage.jsx';

export function IntakeHelpPage() {
  return <main className="intake-shell compact"><section className="intake-panel">
    <h1>사건 접수 이용 안내</h1>
    <ol className="help-steps">
      <li>문의 목적과 가까운 사건 유형을 선택해 주세요.</li>
      <li>현재 상황을 100자 이내로 적고 사건 유형을 확인해 주세요.</li>
      <li>결과와 참고 절차를 살펴본 뒤 ‘다음: 문의 작성’으로 이동하세요.</li>
      <li>핵심 내용을 100자까지 보완하고 추가 정보를 입력한 뒤 ‘문의 접수하기’를 누르면 서버에 저장됩니다.</li>
    </ol>
    <p className="muted">등록 전 입력은 현재 탭의 메모리에만 유지되며 새로고침하면 초기화됩니다. 분류 미리보기는 문의를 저장하지 않습니다.</p>
    <a href="#/intake/type" className="intake-button">사건 접수로 이동</a>
  </section></main>;
}

export function IntakeNotFoundPage() {
  return <main className="intake-shell compact"><section className="intake-panel">
    <h1>페이지를 찾을 수 없습니다.</h1><a href="#/intake/type" className="intake-button">사건 접수로 이동</a>
  </section></main>;
}
