import DatePicker from '../DatePicker';
import RegionSearch from '../RegionSearch';
import { useState, type FormEvent } from 'react';
import { api } from '../api';
import { age, bookingGroups, budgetLabel, compactRegion, dateLabel, daysUntil, money, safeUrl, stateLabel } from '../logic';
import { Asset, BudgetInput, Button, Icon, Modal, Notice, Tag, Top } from '../components';
import { equipmentOptions } from './Start';
import type { AgentTask, Profile, View } from '../types';

type Props = { view: View; profile: Profile; tasks: AgentTask[]; loggedIn: boolean; go: (v: View) => void; onSave: (p: Profile) => void; logout: () => Promise<void>; openTask: (t: AgentTask) => void; perform: (f: () => Promise<unknown>) => Promise<void> };
type Edit = 'name' | 'email' | 'region' | 'age' | 'equipment' | 'budget' | 'notifications';
export default function Account(p: Props) {
  const [draft, setDraft] = useState(p.profile); const [editing, setEditing] = useState<Edit | null>(null);
  const [detail, setDetail] = useState<AgentTask | null>(null);
  const [logoutOpen, setLogoutOpen] = useState(false); const [error, setError] = useState('');
  const groups = bookingGroups(p.tasks); const years = age(p.profile.birthDate);
  async function save(e: FormEvent) {
    e.preventDefault(); if (!draft.name.trim() || age(draft.birthDate) === null) { setError('이름과 생년월일을 확인해 주세요.'); return; }
    await p.perform(async () => { const saved = await api.saveProfile(draft); p.onSave(saved); setDraft(saved); setEditing(null); setError(''); });
  }
  function edit(value: Edit) { setDraft(p.profile); setError(''); setEditing(value); }
  async function notify(checked: boolean) { await p.perform(async () => { const saved = await api.saveProfile({ ...p.profile, notifications: checked }); p.onSave(saved); setDraft(saved); }); }
  function row(label: string, value: string, target: Edit | null) {
    return <button type="button" className="setting-button" onClick={() => target && edit(target)} disabled={!target}><span>{label}</span><span>{value}</span>{target && <Asset id="da022" size={16}/>}</button>;
  }
  function booking(t: AgentTask) {
    const days = daysUntil(t.conditions.date);
    return <article className={'booking-card ' + (t.state === 'CONFIRMED' ? 'confirmed' : ['RECEIVED', 'PENDING'].includes(t.state) ? 'waiting' : '')} key={t.id}>
      <div className="booking-badges"><Tag tone={t.state === 'CONFIRMED' ? 'green' : 'yellow'}>{stateLabel[t.state] || t.state}</Tag>{t.state === 'CONFIRMED' && Number.isFinite(days) && days >= 0 && <Tag tone="gray">{days === 0 ? 'D-Day' : 'D-' + days}</Tag>}{t.bookingReference && <small>No. {t.bookingReference}</small>}</div>
      <h3><button type="button" className="booking-title" onClick={() => setDetail(t)}>{t.approval?.name || t.conditions.purpose}</button></h3><p className="candidate-meta"><Asset id="ee3ab" size={14}/>{dateLabel(t.conditions.date)} {t.conditions.start}–{t.conditions.end} · {money(t.approval?.totalPrice ?? t.conditions.budget)}</p>
      {t.state === 'CONFIRMED' ? <div className="timeline"><div><i/><span>입실</span><strong>{t.conditions.start}</strong></div><div><i/><span>이용</span><strong>{t.conditions.purpose}</strong></div><div><i/><span>퇴실</span><strong>{t.conditions.end}</strong></div></div> : ['RECEIVED', 'PENDING'].includes(t.state) && <div className="notice yellow compact"><Icon name="clock" size={14}/>시설의 최종 확정 안내를 확인해 주세요</div>}
      {t.state === 'CONFIRMED' && <div className="booking-actions"><button type="button" onClick={() => setDetail(t)}><Icon name="calendar" size={14}/>진행 내용</button>{t.approval && safeUrl(t.approval.url) && <a href={safeUrl(t.approval.url)} target="_blank" rel="noreferrer"><Icon name="shield" size={14}/>이용 안내</a>}</div>}
    </article>;
  }
  if (!p.loggedIn) return <><Top title="마이" leading/><section className="page empty"><h1>로그인하고<br/>나의 준비를 확인해요</h1><Button onClick={() => p.go('login')}>이메일로 로그인</Button><button className="text-button" onClick={() => p.go('signup')}>계정 만들기</button></section></>;
  return <><Top title="마이" leading right={<button type="button" className="icon-button" aria-label="계정 설정" onClick={() => p.view === 'account' ? edit('notifications') : p.go('account')}><Asset id="5853e" size={22}/></button>}/>
    <section className="page account-page">
      <section className="profile-hero"><div className="profile-identity"><div className="avatar">{p.profile.name.slice(p.profile.name.length > 1 ? 1 : 0, p.profile.name.length > 1 ? 2 : 1)}</div><div><h1>{p.profile.name}</h1><p>{compactRegion(p.profile.region) || '지역 미설정'} · {years === null ? '나이 확인 필요' : '만 ' + years + '세'}</p></div><Tag>시설별 자격 확인</Tag></div><div className="profile-stats"><div><strong>{groups.scheduled.length}</strong><span>예정</span></div><div><strong>{groups.pending.length}</strong><span>승인 대기</span></div><div><strong>{groups.completed.length}</strong><span>이용 완료</span></div></div></section>
      <div className="account-tabs"><button type="button" aria-current={p.view === 'reservations' ? 'page' : undefined} onClick={() => p.go('reservations')}>예약내역</button><button type="button" aria-current={p.view === 'account' ? 'page' : undefined} onClick={() => p.go('account')}>계정관리</button></div>
      {p.view === 'reservations' ? <>
        <h2 className="group-title">다가오는 예약 <em>{groups.scheduled.length + groups.pending.length}</em></h2>
        {groups.scheduled.concat(groups.pending).sort((a, b) => (a.conditions.date + a.conditions.start).localeCompare(b.conditions.date + b.conditions.start)).map(booking)}
        {!groups.scheduled.length && !groups.pending.length && <div className="empty-card"><Icon name="calendar" size={30}/><h3>아직 예약한 공간이 없어요</h3><Button secondary onClick={() => p.go('home')}>공간 찾으러 가기</Button></div>}
        {groups.working.length > 0 && <><h2 className="group-title">진행 중인 준비</h2>{groups.working.map(booking)}</>}
        <h2 className="group-title">지난 예약</h2>{groups.completed.map(booking)}{!groups.completed.length && <p className="caption">지난 예약이 없어요.</p>}
      </> : <>
        <section className="settings-section"><h2>기본 정보</h2><div className="settings-group">{row('이름', p.profile.name, 'name')}{row('이메일', p.profile.email, 'email')}{row('휴대폰', '미등록 · 이메일 로그인', null)}</div></section>
        <section className="settings-section"><h2>예약 조건 <small>자격·장비 확인에 사용돼요</small></h2><div className="settings-group">{row('거주 지역', compactRegion(p.profile.region) || '지역 설정', 'region')}{row('나이', years === null ? '나이 확인 필요' : '만 ' + years + '세', 'age')}{row('보유 장비', p.profile.equipment.join(' · ') || '없음', 'equipment')}{row('희망 예산', budgetLabel(p.profile.budget), 'budget')}</div></section>
        <section className="settings-section"><h2>알림 · 개인정보</h2><div className="settings-group">
          <label className="switch-row"><span>예약 일정 알림<small>앱 안에서 다가오는 예약을 표시해요</small></span><span className="switch"><input type="checkbox" checked={p.profile.notifications} onChange={e => void notify(e.target.checked)}/><span aria-hidden="true">{p.profile.notifications && <img src="/figma/f3f65.svg" alt=""/>}</span></span></label>
          <label className="switch-row"><span>입실 30분 전 알림<small>푸시 알림 연결 전이에요</small></span><span className="switch"><input type="checkbox" checked={false} disabled/><span aria-hidden="true"/></span></label>
        </div></section>
        <p className="caption">올린 캡처는 분석에만 사용하고 원본은 저장하지 않아요.</p>
        <div className="account-links"><button type="button" onClick={() => setLogoutOpen(true)}>로그아웃</button></div>
      </>}
    </section>
    {detail && <Modal titleId="booking-detail-title" onClose={() => setDetail(null)}><h2 id="booking-detail-title">{detail.approval?.name || detail.conditions.purpose}</h2><Tag>{stateLabel[detail.state] || detail.state}</Tag><p className="caption">{dateLabel(detail.conditions.date)} {detail.conditions.start}–{detail.conditions.end}</p>{detail.bookingReference && <p>예약번호 {detail.bookingReference}</p>}<p className="booking-proof">{detail.evidence || detail.message}</p>{detail.events.length > 0 && <ol className="event-list">{detail.events.slice(-5).map((item, i) => <li key={i}>{item.message}</li>)}</ol>}{!['CONFIRMED', 'RECEIVED', 'PENDING'].includes(detail.state) && <Button onClick={() => { p.openTask(detail); setDetail(null); }}>진행 이어가기</Button>}<Button secondary onClick={() => setDetail(null)}>닫기</Button></Modal>}
    {editing && <Modal titleId="profile-edit-title" onClose={() => setEditing(null)}><h2 id="profile-edit-title">{({ name: '이름', email: '이메일', region: '거주 지역', age: '나이', equipment: '보유 장비', budget: '희망 예산', notifications: '예약 일정 알림' })[editing]}</h2><form onSubmit={save}>
      {editing === 'name' && <label>이름<input autoFocus required value={draft.name} maxLength={40} onChange={e => setDraft({ ...draft, name: e.target.value })}/></label>}
      {editing === 'email' && <label>로그인 이메일<input readOnly value={p.profile.email}/></label>}
      {editing === 'region' && <RegionSearch value={draft.region} onChange={region => setDraft({ ...draft, region })}/>}
      {editing === 'age' && <DatePicker label="생년월일" value={draft.birthDate} max={new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date())} onChange={birthDate => setDraft({ ...draft, birthDate })}/>}
      {editing === 'equipment' && <fieldset><legend>이미 가지고 있는 장비</legend><div className="chips">{equipmentOptions.map(item => <button key={item} type="button" aria-pressed={draft.equipment.includes(item)} onClick={() => setDraft({ ...draft, equipment: draft.equipment.includes(item) ? draft.equipment.filter(x => x !== item) : [...draft.equipment, item] })}>{item}</button>)}</div></fieldset>}
      {editing === 'budget' && <BudgetInput label="희망 예산" value={draft.budget} onChange={budget => { if (budget !== null) setDraft({ ...draft, budget }); }}/>}
      {editing === 'notifications' && <label className="checkbox"><input type="checkbox" checked={draft.notifications} onChange={e => setDraft({ ...draft, notifications: e.target.checked })}/>앱에서 다가오는 예약을 표시해요</label>}
      {error && <Notice error>{error}</Notice>}<Button type="submit">{editing === 'email' ? '확인' : '저장'}</Button><Button secondary type="button" onClick={() => setEditing(null)}>닫기</Button>
    </form></Modal>}
    {logoutOpen && <Modal titleId="logout-title" onClose={() => setLogoutOpen(false)}><h2 id="logout-title">로그아웃할까요?</h2><p>예약 내역과 설정은 그대로 남아 있어요.</p><Button onClick={() => void p.perform(p.logout)}>로그아웃</Button><Button secondary onClick={() => setLogoutOpen(false)}>돌아가기</Button></Modal>}
  </>;
}
