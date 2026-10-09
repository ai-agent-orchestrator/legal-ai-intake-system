export default function ProcedureDetail({ detail }) {
  return (
    <article>
      <div className="detail-title">
        <strong>{detail.title}</strong>
        <span>{detail.summary}</span>
        <div className="pill-row">
          <span className="pill">{detail.domain}</span>
          <span className="pill">{detail.code}</span>
          <span className="pill">{detail.userRole}</span>
        </div>
      </div>

      <h3>Steps</h3>
      <ul className="step-list">
        {detail.steps.map((step) => (
          <li key={step.stepCode} className="step-card">
            <strong>
              {step.stepOrder}. {step.title}
            </strong>
            <p>{step.description}</p>
            <ul className="checklist">
              {step.checklist.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </li>
        ))}
      </ul>

      <h3>Forms</h3>
      <ul className="form-list">
        {detail.forms.map((form) => (
          <li key={form.formCode} className="form-card">
            <strong>{form.title}</strong>
            <p>{form.usageNote}</p>
            <div className="pill-row">
              <span className="pill">{form.formCode}</span>
              <span className="pill">{form.fileType}</span>
            </div>
          </li>
        ))}
      </ul>

      <h3>Knowledge Graph Edges</h3>
      <ul className="edge-list">
        {detail.knowledgeGraphEdges.map((edge) => (
          <li key={edge} className="edge-card">{edge}</li>
        ))}
      </ul>
    </article>
  );
}
