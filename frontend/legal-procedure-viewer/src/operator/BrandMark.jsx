import { useId } from 'react';

export default function BrandMark() {
  const id = useId();
  return <svg className="brand-mark" viewBox="0 0 60 68" aria-hidden="true">
    <defs><linearGradient id={id} x1="0" x2="1" y1="1" y2="0"><stop stopColor="#0044e9" /><stop offset="1" stopColor="#1778ff" /></linearGradient></defs>
    <path d="M30 2C22 8 13 10 5 11v23c0 15 11 25 25 32 14-7 25-17 25-32V11C47 10 38 8 30 2Z" fill={`url(#${id})`} />
    <path d="M30 7C23 11 16 13 10 14v20c0 12 8 21 20 27 12-6 20-15 20-27V14C44 13 37 11 30 7Z" fill="none" stroke="#fff" strokeOpacity=".85" strokeWidth="2" />
    <path d="M20 14h15l9 9v23H20Z" fill="#fff" /><path d="M35 14v10h9" fill="#a9ccff" />
    <path d="M14 29h15l8 8v20H14Z" fill="#fff" stroke="#1456ff" strokeWidth="2" /><path d="M29 30v8h8" fill="#c9dcff" />
    <path d="M24 24h7m-7 5h14M19 41h13m-13 5h13m-13 5h8" stroke="#0950e9" strokeWidth="2" />
  </svg>;
}
