import React from 'react';
import { createRoot } from 'react-dom/client';
import OperatorApp from './OperatorApp.jsx';
import './operator.css';
import './operator-draft.css';
import './marketing.css';
import './security.css';
import './audit.css';
import './observability-summary.css';

createRoot(document.getElementById('root')).render(<OperatorApp />);
