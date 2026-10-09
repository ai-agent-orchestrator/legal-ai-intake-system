export default function ChecklistPanel({ checklist }) {
  if (!checklist) {
    return null;
  }

  return (
    <section className="checklist-panel">
      <h2>Practical Checklist</h2>
      <div className="detail-title">
        <strong>{checklist.stageLabel}</strong>
        <span>
          {checklist.domain} / {checklist.role} / {checklist.stageCode}
        </span>
      </div>
      <ul className="checklist">
        {checklist.checklist.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
      <div className="pill-row">
        <span className="pill">{checklist.recommendedAction}</span>
      </div>
    </section>
  );
}
