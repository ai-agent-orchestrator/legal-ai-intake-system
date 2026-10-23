// Separate local UI verification server. Production always uses the real API.
import './intake-fixture.mjs';
import { createServer } from 'vite';
const server = await createServer({ server: { host: '127.0.0.1', port: 5176, strictPort: true,
  proxy: { '/api': { target: 'http://127.0.0.1:8086', changeOrigin: true } } } });
await server.listen();
console.log('Intake QA UI: http://127.0.0.1:5176/intake.html#/lawyer/intakes (synthetic data only)');
