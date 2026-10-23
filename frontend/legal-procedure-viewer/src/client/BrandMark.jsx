import { useId } from 'react';

export default function BrandMark() {
  const id = useId();
  return <svg className="brand-mark" viewBox="0 0 52 60" aria-hidden="true">
    <defs><linearGradient id={id} x1="0" y1="1" x2=".85" y2="0"><stop stopColor="#00378e"/><stop offset="1" stopColor="#0057ef"/></linearGradient></defs>
    <path d="M26 1C19 7 10 10 2 11v19c0 13 9 23 24 29 15-6 24-16 24-29V11C42 10 33 7 26 1Z" fill={`url(#${id})`}/>
    <path d="M26 5C19 10 12 12 6 14v16c0 11 7 19 20 25 13-6 20-14 20-25V14C40 12 33 10 26 5Z" fill="none" stroke="#fff" strokeOpacity=".13"/>
    <path d="M17 17h12l7 7v20H17Z" fill="none" stroke="#fff" strokeWidth="1.9" strokeLinejoin="round"/>
    <path d="M29 17v8h7M21 29h11m-11 5h11m-11 5h8" fill="none" stroke="#fff" strokeWidth="1.7" strokeLinejoin="round"/>
    <path d="M13 21v27h19" fill="none" stroke="#fff" strokeOpacity=".85" strokeWidth="1.5"/>
  </svg>;
}
