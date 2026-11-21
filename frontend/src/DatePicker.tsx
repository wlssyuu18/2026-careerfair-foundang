import { useId, useRef, useState } from 'react';
import { Button, Icon, Modal } from './components';
import { calendarDays, dateLabel } from './logic';

export default function DatePicker({ label, value, onChange, min, max }: { label: string; value: string; onChange: (date: string) => void; min?: string; max?: string }) {
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date());
  const id = useId(); const grid = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false); const [month, setMonth] = useState(today.slice(0, 7)); const [selected, setSelected] = useState(value);
  const [mode, setMode] = useState<'days' | 'years' | 'months'>('days'); const [yearPage, setYearPage] = useState(1900);
  const [year, number] = month.split('-').map(Number);
  const firstYear = Number((min || '1900').slice(0, 4));
  const lastYear = Number((max || String(Math.max(Number(today.slice(0, 4)) + 10, year))).slice(0, 4));
  const allowed = (date: string) => (!min || date >= min) && (!max || date <= max);
  const previousDisabled = mode === 'years' ? yearPage <= firstYear : mode === 'months' ? year <= firstYear : !!min && month <= min.slice(0, 7);
  const nextDisabled = mode === 'years' ? yearPage + 12 > lastYear : mode === 'months' ? year >= lastYear : !!max && month >= max.slice(0, 7);
  function moveMonth(offset: number) {
    const next = new Date(Date.UTC(year, number - 1 + offset, 1, 12)).toISOString().slice(0, 7);
    if ((!min || next >= min.slice(0, 7)) && (!max || next <= max.slice(0, 7))) setMonth(next);
  }
  function navigate(offset: number) {
    if (mode === 'years') setYearPage(Math.max(firstYear, yearPage + offset * 12));
    else if (mode === 'months') setMonth((year + offset) + '-' + String(number).padStart(2, '0'));
    else moveMonth(offset);
  }
  return <div className="time-field date-field"><span id={`${id}-label`}>{label}</span>
    <button type="button" className={`time-trigger ${value ? 'has-value' : ''}`} aria-labelledby={`${id}-label ${id}-value`} aria-haspopup="dialog" aria-expanded={open} onClick={() => { const date = value || (max && today > max ? max : min && today < min ? min : today); setSelected(value); setMonth(date.slice(0, 7)); setMode('days'); setOpen(true); }}><span id={`${id}-value`}>{value ? value.slice(0, 4) + '년 ' + dateLabel(value, true) : '날짜를 선택해 주세요'}</span><Icon name="calendar" size={20}/></button>
    {open && <Modal titleId={`${id}-title`} className="date-modal" onClose={() => setOpen(false)}>
      <div className="row spread"><h2 id={`${id}-title`}>{label} 선택</h2><button type="button" className="icon-button" aria-label="날짜 선택 닫기" onClick={() => setOpen(false)}><Icon name="close"/></button></div>
      <div className="calendar-heading">
        <button type="button" className="icon-button" aria-label={mode === 'years' ? '이전 연도 목록' : mode === 'months' ? '이전 연도' : '이전 달'} disabled={previousDisabled} onClick={() => navigate(-1)}><Icon name="back" size={18}/></button>
        <div className="calendar-period-triggers"><button type="button" className="calendar-period-button" aria-label="연도 선택" aria-expanded={mode === 'years'} onClick={() => { setYearPage(Math.max(firstYear, year - 5)); setMode(mode === 'years' ? 'days' : 'years'); }}>{year}년</button><button type="button" className="calendar-period-button" aria-label="월 선택" aria-expanded={mode === 'months'} onClick={() => setMode(mode === 'months' ? 'days' : 'months')}>{number}월</button></div>
        <button type="button" className="icon-button" aria-label={mode === 'years' ? '다음 연도 목록' : mode === 'months' ? '다음 연도' : '다음 달'} disabled={nextDisabled} onClick={() => navigate(1)}><Icon name="arrow" size={18}/></button>
      </div>
      <div className="calendar-body">
      {mode === 'years' ? <div className="calendar-period-grid" role="group" aria-label="연도 목록">{Array.from({ length: 12 }, (_, i) => yearPage + i).map(y => <button type="button" key={y} disabled={y > lastYear} aria-pressed={y === year} onClick={() => { setMonth(y + '-' + String(number).padStart(2, '0')); setMode('months'); }}>{y}년</button>)}</div> : mode === 'months' ? <div className="calendar-period-grid" role="group" aria-label="월 목록">{Array.from({ length: 12 }, (_, i) => i + 1).map(m => { const next = year + '-' + String(m).padStart(2, '0'); return <button type="button" key={m} disabled={!!min && next < min.slice(0, 7) || !!max && next > max.slice(0, 7)} aria-pressed={m === number} onClick={() => { setMonth(next); setMode('days'); }}>{m}월</button>; })}</div> : <>
      <div className="calendar-week" aria-hidden="true">{['일', '월', '화', '수', '목', '금', '토'].map(day => <span key={day}>{day}</span>)}</div>
      <div className="calendar-grid" role="group" aria-label="날짜" ref={grid}>{calendarDays(month).map(date => <button type="button" key={date} data-date={date} className={date.startsWith(month) ? '' : 'other-month'} aria-label={date} aria-pressed={selected === date} aria-current={date === today ? 'date' : undefined} disabled={!allowed(date)} onClick={() => setSelected(date)} onKeyDown={e => {
        const offset = ({ ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 } as Record<string, number>)[e.key];
        if (!offset) return;
        e.preventDefault(); const next = new Date(date + 'T12:00:00Z'); next.setUTCDate(next.getUTCDate() + offset); const target = next.toISOString().slice(0, 10);
        if (!allowed(target)) return;
        setMonth(target.slice(0, 7)); setSelected(target); requestAnimationFrame(() => grid.current?.querySelector<HTMLButtonElement>(`[data-date="${target}"]`)?.focus());
      }}>{Number(date.slice(-2))}</button>)}</div>
      </>}
      </div>
      <p className="calendar-selection" role="status">{selected ? selected.slice(0, 4) + '년 ' + dateLabel(selected, true) : '원하는 날짜를 선택해 주세요'}</p>
      <Button type="button" disabled={!selected || !allowed(selected)} onClick={() => { onChange(selected); setOpen(false); }}>이 날짜로 선택</Button>
    </Modal>}
  </div>;
}
