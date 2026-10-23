const paths = {
  home: <><path d="M2 10 12 2l10 8v2H2Zm2 3h7v9H4Zm9 0h7v9h-7Z" fill="currentColor" stroke="none" /><path d="M13 13h7v9h-7Z" fill="currentColor" opacity=".72" stroke="none" /></>,
  document: <><path d="M5 2h10l5 5v15H5Z" fill="currentColor" stroke="none" /><path d="M15 2v6h5" fill="white" fillOpacity=".62" stroke="none" /><path d="M8 7h4m-4 5h9m-9 4h9m-9 3h6" stroke="white" strokeWidth="1.55" /><path d="M3 6v16h13" stroke="currentColor" strokeWidth="1.5" /></>,
  alert: <><path d="M10.3 3a2 2 0 0 1 3.4 0L23 20a1.3 1.3 0 0 1-1.2 2H2.2A1.3 1.3 0 0 1 1 20Z" fill="currentColor" stroke="none" /><path d="M12 8v6" stroke="white" strokeWidth="2.1" /><circle cx="12" cy="18" r="1.15" fill="white" stroke="none" /></>,
  shield: <><path d="M12 1.5 22 5v7c0 5-4 9-10 11C6 21 2 17 2 12V5Z" fill="currentColor" stroke="none" /><path d="M12 5v7h6.5c-.2 3-2.3 5.5-6.5 7V12H5.5V7.5Z" fill="white" stroke="none" /></>,
  flame: <><path d="M13 1c2 5-3 7-2 11 2-1 3-3 3-6 4 4 8 8 6 13-3 6-14 6-17-1-2-4 1-8 4-11-1 4 0 6 2 7C8 8 14 7 13 1Z" fill="currentColor" stroke="none" /><path d="M12 13c2 3 4 4 3 7-2 3-6 1-5-2Z" fill="white" fillOpacity=".8" stroke="none" /></>,
  chart: <><rect x="2" y="13" width="5" height="10" rx="1.7" fill="currentColor" opacity=".86" stroke="none" /><rect x="9.5" y="7" width="5" height="16" rx="1.7" fill="currentColor" stroke="none" /><rect x="17" y="1" width="5" height="22" rx="1.7" fill="currentColor" opacity=".76" stroke="none" /></>,
  clipboard: <><rect x="4" y="4" width="16" height="19" rx="1.8" fill="currentColor" stroke="none" /><rect x="8" y="1" width="8" height="6" rx="1.3" fill="currentColor" stroke="white" strokeWidth="1" /><path d="M8 11h8m-8 4h8m-8 4h6" stroke="white" strokeWidth="1.5" /></>,
  settings: <><path d="m9 3 1-2h4l1 2 3 1 2 3-1 3v4l1 3-2 3-3 1-1 2h-4l-1-2-3-1-2-3 1-3v-4L4 7l2-3Z" fill="currentColor" stroke="none" /><circle cx="12" cy="12" r="4.1" fill="white" stroke="none" /></>,
  audit: <><path d="M7 2 2 6v5l2 3-2 8h7l1-7-2-4V7l2-3Zm10 0 5 4v5l-2 3 2 8h-7l-1-7 2-4V7l-2-3Z" fill="currentColor" stroke="none" /><path d="M11 4h2v7h-2Zm0 10h2v2h-2Z" fill="currentColor" stroke="none" /></>,
  user: <><circle cx="12" cy="7" r="4" /><path d="M3 22v-3a9 9 0 0 1 18 0v3" /></>,
  clock: <><circle cx="12" cy="12" r="9" /><path d="M12 6v6l4 2" /></>,
  refresh: <><path d="M20 8a8 8 0 0 0-14-3L3 8m0-5v5h5M4 16a8 8 0 0 0 14 3l3-3m0 5v-5h-5" /></>,
  search: <><circle cx="10" cy="10" r="7" /><path d="m15 15 7 7" /></>,
  users: <><circle cx="9" cy="6.5" r="4.5" fill="currentColor" stroke="none" /><path d="M1 23v-4a8 8 0 0 1 16 0v4Z" fill="currentColor" stroke="none" /><circle cx="18" cy="7" r="3.5" fill="currentColor" opacity=".7" stroke="none" /><path d="M17.5 13c4 0 6.5 3 6.5 6v4h-5v-5a9 9 0 0 0-1.5-5Z" fill="currentColor" opacity=".7" stroke="none" /></>,
  calendar: <><rect x="3" y="5" width="18" height="17" rx="2" /><path d="M7 2v6m10-6v6M3 11h18" /></>,
  pie: <><path d="M11 3a9 9 0 1 0 10 10H11Z" /><path d="M14 2v8h8a9 9 0 0 0-8-8Z" fill="currentColor" stroke="none" /></>,
  pin: <><path d="M19 9c0 6-7 13-7 13S5 15 5 9a7 7 0 0 1 14 0Z" /><circle cx="12" cy="9" r="2.5" /></>,
  journey: <><path d="m12 2 3 2 4 1 1 4 2 3-2 3-1 4-4 1-3 2-3-2-4-1-1-4-2-3 2-3 1-4 4-1Z" fill="currentColor" stroke="none" /><path d="M8 12h8m-4-4v8" stroke="white" /></>,
  info: <><circle cx="12" cy="12" r="10" fill="currentColor" stroke="none" /><path d="M12 11v6m0-11v.1" stroke="white" strokeWidth="2" /></>,
  'arrow-left': <path d="M21 12H3m6-6-6 6 6 6" />,
  'arrow-right': <path d="M3 12h18m-6-6 6 6-6 6" />,
  close: <path d="m5 5 14 14M5 19 19 5" />,
  'check-circle': <><circle cx="12" cy="12" r="10" fill="currentColor" stroke="none" /><path d="m7 12 3 3 7-7" stroke="white" /></>,
  'chevron-right': <path d="m9 4 8 8-8 8" />,
  'chevron-down': <path d="m5 9 7 7 7-7" />,
};

export default function Icon({ name, className = '' }) {
  return <svg className={`ui-icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] || paths.document}</svg>;
}
