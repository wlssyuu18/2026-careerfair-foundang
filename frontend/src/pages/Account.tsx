import DatePicker from '../DatePicker';
import RegionSearch from '../RegionSearch';
import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../api';
import { age, bookingGroups, budgetLabel, compactRegion, dateLabel, daysUntil, extractedStart, money, stateLabel } from '../logic';
import { Asset, BudgetInput, Button, Icon, Modal, Notice, Tag, Top } from '../components';
import { equipmentOptions } from './Start';
import type { AgentTask, Profile, View } from '../types';

type Props = { view: View; profile: Profile; tasks: AgentTask[]; loggedIn: boolean; go: (v: View) => void; onSave: (p: Profile) => void; logout: () => Promise<void>; openTask: (t: AgentTask) => void; perform: (f: () => Promise<unknown>) => Promise<void> };
type Edit = 'accountInfo' | 'conditions' | 'equipment' | 'notifications' | 'privacy';
export default function Account(p: Props & { cancelBooking?: (t: AgentTask) => Promise<void> }) {
  const [draft, setDraft] = useState(p.profile); const [editing, setEditing] = useState<Edit | null>(null);
  const [detail, setDetail] = useState<AgentTask | null>(null);
  useEffect(() => { if (detail) setDetail(p.tasks.find(t => t.id === detail.id) || detail); }, [p.tasks]);
  const [logoutOpen, setLogoutOpen] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false); const [password, setPassword] = useState(''); const [withdrawing, setWithdrawing] = useState(false); const [error, setError] = useState('');
  const [reservationTab, setReservationTab] = useState<'upcoming' | 'past'>('upcoming');
  const groups = bookingGroups(p.tasks); const years = age(p.profile.birthDate);
  const upcoming = groups.scheduled.concat(groups.pending).sort((a, b) => (a.conditions.date + a.conditions.start).localeCompare(b.conditions.date + b.conditions.start));
  const past = groups.completed.concat(p.tasks.filter(t => t.state === 'STOPPED' && t.bookingReference)).sort((a, b) => b.conditions.date.localeCompare(a.conditions.date));
  async function save(e: FormEvent) {
    e.preventDefault(); if (!draft.name.trim() || age(draft.birthDate) === null) { setError('이름과 생년월일을 확인해 주세요.'); return; }
    await p.perform(async () => { const saved = await api.saveProfile(draft); p.onSave(saved); setDraft(saved); setEditing(null); setError(''); });
  }
  function edit(value: Edit) { setDraft(p.profile); setError(''); setEditing(value); }
  function row(label: string, value: string, icon: string, target: Edit) {
    return <button type="button" className="setting-button" onClick={() => edit(target)}><span className="setting-icon"><Icon name={icon} size={18}/></span><strong>{label}</strong><span>{value}</span><Asset id="da022" size={16}/></button>;
  }
  function booking(t: AgentTask) {
    const days = daysUntil(t.conditions.date);
    return <article className={'booking-card ' + (t.state === 'CONFIRMED' ? 'confirmed' : ['RECEIVED', 'PENDING'].includes(t.state) ? 'waiting' : '')} key={t.id}>
      <div className="booking-badges"><Tag tone={t.state === 'CONFIRMED' ? 'green' : 'yellow'}>{stateLabel[t.state] || t.state}</Tag>{t.state === 'CONFIRMED' && Number.isFinite(days) && days >= 0 && <Tag tone="gray">{days === 0 ? 'D-Day' : 'D-' + days}</Tag>}{t.bookingReference && <small>No. {t.bookingReference}</small>}</div>
      <h3><button type="button" className="booking-title" onClick={() => setDetail(t)}>{t.approval?.name || t.conditions.purpose}</button></h3><p className="candidate-meta"><Asset id="ee3ab" size={14}/>{dateLabel(t.conditions.date)} {t.conditions.start}–{t.conditions.end} · {money(t.approval?.totalPrice ?? null)}</p>
      {t.state === 'CONFIRMED' ? <div className="timeline"><div><i/><span>입실</span><strong>{t.conditions.start}</strong></div><div><i/><span>{t.conditions.purpose.includes('면접') ? '면접' : '이용'}</span><strong>{extractedStart(t.conditions)}</strong></div><div><i/><span>퇴실</span><strong>{t.conditions.end}</strong></div></div> : ['RECEIVED', 'PENDING'].includes(t.state) && <div className="notice yellow compact"><Icon name="clock" size={14}/>{'시설에서 승인하면 바로 알려드릴게요'}</div>}
      {t.state === 'CONFIRMED' && <div className="booking-actions"><a href={'https://www.google.com/maps/dir/?api=1&destination=' + encodeURIComponent(t.candidates.find(c => c.id === t.selectedId)?.address || t.approval?.name || '')} target="_blank" rel="noreferrer"><Icon name="pin" size={14}/>길찾기</a><button type="button" onClick={() => setDetail(t)}><Icon name="shield" size={14}/>이용 안내</button><button type="button" onClick={() => setDetail(t)}><Icon name="calendar" size={14}/>변경·취소</button></div>}

    </article>;
  }
  function recent(t: AgentTask) {
    return <button type="button" className="recent-booking" key={t.id} onClick={() => setDetail(t)}><Tag tone={t.state === 'STOPPED' ? 'coral' : 'gray'}>{t.state === 'STOPPED' ? '취소됨' : '이용 완료'}</Tag><strong>{t.approval?.name || t.conditions.purpose}</strong><small>{dateLabel(t.conditions.date)}</small></button>;
  }
  if (!p.loggedIn) return <><Top title={p.view === 'reservations' ? '예약' : '마이'} leading/><section className="page empty"><h1>로그인하고<br/>나의 준비를 확인해요</h1><Button onClick={() => p.go('login')}>이메일로 로그인</Button><button className="text-button" onClick={() => p.go('signup')}>계정 만들기</button></section></>;
  return <><Top title={p.view === 'reservations' ? '예약' : '마이'} leading right={p.view === 'reservations' ? <Icon name="bell" size={22}/> : undefined}/>
    <section className="page account-page">
      {p.view === 'account' && <section className="profile-hero"><div className="profile-identity"><div className="avatar">{p.profile.name.slice(p.profile.name.length > 1 ? 1 : 0, p.profile.name.length > 1 ? 2 : 1)}</div><div><h1>{p.profile.name}</h1><p>{compactRegion(p.profile.region) || '지역 미설정'} · {years === null ? '나이 확인 필요' : '만 ' + years + '세'}</p></div><Tag>시설별 자격 확인</Tag></div></section>}
      {p.view === 'reservations' ? <>
        <div className="reservation-tabs" aria-label="예약 내역 구분">{(['upcoming', 'past'] as const).map(tab => <button key={tab} type="button" aria-pressed={reservationTab === tab} aria-controls="reservation-list" onClick={() => setReservationTab(tab)}>{tab === 'upcoming' ? '다가오는 예약 ' + upcoming.length : '지난 예약'}</button>)}</div>
        <div id="reservation-list">{reservationTab === 'upcoming' ? <>
          {upcoming.map(booking)}
          {!upcoming.length && <div className="empty-card"><Icon name="calendar" size={30}/><h3>다가오는 예약이 없어요</h3><Button secondary onClick={() => p.go('home')}>공간 찾으러 가기</Button></div>}
          {past.length > 0 && <><h2 className="group-title">최근 이용</h2><div className="recent-bookings">{past.slice(0, 2).map(recent)}</div></>}
        </> : past.length ? <div className="recent-bookings">{past.map(recent)}</div> : <div className="empty-card"><Icon name="calendar" size={30}/><h3>지난 예약이 없어요</h3><p>이용 완료·취소된 예약을 여기서 확인해요.</p></div>}</div>
      </> : <>
        <section className="settings-group my-settings">{row('계정 정보', p.profile.email, 'user', 'accountInfo')}{row('예약 조건', compactRegion(p.profile.region) + ' · ' + budgetLabel(p.profile.budget), 'pin', 'conditions')}{row('보유 장비', p.profile.equipment.join(' · ') || '없음', 'monitor', 'equipment')}{row('알림 설정', p.profile.notifications ? '켜짐' : '꺼짐', 'clock', 'notifications')}{row('개인정보 · 캡처', '원본 저장 안 함', 'shield', 'privacy')}</section>
        <button className="logout-button" type="button" onClick={() => setLogoutOpen(true)}><Icon name="logout" size={20}/>로그아웃</button>
        <button className="withdraw-link" type="button" onClick={() => { setError(''); setWithdrawOpen(true); }}>회원 탈퇴</button>
      </>}
    </section>
    {detail && <Modal titleId="booking-detail-title" onClose={() => setDetail(null)}><h2 id="booking-detail-title">{detail.approval?.name || detail.conditions.purpose}</h2><Tag>{stateLabel[detail.state] || detail.state}</Tag><p className="caption">{dateLabel(detail.conditions.date)} {detail.conditions.start}–{detail.conditions.end}</p>{detail.bookingReference && <p>예약번호 {detail.bookingReference}</p>}<p className="booking-proof">{detail.evidence || detail.message}</p>{detail.approval && <p>취소 안내: {detail.approval.cancellation}</p>}{detail.simulated && ['PENDING', 'CONFIRMED'].includes(detail.state) && <p className="caption">예약 변경은 기존 예약을 취소한 뒤 새로 신청해 주세요.</p>}{detail.simulated && ['PENDING', 'CONFIRMED'].includes(detail.state) && <Button secondary onClick={() => void p.cancelBooking?.(detail).then(() => setDetail(null))}>예약 취소</Button>}{detail.events.length > 0 && <ol className="event-list">{detail.events.slice(-5).map((item, i) => <li key={i}>{item.message}</li>)}</ol>}{!detail.bookingReference && !['CONFIRMED', 'RECEIVED', 'PENDING'].includes(detail.state) && <Button onClick={() => { p.openTask(detail); setDetail(null); }}>진행 이어가기</Button>}<Button secondary onClick={() => setDetail(null)}>닫기</Button></Modal>}
    {editing && <Modal titleId="profile-edit-title" onClose={() => setEditing(null)}><h2 id="profile-edit-title">{({ accountInfo: '계정 정보', conditions: '예약 조건', equipment: '보유 장비', notifications: '알림 설정', privacy: '개인정보 · 캡처' })[editing]}</h2><form onSubmit={save}>
      {editing === 'accountInfo' && <><label>이름<input autoFocus required value={draft.name} maxLength={40} onChange={e => setDraft({ ...draft, name: e.target.value })}/></label><label>로그인 이메일<input readOnly value={p.profile.email}/></label></>}
      {editing === 'conditions' && <><RegionSearch value={draft.region} onChange={region => setDraft({ ...draft, region })}/><DatePicker label="생년월일" value={draft.birthDate} max={new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date())} onChange={birthDate => setDraft({ ...draft, birthDate })}/><BudgetInput label="희망 예산" value={draft.budget} onChange={budget => { if (budget !== null) setDraft({ ...draft, budget }); }}/></>}
      {editing === 'equipment' && <fieldset><legend>이미 가지고 있는 장비</legend><div className="chips">{equipmentOptions.map(item => <button key={item} type="button" aria-pressed={draft.equipment.includes(item)} onClick={() => setDraft({ ...draft, equipment: draft.equipment.includes(item) ? draft.equipment.filter(x => x !== item) : [...draft.equipment, item] })}>{item}</button>)}</div></fieldset>}
      {editing === 'notifications' && <><label className="switch-row"><span>예약 일정 알림<small>앱 안에서 다가오는 예약을 표시해요</small></span><span className="switch"><input type="checkbox" checked={draft.notifications} onChange={e => setDraft({ ...draft, notifications: e.target.checked })}/><span aria-hidden="true"/></span></label><p className="caption">승인 결과는 앱 안에서 알려드려요.</p></>}
      {editing === 'privacy' && <><Notice>안내문 이미지 원본은 저장하지 않아요.</Notice><p>분석한 이용 조건과 예약 내역은 계정에 저장돼요. 로그아웃해도 예약 내역은 유지돼요.</p></>}
      {error && <Notice error>{error}</Notice>}{editing !== 'privacy' && <Button type="submit">저장</Button>}<Button secondary type="button" onClick={() => setEditing(null)}>닫기</Button>
    </form></Modal>}
    {logoutOpen && <Modal className="logout-dialog" titleId="logout-title" onClose={() => setLogoutOpen(false)}><span className="logout-symbol"><Icon name="logout" size={28}/></span><h2 id="logout-title">로그아웃할까요?</h2><p>예약 내역과 설정은 그대로 남아 있어요.<br/>다시 로그인하면 이어서 볼 수 있어요.</p>{upcoming.length > 0 && <div className="notice yellow compact"><Icon name="shield" size={14}/><span>다가오는 예약 일정이 있어요. 로그아웃하면 알림을 받을 수 없어요.</span></div>}<div className="logout-actions"><Button secondary onClick={() => setLogoutOpen(false)}>취소</Button><Button onClick={() => void p.perform(p.logout)}>로그아웃</Button></div></Modal>}
    {withdrawOpen && <Modal titleId="withdraw-title" onClose={() => { if (!withdrawing) setWithdrawOpen(false); }}><h2 id="withdraw-title">회원 탈퇴할까요?</h2><p>계정과 예약 내역이 삭제되며 복구할 수 없어요. 예약된 공간도 취소돼요.</p><form onSubmit={async e => { e.preventDefault(); if (withdrawing) return; setWithdrawing(true); setError(''); try { await api.withdraw(password); await p.logout(); } catch (e) { setError((e as Error).message); } finally { setWithdrawing(false); } }}><label>현재 비밀번호<input type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)}/></label>{error && <Notice error>{error}</Notice>}<Button disabled={withdrawing}>회원 탈퇴</Button><Button type="button" secondary disabled={withdrawing} onClick={() => setWithdrawOpen(false)}>취소</Button></form></Modal>}

  </>;
}
