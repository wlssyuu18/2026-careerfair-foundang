import { useEffect, useId, useRef, useState, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { isPublicCandidate, money, timeLabel, timeValue, wonFromManwon } from './logic';
import type { Availability, BookingCheck, Candidate } from './types';

export function Icon({ name, size = 24 }: { name: string; size?: number }) {
  const paths: Record<string, ReactNode> = {
    home: <><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1Z"/><path d="M9 21v-8h6v8"/></>,
    calendar: <><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 11h18m-13 5h2m4 0h2"/></>,
    user: <><circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/></>,
    arrow: <path d="m9 5 7 7-7 7M16 12H3"/>,
    back: <path d="m14 5-7 7 7 7M7 12h14"/>,
    upload: <><path d="M12 16V3m-5 5 5-5 5 5M4 16v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4"/></>,
    spark: <><path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5Z"/><path d="m20 2 .5 1.5L22 4l-1.5.5L20 6l-.5-1.5L18 4l1.5-.5Z"/></>,
    check: <path d="m5 12 4 4L19 6"/>,
    pin: <><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/></>,
    shield: <><path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z"/><path d="m8 12 3 3 5-6"/></>,
    room: <><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M9 21V7h7v14m-3-7h1"/></>,
    monitor: <><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4"/></>,
    bag: <><rect x="4" y="8" width="16" height="13" rx="3"/><path d="M8 8V6a4 4 0 0 1 8 0v2m-9 5v2m10-2v2"/></>,
    camera: <><path d="m8 5 1-2h6l1 2h4a1 1 0 0 1 1 1v14H3V6a1 1 0 0 1 1-1Z"/><circle cx="12" cy="13" r="4"/></>,
    tool: <path d="m14 6 4 4 3-3a6 6 0 0 1-7 8l-6 6-5-5 6-6a6 6 0 0 1 8-7Z"/>,
    ball: <><circle cx="12" cy="12" r="9"/><path d="m12 7 5 4-2 6H9l-2-6Zm0-4v4M3 10l4 1m10 0 4-1m-6 7 3 3m-9-3-3 3"/></>,
    close: <path d="m6 6 12 12M6 18 18 6"/>,
    logout: <><path d="M9 3H5v18h4M13 7l5 5-5 5M9 12h12"/></>,
    clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] ?? paths.spark}</svg>;
}
export function Button({ children, secondary, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { secondary?: boolean }) {
  return <button {...props} className={`button ${secondary ? 'secondary' : ''} ${props.className ?? ''}`}>{children}</button>;
}
export function Asset({ id, size = 24 }: { id: string; size?: number }) {
  return <span className="asset-icon" style={{ width: size, height: size }} aria-hidden="true"><img src={'/figma/' + id + '.svg'} alt=""/></span>;
}
export function Top({ title, back, label, brand, right, leading = false }: { title: string; back?: () => void; label?: string; brand?: boolean; right?: ReactNode; leading?: boolean }) {
  return <header className={'page-top' + (brand || leading ? ' leading' : '')}>
    {brand ? <span className="brand-small"><span className="brand-mark"><Asset id="3bac0" size={20}/></span>찾았당</span> : back ? <button type="button" className="icon-button" aria-label="이전 화면" onClick={back}><Asset id="cea89"/></button> : leading ? <strong>{title}</strong> : <span className="header-space"/>}
    {!brand && !leading && <strong>{label ?? title}</strong>}
    {right ?? <span className="header-space"/>}
  </header>;
}
export function InfoRow({ icon, label, value, badge, onClick, danger = false }: { icon?: string; label: string; value: ReactNode; badge?: string; onClick: () => void; danger?: boolean }) {
  return <button type="button" className={'info-row' + (danger ? ' danger' : '')} onClick={onClick}>
    {icon && <span className="info-icon"><Icon name={icon} size={20}/></span>}
    <span className="info-copy"><span>{label}</span><strong>{value}</strong></span>
    {badge && <Tag tone={danger ? 'coral' : 'green'}>{badge}</Tag>}<Asset id="da022" size={16}/>
  </button>;
}
export function Tag({ children, tone = 'green' }: { children: ReactNode; tone?: string }) { return <span className={`tag ${tone}`}>{children}</span>; }
export function Notice({ children, error = false }: { children: ReactNode; error?: boolean }) { return <div className={`notice ${error ? 'error' : ''}`} role={error ? 'alert' : 'status'}><Icon name="shield" size={19}/><div>{children}</div></div>; }
export function Modal({ children, titleId, onClose, className = '' }: { children: ReactNode; titleId: string; onClose: () => void; className?: string }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { const dialog = ref.current!; dialog.showModal(); return () => dialog.close(); }, []);
  return <dialog ref={ref} className={'modal ' + className} aria-labelledby={titleId} onCancel={e => { e.preventDefault(); onClose(); }}><div className="modal-content">{children}</div></dialog>;
}
export function BudgetInput({ label, value, onChange }: { label: string; value: number | null; onChange: (won: number | null) => void }) {
  const [text, setText] = useState(value === null ? '' : String(value / 10000));
  const lastValue = useRef(value);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (value !== lastValue.current) { setText(value === null ? '' : String(value / 10000)); lastValue.current = value; input.current?.setCustomValidity(''); }
  }, [value]);
  return <label className="budget-field">{label}<span className="budget-input">
    <input ref={input} type="text" inputMode="decimal" required pattern="[0-9]+([.][0-9]{0,4})?" maxLength={9} value={text} placeholder="예: 1" aria-label={`${label} (만원)`} onChange={e => {
      const next = e.target.value;
      if (next && !/^\d*(?:\.\d{0,4})?$/.test(next)) return;
      const won = wonFromManwon(next);
      setText(next); lastValue.current = won ?? value;
      e.target.setCustomValidity(next && won === null ? '예산은 0~1,000만원 사이의 숫자로 입력해 주세요.' : '');
      onChange(won);
    }}/><span aria-hidden="true">만원</span>
  </span></label>;
}
export function TimePicker({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [hour, setHour] = useState(''); const [minute, setMinute] = useState('00');
  const hours = useRef<HTMLDivElement>(null); const minutes = useRef<HTMLDivElement>(null);
  const selected = timeValue(hour, minute);
  useEffect(() => {
    if (!open) return;
    for (const ref of [hours, minutes]) ref.current?.querySelector('[aria-pressed="true"]')?.scrollIntoView({ block: 'center' });
  }, [open]);
  return <div className="time-field"><span id={`${id}-label`}>{label}</span>
    <button type="button" className={`time-trigger ${value ? 'has-value' : ''}`} aria-labelledby={`${id}-label ${id}-value`} aria-haspopup="dialog" aria-expanded={open} onClick={() => { const parts = value.split(':'); setHour(parts[0] || ''); setMinute(parts[1] || '00'); setOpen(true); }}>
      <span id={`${id}-value`}>{timeLabel(value)}</span><Icon name="clock" size={20}/>
    </button>
    {open && <Modal titleId={`${id}-title`} onClose={() => setOpen(false)}>
      <div className="row spread"><h2 id={`${id}-title`}>{label} 선택</h2><button type="button" className="icon-button" aria-label="시간 선택 닫기" onClick={() => setOpen(false)}><Icon name="close"/></button></div>
      <p className="time-preview" aria-live="polite">{selected ? timeLabel(selected) : '시와 분을 선택해 주세요'}</p>
      <div className="time-columns">
        <fieldset><legend>시 · 24시간</legend><div className="time-options" ref={hours}>{Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0')).map(h => <button type="button" key={h} aria-pressed={hour === h} onClick={() => setHour(h)}>{h}시</button>)}</div></fieldset>
        <fieldset><legend>분</legend><div className="time-options" ref={minutes}>{Array.from({ length: 60 }, (_, i) => String(i).padStart(2, '0')).map(m => <button type="button" key={m} aria-pressed={minute === m} onClick={() => setMinute(m)}>{m}분</button>)}</div></fieldset>
      </div>
      <Button type="button" disabled={!selected} onClick={() => { onChange(selected); setOpen(false); }}>이 시간으로 선택</Button>
    </Modal>}
  </div>;
}
export function CandidateCard({ candidate: c, availability, booking, disabled, onSelect, recommended = false }: { candidate: Candidate; availability?: Availability; booking?: BookingCheck; disabled?: boolean; onSelect: () => void; recommended?: boolean }) {
  const space = c.equipment.filter(item => /공간|방음|독립/.test(item));
  const devices = c.equipment.filter(item => !space.includes(item));
  return <article className={'resource-card' + (recommended ? ' recommended' : '')}>
    <div className="candidate-top"><Tag>{isPublicCandidate(c) ? '공공' : '민간'}</Tag>{recommended && <Tag tone="yellow">AI 추천 1순위</Tag>}<strong className="price">{booking?.totalPrice != null ? money(booking.totalPrice) : money(c.price)}</strong></div>
    <h3>{c.name}</h3><p className="candidate-meta"><span><Asset id="23a96" size={14}/>{c.address || '주소 확인 필요'}</span></p>
    {booking?.cancellation && <p className="candidate-meta">{booking.cancellation}</p>}
    <ul className="checks"><li><Asset id="1619c" size={14}/>{c.eligibility || '이용 자격 확인 필요'}</li>{devices.length > 0 && <li><Asset id="1619c" size={14}/>{devices.join(' · ')}</li>}{space.length > 0 && <li><Asset id="1619c" size={14}/>{space.join(' · ')}</li>}</ul>
    <Button disabled={disabled || booking?.status !== 'READY' || c.price === null} onClick={onSelect}>이 공간 예약하기</Button>
  </article>;
}
