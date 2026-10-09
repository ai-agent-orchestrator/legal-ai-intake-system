export default function StatusBanner({
  status,
  idleTitle,
  loadingTitle,
  successTitle,
  errorTitle,
  message,
  error
}) {
  const titleByStatus = {
    idle: idleTitle,
    loading: loadingTitle,
    success: successTitle,
    error: errorTitle
  };

  return (
    <div className={`banner ${status}`}>
      <strong>{titleByStatus[status] || idleTitle}</strong>
      <p>{status === "error" ? error : message}</p>
    </div>
  );
}
