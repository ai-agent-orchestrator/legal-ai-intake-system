export default function ProcedureList({ status, procedures, selectedCode, onSelect }) {
  if (status === "loading") {
    return <div className="empty">Loading legal procedure list...</div>;
  }

  if (status === "error") {
    return <div className="empty">Could not load procedure list.</div>;
  }

  if (procedures.length === 0) {
    return <div className="empty">No matching legal procedures.</div>;
  }

  return (
    <div className="procedure-list">
      {procedures.map((procedure) => (
        <button
          key={procedure.code}
          type="button"
          className={`procedure-button ${selectedCode === procedure.code ? "active" : ""}`}
          onClick={() => onSelect(procedure.code)}
        >
          <strong>{procedure.title}</strong>
          <span>{procedure.summary}</span>
          <div className="pill-row">
            <span className="pill">{procedure.domain}</span>
            <span className="pill">{procedure.code}</span>
            <span className="pill">{procedure.userRole}</span>
          </div>
        </button>
      ))}
    </div>
  );
}
