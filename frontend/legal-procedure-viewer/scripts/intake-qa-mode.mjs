const mode = process.argv[2];
if (!['success', 'list-slow', 'list-error', 'list-forbidden', 'list-empty', 'list-malformed', 'consultation-slow', 'consultation-error', 'consultation-missing', 'consultation-empty', 'status-slow', 'status-error', 'status-conflict', 'memo-slow', 'memo-error', 'memo-validation', 'memo-lost'].includes(mode)) throw new Error('Choose a documented Intake QA mode.');
const response = await fetch(`http://127.0.0.1:8086/__qa__/mode?value=${mode}`, { method: 'POST', signal: AbortSignal.timeout(5000) });
if (!response.ok) throw new Error(`QA mode update failed: ${response.status}`);
console.log(await response.json());
