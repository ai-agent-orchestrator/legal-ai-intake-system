import { useState } from 'react';
import { Feedback, useResource } from './components.jsx';
import { selectProcedureCode } from './model.js';
import Icon from './Icon.jsx';

function Loading({ children }) { return <div className="write-reference-state" role="status"><span className="intake-spinner"/>{children}</div>; }
function Empty({ children }) { return <p className="write-reference-state">{children}</p>; }

function Checklist({ api, code, activeStep, onStep }) {
  const result = useResource(signal => api.procedureSteps(code, signal), [api, code]);
  const [checked, setChecked] = useState([]);
  if (result.status === 'loading') return <Loading>체크리스트를 불러오는 중입니다.</Loading>;
  if (result.error) return <Feedback error={result.error} retry={result.retry}/>;
  if (!result.data.length) return <Empty>등록된 단계별 체크리스트가 없습니다.</Empty>;
  const steps = [...result.data].sort((a, b) => a.stepOrder - b.stepOrder);
  const step = steps.find(item => item.stepCode === activeStep) || steps[0];
  return <div className="write-checklist"><label className="visually-hidden" htmlFor="checklist-step">확인할 절차 단계</label><select id="checklist-step" value={step.stepCode} onChange={event => onStep(event.target.value)}>{steps.map(item => <option key={item.stepCode} value={item.stepCode}>{item.stepOrder}. {item.title}</option>)}</select>{step.checklist.length ? step.checklist.map((item, index) => {
    const key = `${step.stepCode}:${index}`;
    return <label key={key}><input type="checkbox" checked={checked.includes(key)} onChange={event => setChecked(current => event.target.checked ? [...current, key] : current.filter(value => value !== key))}/><span>{item}</span></label>;
  }) : <Empty>이 단계에 등록된 준비 항목이 없습니다.</Empty>}<small>체크한 준비 여부는 현재 화면에서만 유지됩니다.</small></div>;
}

function FormDetail({ api, formCode, procedureCode }) {
  const result = useResource(signal => api.form(formCode, procedureCode, signal), [api, formCode, procedureCode]);
  if (result.status === 'loading') return <Loading>선택한 양식 안내를 불러오는 중입니다.</Loading>;
  if (result.error) return <Feedback error={result.error} retry={result.retry}/>;
  return <div className="selected-form"><div><small>선택한 양식 안내</small><h3>{result.data.title}</h3><p>{result.data.usageNote}</p><span className="form-file-type">{result.data.fileType.toUpperCase()}</span></div><Icon name="document" className="form-sheet"/></div>;
}

function Forms({ api, code }) {
  const result = useResource(signal => api.forms(code, signal), [api, code]);
  const [selected, setSelected] = useState(null);
  if (result.status === 'loading') return <Loading>관련 양식을 불러오는 중입니다.</Loading>;
  if (result.error) return <Feedback error={result.error} retry={result.retry}/>;
  if (!result.data.length) return <Empty>이 절차에 등록된 관련 양식이 없습니다.</Empty>;
  const selectedCode = result.data.some(item => item.formCode === selected) ? selected : result.data[0].formCode;
  return <><div className="write-form-list" aria-label="관련 양식 목록">{result.data.map(form => <button type="button" key={form.formCode} className={form.formCode === selectedCode ? 'selected' : ''} aria-pressed={form.formCode === selectedCode} onClick={() => setSelected(form.formCode)}><Icon name="document"/><span><strong>{form.title}</strong><small>{form.usageNote}</small></span><Icon name="chevron"/></button>)}</div><FormDetail key={selectedCode} api={api} formCode={selectedCode} procedureCode={code}/></>;
}

function ReferenceBody({ api, code, domain }) {
  const detail = useResource(signal => api.procedure(code, signal, domain), [api, code, domain]);
  const [activeStep, setActiveStep] = useState(null);
  const [checklistOpen, setChecklistOpen] = useState(false);
  const [checklistLoaded, setChecklistLoaded] = useState(false);
  const [formsOpen, setFormsOpen] = useState(true);
  return <>
    <section className="intake-panel write-reference-panel"><h2><Icon name="badge"/>관련 절차 안내</h2><p className="write-reference-caption">선택하신 사건 유형의 일반적인 절차를 확인해 보세요.</p>{detail.status === 'loading' ? <Loading>관련 절차를 불러오는 중입니다.</Loading> : detail.error ? <Feedback error={detail.error} retry={detail.retry}/> : <><h3 className="reference-procedure-title">{detail.data.title}</h3>{detail.data.steps.length ? <ol className="write-timeline">{[...detail.data.steps].sort((a, b) => a.stepOrder - b.stepOrder).map((step, index, steps) => <li key={step.stepOrder}><button type="button" className={(activeStep || steps[0].stepCode) === step.stepCode ? 'selected' : ''} aria-pressed={(activeStep || steps[0].stepCode) === step.stepCode} onClick={() => setActiveStep(step.stepCode)}><span className="timeline-number">{step.stepOrder}</span><span><strong>{step.title}</strong><small>{step.description}</small></span></button></li>)}</ol> : <Empty>등록된 절차 단계가 없습니다.</Empty>}</>}</section>
    <section className="intake-panel write-reference-panel"><button type="button" className="write-reference-toggle" aria-expanded={checklistOpen} aria-controls="write-checklist-body" onClick={() => { setChecklistOpen(value => !value); setChecklistLoaded(true); }}><Icon name="clipboard"/><h2>단계별 체크리스트</h2><Icon name="chevron" className={checklistOpen ? 'rotate' : ''}/></button><p className="write-reference-caption">현재 단계에서 준비하면 좋은 항목들을 확인해 보세요.</p><div id="write-checklist-body" hidden={!checklistOpen}>{checklistLoaded && <Checklist api={api} code={code} activeStep={activeStep} onStep={setActiveStep}/>}</div></section>
    <section className="intake-panel write-reference-panel"><button type="button" className="write-reference-toggle" aria-expanded={formsOpen} aria-controls="write-forms-body" onClick={() => setFormsOpen(value => !value)}><Icon name="document"/><h2>관련 양식 안내</h2><Icon name="chevron" className={formsOpen ? 'rotate' : ''}/></button><p className="write-reference-caption">이 사건 유형과 관련된 주요 양식을 확인해 보세요.</p><div id="write-forms-body" hidden={!formsOpen}><Forms api={api} code={code}/></div></section>
  </>;
}

export default function IntakeReference({ api, domain }) {
  const list = useResource(signal => api.procedures(domain, signal), [api, domain]);
  const [selected, setSelected] = useState(null);
  const code = selectProcedureCode(list.data, selected);
  return <aside className="write-aside" aria-label="문의 작성 참고 안내">
    {list.status === 'loading' ? <section className="intake-panel write-reference-panel"><Loading>사건 유형의 참고 정보를 불러오는 중입니다.</Loading></section> : list.error ? <section className="intake-panel write-reference-panel"><h2>참고 안내</h2><Feedback error={list.error} retry={list.retry}/></section> : !list.data.length ? <section className="intake-panel write-reference-panel"><h2><Icon name="info"/>참고 안내</h2><Empty>이 사건 유형에 등록된 참고 절차와 양식이 없습니다. 문의 내용은 계속 작성할 수 있습니다.</Empty></section> : <>
      {list.data.length > 1 && <div className="write-procedure-choice"><label htmlFor="write-procedure-code">참고할 절차</label><select id="write-procedure-code" value={code} onChange={event => setSelected(event.target.value)}>{list.data.map(item => <option key={item.code} value={item.code}>{item.title}</option>)}</select></div>}
      {domain === 'UNKNOWN' && <p className="reference-note">사건 유형이 미분류되어 전체 분야의 참고 절차를 표시합니다. 선택한 절차가 사건 유형을 확정하지는 않습니다.</p>}
      <ReferenceBody key={code} api={api} code={code} domain={list.data.find(item => item.code === code)?.domain}/>
    </>}
  </aside>;
}
