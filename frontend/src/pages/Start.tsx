import DatePicker from '../DatePicker';
import RegionSearch from '../RegionSearch';
import { useState, type FormEvent } from 'react';
import { api } from '../api';
import { age, birthInput, passwordStrength } from '../logic';
import { Asset, BudgetInput, Button, Icon, Modal, Notice, Top } from '../components';
import type { Profile, View } from '../types';

export const equipmentOptions = ['노트북', '웹캠', '마이크', '이어폰', '삼각대'];
export const emptyProfile: Profile = { id: '', email: '', name: '', birthDate: '', region: '', budget: 10000, equipment: [], notifications: true };
type Props = { view: View; go: (v: View) => void; profile: Profile; setProfile: (p: Profile) => void; onLogin: (p: Profile) => void };

export default function Start({ view, go, profile, setProfile, onLogin }: Props) {
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [confirmation, setConfirmation] = useState('');
  const [name, setName] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const strength = passwordStrength(password); const years = age(profile.birthDate);
  const [editing, setEditing] = useState<'region' | 'age' | null>(null);
  const toggle = (item: string) => setProfile({ ...profile, equipment: profile.equipment.includes(item) ? profile.equipment.filter(e => e !== item) : [...profile.equipment, item] });
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('');
    if (view === 'signup') {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return setError('이메일 형식을 확인해 주세요.');
      if (strength < 4) return setError('비밀번호는 10자 이상, 영문·숫자·특수문자를 모두 포함해 주세요.');
      if (password !== confirmation) return setError('비밀번호가 일치하지 않습니다.');
      setProfile({ ...profile, name, email }); go('birth'); return;
    }
    if (view === 'birth' && years === null) return setError('올바른 생년월일 8자리를 입력해 주세요.');
    setBusy(true);
    try {
      if (view === 'birth') { await api.signup({ ...profile, password }); onLogin(await api.login(profile.email, password)); }
      else onLogin(await api.login(email, password));
      setPassword(''); setConfirmation('');
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  if (view === 'splash' || view === 'intro') return <section className="intro-page">
    <figure className="intro-art" aria-label="안내문을 읽고 공간을 찾는 과정 예시">
      <div className="intro-ai"><Asset id="3bac0" size={14}/>AI가 조건 3개를 읽었어요</div>
      <div className="intro-paper"><strong>✉ 1차 화상면접 안내</strong><span className="paper-highlight">일시: 11월 27일(금) 14:00</span><span className="paper-highlight">방식: Zoom 화상면접</span><i/><span className="paper-highlight">준비물: 웹캠, 마이크</span><i className="short"/></div>
      <div className="intro-match"><span className="info-icon"><Icon name="pin" size={18}/></span><div><strong>공공 화상면접실</strong><span>13:30–15:30 · 무료</span></div></div>
    </figure>
    <div className="intro-copy"><h1>면접 안내문만 올리면<br/><em>화상면접 공간</em> 예약까지</h1><p>AI가 면접 시각과 필요한 장비를 읽고,<br/>공공·민간 공간을 한 번에 찾아 예약을 도와드려요.</p><div className="step-dots" aria-label="소개 1단계"><i className="active"/><i/><i/></div></div>
    <div className="intro-bottom"><Button onClick={() => go('conditions')}>시작하기</Button><p>이미 계정이 있어요 · <button type="button" onClick={() => go('login')}>로그인</button></p></div>
  </section>;
  if (view === 'conditions') return <section className="onboarding-page">
    <div className="onboarding-progress"><button className="icon-button" aria-label="이전 화면" onClick={() => go('intro')}><Asset id="cea89"/></button><div><span/></div><strong>2/2</strong></div>
    <form onSubmit={e => { e.preventDefault(); go('signup'); }}>
      <h1>예약 조건을<br/>미리 알려주세요</h1><p className="lead">공간 이용 자격과 장비를 확인할 때만 사용해요.<br/>마이페이지에서 언제든 바꿀 수 있어요.</p>
      <fieldset><legend>거주 지역 <small>공공시설 자격 확인용</small></legend><button type="button" className="region-trigger" onClick={() => setEditing('region')}><Icon name="pin" size={18}/><span>{profile.region || '거주 지역을 선택해 주세요'}</span><Asset id="da022" size={16}/></button></fieldset>
      <fieldset><legend>나이 <small>청년 대상 시설 확인용</small></legend><div className="chips age-chips">{['만 19–24세', '만 25–29세', '만 30–34세', '그 외'].map((label, i) => <button key={label} type="button" aria-pressed={years !== null && (i === 0 ? years >= 19 && years <= 24 : i === 1 ? years >= 25 && years <= 29 : i === 2 ? years >= 30 && years <= 34 : years < 19 || years > 34)} onClick={() => setEditing('age')}>{label}</button>)}</div></fieldset>
      <fieldset><legend>가지고 있는 장비 <small>있는 건 빼고 찾아요</small></legend><div className="chips">{equipmentOptions.map(item => <button key={item} type="button" aria-pressed={profile.equipment.includes(item)} onClick={() => toggle(item)}>{profile.equipment.includes(item) && '✓ '}{item}</button>)}</div></fieldset>
      <BudgetInput label="희망 예산" value={profile.budget} onChange={budget => { if (budget !== null) setProfile({ ...profile, budget }); }}/>
      <Button type="submit" className="onboarding-submit">완료하고 시작하기</Button>
    </form>
    {editing && <Modal titleId="onboarding-edit" onClose={() => setEditing(null)}><h2 id="onboarding-edit">{editing === 'region' ? '거주 지역' : '나이 확인'}</h2><form onSubmit={e => { e.preventDefault(); if (editing === 'age' && years === null) return; setEditing(null); }}>
      {editing === 'region' ? <RegionSearch value={profile.region} onChange={region => setProfile({ ...profile, region })}/> : <><p>시설의 연령 조건을 정확히 확인할 수 있도록 생년월일을 입력해 주세요.</p><DatePicker label="생년월일" value={profile.birthDate} max={new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date())} onChange={birthDate => setProfile({ ...profile, birthDate })}/>{years !== null && <p>만 {years}세</p>}</>}
      <Button type="submit">선택 완료</Button><Button secondary type="button" onClick={() => setEditing(null)}>닫기</Button></form></Modal>}
  </section>;
  return <><Top title={view === 'login' ? '로그인' : '회원가입'} back={() => go(view === 'birth' ? 'signup' : 'conditions')} label={view === 'login' ? '다시 만나 반가워요' : `시작하기 ${view === 'birth' ? 3 : 2} / 3`}/><section className="page auth"><div className="mini-mark"><Icon name={view === 'birth' ? 'calendar' : 'user'} size={30}/></div><h1>{view === 'login' ? <>다음 준비도<br/>함께해요.</> : view === 'birth' ? <>생년월일을<br/>알려주세요.</> : <>준비를 이어갈<br/>계정을 만들어요.</>}</h1><p className="lead">{view === 'birth' ? '시설별 연령 조건을 확인하는 데 사용해요.' : '이메일로 로그인하고 예약 계획을 이어가세요.'}</p><form onSubmit={submit}>
    {view === 'birth' ? <><label>생년월일<input autoFocus inputMode="numeric" value={birthInput(profile.birthDate)} placeholder="YYYY-MM-DD" maxLength={10} onChange={e => setProfile({ ...profile, birthDate: birthInput(e.target.value) })}/></label>{years !== null && <Notice>만 {years}세 · 시설별 자격은 예약 시 확인해요.</Notice>}</> : <>
      {view === 'signup' && <label>이름<input autoComplete="name" required value={name} maxLength={40} onChange={e => setName(e.target.value)} placeholder="이름"/></label>}
      <label>이메일<input autoComplete="email" type="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="hello@example.com" maxLength={254}/></label>
      <label>비밀번호<input autoComplete={view === 'login' ? 'current-password' : 'new-password'} type="password" required value={password} onChange={e => setPassword(e.target.value)} placeholder={view === 'login' ? '비밀번호' : '10자 이상, 영문·숫자·특수문자'} maxLength={72}/></label>
      {view === 'signup' && <><div className="strength" aria-label={`비밀번호 강도 ${strength}/4`}>{[1, 2, 3, 4].map(n => <i className={strength >= n ? 'active' : ''} key={n}/>)}</div><label>비밀번호 확인<input type="password" autoComplete="new-password" required value={confirmation} onChange={e => setConfirmation(e.target.value)} placeholder="한 번 더 입력해 주세요" maxLength={72}/></label>{confirmation && confirmation !== password && <span className="field-error">비밀번호가 일치하지 않습니다.</span>}</>}
    </>}{error && <Notice error>{error}</Notice>}<Button disabled={busy}>{busy ? '처리 중…' : view === 'login' ? '로그인' : view === 'birth' ? '가입하고 시작하기' : '다음'}<Icon name="arrow" size={18}/></Button></form><button className="text-button" onClick={() => go(view === 'login' ? 'signup' : 'login')}>{view === 'login' ? '아직 계정이 없어요 · 회원가입' : '이미 계정이 있어요 · 로그인'}</button><button className="text-button muted" onClick={() => go('home')}>로그인 없이 둘러보기</button></section></>;
}
