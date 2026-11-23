import DatePicker from '../DatePicker';
import CameraCapture from '../CameraCapture';
import { api } from '../api';
import RegionSearch from '../RegionSearch';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Asset, BudgetInput, Button, CandidateCard, Icon, InfoRow, Modal, Notice, Tag, TimePicker, Top } from '../components';
import { bookingGroups, budgetLabel, compactRegion, conditionError, dateLabel, extractedStart, isPastSlot, isPublicCandidate, money, running, safeUrl, stateLabel, taskView, timeLabel } from '../logic';
import type { AgentTask, Candidate, Conditions, Profile, View } from '../types';

type Props = { view: View; go: (v: View) => void; profile: Profile; loggedIn: boolean; task: AgentTask | null; tasks: AgentTask[]; busy: boolean; perform: (action: () => Promise<unknown>) => Promise<void>; analyze: (text: string, file?: File) => Promise<void>; search: (conditions: Conditions) => Promise<void>; select: (id: string) => Promise<void>; respond: (answer: string, submitted?: boolean) => Promise<void>; approve: () => Promise<void>; stop: () => Promise<void> };
type Edit = 'schedule' | 'purpose' | 'equipment' | 'space' | 'budget';
const analysisSlides = [
  { title: '안내문을 읽고 있어요', copy: '작은 글씨 속 중요한 내용도 놓치지 않도록.', label: '안내문 읽기', icon: 'upload', tags: ['안내문', '중요한 내용'] },
  { title: '필요한 정보만 쏙쏙', copy: '언제, 어디서, 무엇이 필요한지 정리해요.', label: '이용 정보 정리', icon: 'calendar', tags: ['날짜와 시간', '필요한 장비'] },
  { title: '정리가 끝나면 만나요', copy: '완료되면 내용 확인 화면으로 바로 이동해요.', label: '결과 확인 안내', icon: 'monitor', tags: ['내 조건에 맞게', '한눈에 확인'] },
];

export function AnalysisCarousel({ onCancel }: { onCancel?: () => void }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  useEffect(() => {
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const reduce = () => { if (motion.matches) setPaused(true); };
    reduce(); motion.addEventListener('change', reduce);
    return () => motion.removeEventListener('change', reduce);
  }, []);
  useEffect(() => {
    if (paused) return;
    const timer = window.setInterval(() => setIndex(value => (value + 1) % analysisSlides.length), 3600);
    return () => window.clearInterval(timer);
  }, [paused]);
  return <section className="analysis-carousel" aria-label="분석 대기 안내" aria-roledescription="캐러셀">
    <p className="sr-only" role="status">안내문 분석 중이에요. 완료되면 결과 화면으로 이동해요.</p>
    <div className="analysis-window">
      <div className="analysis-track" style={{ transform: 'translateX(-' + index * 100 + '%)' }}>
        {analysisSlides.map((slide, i) => <section key={slide.label} className={'analysis-slide scene-' + i} aria-hidden={index !== i}>
          <div className="analysis-art" aria-hidden="true">
            <span className="analysis-orbit orbit-one"/><span className="analysis-orbit orbit-two"/>
            <div className="analysis-paper"><span className="analysis-paper-heading"><Icon name={slide.icon} size={19}/><span/><Icon name="spark" size={17}/></span><i/><i/><i/><i/></div>
            <span className="analysis-float float-first"><Icon name={i === 0 ? 'spark' : 'clock'} size={17}/>{slide.tags[0]}</span>
            <span className="analysis-float float-second"><Icon name={i === 2 ? 'check' : 'monitor'} size={17}/>{slide.tags[1]}</span>
          </div>
          <h1>{slide.title}</h1><p>{slide.copy}</p>
        </section>)}
      </div>
    </div>
    <div className="analysis-pagination">
      <div className="analysis-dots" aria-label="안내 페이지">{analysisSlides.map((slide, i) => <button key={slide.label} type="button" aria-label={(i + 1) + ' / 3 · ' + slide.label} aria-pressed={index === i} onClick={() => { setIndex(i); setPaused(true); }}><span/></button>)}</div>
      <button className="analysis-pause" type="button" aria-label={paused ? '안내 자동 넘김 재생' : '안내 자동 넘김 일시정지'} onClick={() => setPaused(value => !value)}>{paused ? '▶' : 'Ⅱ'}</button>
    </div>
    {onCancel && <button className="text-button analysis-cancel" type="button" onClick={onCancel}>분석 취소</button>}
  </section>;
}

export default function Flow(p: Props & { browse?: () => Promise<void> }) {
  const [nearby, setNearby] = useState<Candidate[]>([]);
  const [nearbyError, setNearbyError] = useState('');
  useEffect(() => {
    if (p.view !== 'home') return;
    let alive = true;
    void api.spaces(p.profile.region || '부산광역시 사하구').then(rooms => { if (alive) { setNearby(rooms); setNearbyError(''); } }).catch(() => { if (alive) setNearbyError('공간 목록을 불러오지 못했어요. 잠시 후 다시 방문해 주세요.'); });
    return () => { alive = false; };
  }, [p.view, p.profile.region]);
  const [text, setText] = useState(''); const gallery = useRef<HTMLInputElement>(null);
  const [conditions, setConditions] = useState<Conditions | null>(p.task?.conditions ?? null);
  const [editing, setEditing] = useState<Edit | null>(null); const [error, setError] = useState(''); const [fileError, setFileError] = useState('');
  const [answer, setAnswer] = useState('');
  const [category, setCategory] = useState('전체');
  const task = p.task; const active = !!task && running(task.state);
  const needResponse = task?.state === 'INPUT_REQUIRED';
  const upcoming = bookingGroups(p.tasks).scheduled.sort((a, b) => (a.conditions.date + a.conditions.start).localeCompare(b.conditions.date + b.conditions.start));
  useEffect(() => { setConditions(p.task?.conditions ?? null); setError(''); setEditing(null); setCategory('전체'); }, [p.task?.id, p.task?.state]);
  async function chooseFile(value?: File) {
    if (!value || p.busy || active) return;
    setFileError(''); setError('');
    if (!['image/jpeg', 'image/png'].includes(value.type) || value.size > 8 * 1024 * 1024) { setFileError('8MB 이하의 JPG·PNG 이미지를 올려주세요.'); return; }
    if (!p.loggedIn) { p.go('login'); return; }
    await p.perform(() => p.analyze('', value));
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!p.loggedIn) { p.go('login'); return; }
    await p.perform(() => p.analyze(text));
  }
  function search(e: FormEvent) {
    e.preventDefault(); if (!conditions) return;
    const problem = conditionError(conditions); setError(problem);
    if (!problem) void p.perform(() => p.search(conditions));
  }
  const candidates = (task?.candidates ?? []).filter(c => c.price !== null && task?.bookingChecks?.[c.id]?.status === 'READY');
  const visible = candidates.filter(c => category === '전체' || (category === '공공' ? isPublicCandidate(c) : !isPublicCandidate(c)));
  const imageInputs = <><input ref={gallery} className="sr-only" type="file" accept="image/png,image/jpeg" onChange={e => { void chooseFile(e.target.files?.[0]); e.target.value = ''; }}/></>;
  const resultList = <>
    {task?.conditions && <section className="search-summary"><div className="row spread"><strong>검색 조건</strong><button type="button" className="text-link" disabled={active} onClick={() => p.go('extracted')}>수정</button></div><div className="chips small"><span>{dateLabel(task.conditions.date)} {task.conditions.start}–{task.conditions.end}</span><span>{task.conditions.region}</span><span>{budgetLabel(task.conditions.budget)}</span>{task.conditions.equipment.map(item => <span key={item}>{item}</span>)}</div></section>}
    <div className="result-tabs" aria-label="공간 구분">{['전체', '공공', '민간'].map(value => <button key={value} type="button" aria-pressed={category === value} onClick={() => setCategory(value)}>{value} {value === '전체' ? candidates.length : candidates.filter(c => value === '공공' ? isPublicCandidate(c) : !isPublicCandidate(c)).length}</button>)}</div>
    <div className="results-list">{visible.map(c => <CandidateCard key={c.id} candidate={c} availability={task?.availability?.[c.id]} booking={task?.bookingChecks?.[c.id]} disabled={active || p.busy} recommended={c.id === candidates[0]?.id} onSelect={() => void p.perform(() => p.select(c.id))}/>)}</div>
    {!visible.length && !active && <Notice>확인된 후보가 아직 없어요. 조건을 바꿔 다시 찾아보세요.</Notice>}
  </>;
  return <>{imageInputs}
    {p.view === 'capture' ? <header className="page-top camera-header"><button type="button" className="icon-button" aria-label="촬영 닫기" onClick={() => p.go('home')}><Icon name="close"/></button><strong>면접 안내문 촬영</strong><span className="header-space"/></header> : <Top title={p.view === 'home' ? '홈' : p.view === 'results' || p.view === 'approval' ? task?.state === 'SEARCHING' ? '공간 검색 중' : '공간 검색 결과 ' + candidates.length + '곳' : p.view === 'analyzing' ? '안내문 분석' : '추출 결과 확인'} brand={p.view === 'home'} back={p.view === 'home' ? undefined : () => p.go('home')} right={p.view === 'home' ? <button type="button" className="icon-button" aria-label="예약 알림 확인" onClick={() => p.go('reservations')}><Asset id="fa917"/></button> : undefined}/>}
    <section className={'page flow ' + p.view + '-page'}>
      {fileError && <Notice error>{fileError}</Notice>}
      {p.view === 'home' && <>
        <div className="home-greeting"><span className="avatar">{p.profile.name?.slice(0, 1) || '당'}</span><div><small>안녕하세요 👋</small><strong>{p.profile.name || '찾았당'}님</strong></div></div>
        <h1 className="home-title">필요한 공간과 물품은<br/><em>찾았당</em>이 찾아드려요.</h1>
        <section className="home-upload"><Tag><Icon name="spark" size={13}/>AI 자동 인식</Tag><h2>면접 안내문을<br/>올려주세요</h2><p>일정부터 공간 예약까지 한 번에</p>
          <div className="hero-document" aria-hidden="true"><strong>면접 안내</strong><i/><i className="highlight"/><i/><i className="highlight"/><i/></div>
          <div className="upload-actions"><button type="button" onClick={() => { setFileError(''); p.go('capture'); }}><Icon name="upload" size={18}/>촬영하기</button><button type="button" onClick={() => gallery.current?.click()}><Icon name="camera" size={16}/>앨범 선택</button></div>
        </section>
        <form className="home-search" onSubmit={e => void submit(e)}><Icon name="spark" size={20}/><label className="sr-only" htmlFor="request">AI에게 요청하기</label><textarea id="request" value={text} maxLength={6000} onChange={e => setText(e.target.value)} placeholder="AI에게 무엇이든 물어보세요" rows={text.length > 35 ? 3 : 1}/><button type="submit" className="send" aria-label="AI 분석 시작" disabled={p.busy || active || !text.trim()}><Icon name="arrow" size={18}/></button></form>
        <div className="home-quick">{['무료 공간만', '오후 예약 가능', '1만 원 이내', 'PC·웹캠 구비'].map(label => <button key={label} type="button" onClick={() => setText(label + ' 내일 오후 2시 화상면접 공간')}>{label}</button>)}</div>
        <div className="nearby-heading"><h2>근처 면접 공간</h2><button className="text-link" type="button" onClick={() => void p.browse?.()}>직접 찾아보기 ›</button></div>
        <div className="nearby-list">{nearby.slice(0,3).map((room, i) => <button type="button" key={room.id} className="nearby-room" onClick={() => void p.browse?.()}><span className={'room-thumb thumb-' + i}><Icon name={i === 0 ? 'monitor' : 'room'} size={23}/></span><span className="room-copy"><strong>{room.name}</strong><small>{room.equipment.slice(0,3).join(' · ')}</small></span><span className="room-cost"><strong>{money(room.price)}</strong><small>{isPublicCandidate(room) ? '공공' : '민간'}</small></span></button>)}{nearbyError && <p className="caption">{nearbyError}</p>}{!nearby.length && !nearbyError && <p className="caption">지역 공간을 찾고 있어요…</p>}</div>

        {p.profile.notifications && upcoming.length > 0 && <button type="button" className="booking-reminder" onClick={() => p.go('reservations')}><Asset id="6fe04" size={18}/><span>다가오는 예약 {upcoming.length}건<strong>{dateLabel(upcoming[0].conditions.date)} {upcoming[0].conditions.start} · {upcoming[0].approval?.name || upcoming[0].conditions.purpose}</strong></span><Asset id="67a56" size={18}/></button>}
        {task && !['CONFIRMED', 'STOPPED'].includes(task.state) && <button className="text-button" type="button" onClick={() => p.go(taskView(task))}>진행 중인 준비 이어보기</button>}
      </>}
      {p.view === 'capture' && <CameraCapture busy={p.busy || active} onCapture={chooseFile} onGallery={() => gallery.current?.click()}/>}
{p.view === 'analyzing' && (task && ['FAILED', 'STOPPED'].includes(task.state) ? <section className="analysis-screen" role="status"><span className="analysis-symbol"><Icon name="spark" size={42}/></span><h1>{task.state === 'FAILED' ? '안내문을 읽지 못했어요' : '분석을 중단했어요'}</h1><p>{task.message}</p><Button secondary onClick={() => p.go('home')}>다시 입력하기</Button></section> : <AnalysisCarousel onCancel={task?.state === 'ANALYZING' ? () => void p.perform(async () => { await p.stop(); p.go('home'); }) : undefined}/>)}
      {p.view === 'extracted' && conditions && <form className="extracted-form" onSubmit={search}>
        <div className="extraction-intro"><div className="document-symbol" aria-hidden="true"><i/><i/><i/><i/></div><div><Tag>AI 추출 완료</Tag><h1>안내문에서 이용 정보를 읽었어요</h1><p>틀린 내용은 눌러서 바로 고칠 수 있어요</p></div></div>
        <h2 className="group-title">{conditions.purpose.includes('면접') ? '면접 정보' : '이용 정보'}</h2><section className="info-group">
          <InfoRow icon="clock" label="대여 일시" value={conditions.date ? dateLabel(conditions.date, true) + ' ' + timeLabel(extractedStart(conditions)) : '입력이 필요해요'} badge={conditions.date && conditions.start ? 'AI 추출' : '확인 필요'} danger={!conditions.date || !conditions.start} onClick={() => setEditing('schedule')}/>
          <InfoRow icon="monitor" label="이용 목적" value={conditions.purpose || '입력이 필요해요'} badge={conditions.purpose ? 'AI 추출' : '확인 필요'} onClick={() => setEditing('purpose')}/>
          <InfoRow icon="monitor" label="필요 장비" value={<>{conditions.equipment.join(' · ') || '장비를 선택해 주세요'}{(conditions.ownedEquipment ?? []).length > 0 && <small className="owned-equipment">보유: {conditions.ownedEquipment.join(' · ')}</small>}</>} onClick={() => setEditing('equipment')}/>
        </section>
        <h2 className="group-title with-icon"><Icon name="spark" size={18}/>AI가 제안한 예약 조건</h2><section className="info-group suggested">
          <InfoRow icon="clock" label="예약 시간" value={conditions.start && conditions.end ? conditions.start + ' ~ ' + conditions.end : '입실·퇴실 시간을 확인해 주세요'} onClick={() => setEditing('schedule')} danger={!conditions.start || !conditions.end}/>
          <InfoRow icon="room" label="공간 조건" value={'1인 독립 공간 · ' + (compactRegion(conditions.region) || '지역 확인 필요')} onClick={() => setEditing('space')}/>
          <InfoRow icon="bag" label="예산" value={conditions.budget === null ? '입력이 필요해요' : budgetLabel(conditions.budget)} badge={conditions.budget === null ? '확인 필요' : undefined} danger={conditions.budget === null} onClick={() => setEditing('budget')}/>
        </section>
        {isPastSlot(conditions.date, conditions.start) && <Notice error>안내문의 일정은 이미 지났어요.<br/>이용할 날짜와 시간을 수정해 주세요.</Notice>}
        {error && <Notice error>{error}</Notice>}
        {conditions.notes.length > 0 && <div className="extraction-notes">{conditions.notes.filter(note => !note.startsWith('안내문 일정 ')).map((note, i) => <p key={i}>{note}</p>)}</div>}
        <footer className="extraction-footer"><p>추출 내용이 안내문과 같은지 확인해 주세요</p><div><Button type="button" secondary onClick={() => { setFileError(''); p.go('capture'); }}>다시 촬영</Button><Button className="dark" type="submit" disabled={p.busy || active}>이 조건으로 공간 찾기</Button></div></footer>
      </form>}
      {(p.view === 'results' || p.view === 'approval') && resultList}
      {p.view === 'approval' && task?.approval && <Modal titleId="approval-title" className="approval-sheet" onClose={() => p.go('results')}>
        <span className="sheet-handle"/><h2 id="approval-title">이 내용으로 예약할까요?</h2>
        <dl className="approval-details"><dt>공간</dt><dd>{task.approval.name}</dd><dt>일시</dt><dd>{dateLabel(task.approval.date)} {task.approval.start}–{task.approval.end}</dd><dt>주소</dt><dd>{candidates.find(c => c.id === task.selectedId)?.address || '주소 확인 필요'}</dd><dt>비용</dt><dd className="green-text">{money(task.approval.totalPrice)}</dd><dt>준비물</dt><dd>{[...(candidates.find(c => c.id === task.selectedId)?.eligibility?.includes('신분증') ? ['신분증'] : []), ...task.conditions.ownedEquipment].join(' · ') || '시설 이용 안내 참고'}</dd><dt>취소</dt><dd>{task.approval.cancellation}</dd></dl>
        <p className="approval-check"><Icon name="check" size={17}/>예약 일시·총금액·취소 조건을 확인했어요</p>
        <div className="notice yellow"><Icon name="shield" size={16}/><span>승인되면 예약 내역에서 확인할 수 있어요.</span></div>
        <div className="approval-actions"><Button secondary onClick={() => p.go('results')}>취소</Button><Button disabled={active || p.busy || task.state !== 'APPROVAL_REQUIRED'} onClick={() => void p.perform(p.approve)}>{p.busy || active ? '예약 진행 중…' : '승인하고 예약하기'}</Button></div>
      </Modal>}
    {p.view !== 'analyzing' && p.view !== 'capture' && task && (active || needResponse || ['FAILED', 'HANDOFF', 'AUTH_REQUIRED', 'UNKNOWN', 'STOPPED'].includes(task.state)) && <section className="agent-panel" aria-live="polite">
      <div className="row"><span className={active ? 'spinner' : 'agent-symbol'}>{!active && <Icon name="spark" size={21}/>}</span><h2>{stateLabel[task.state] ?? task.state}</h2></div>
      <p>{task.message}</p>
      {needResponse && <form onSubmit={e => { e.preventDefault(); void p.perform(async () => { await p.respond(answer); setAnswer(''); }); }}><Notice>{task.question || '예약에 필요한 추가 정보를 알려주세요.'}</Notice><label>추가 정보<input required value={answer} onChange={e => setAnswer(e.target.value)} maxLength={2000}/></label><Button disabled={p.busy}>확인하고 이어가기</Button></form>}
      {['HANDOFF', 'AUTH_REQUIRED'].includes(task.state) && <Button secondary onClick={() => p.go('results')}>다른 공간 선택하기</Button>}
      {['HANDOFF', 'AUTH_REQUIRED'].includes(task.state) && safeUrl(task.approval?.url || candidates.find(c => c.id === task.selectedId)?.url || '') && <a className="button secondary" href={safeUrl(task.approval?.url || candidates.find(c => c.id === task.selectedId)?.url || '')} target="_blank" rel="noreferrer">시설 사이트에서 이어서 예약하기</a>}
      {['FAILED', 'STOPPED'].includes(task.state) && <Button secondary onClick={() => p.go('extracted')}>조건을 확인하고 다시 찾기</Button>}
      {(active || needResponse) && <button className="text-button" onClick={() => void p.perform(p.stop)}>{task.state === 'SEARCHING' ? '검색 중단' : '진행 중단'}</button>}
      {task.state === 'UNKNOWN' && <Notice error>신청 결과를 확인하지 못했어요. 중복 신청을 막기 위해 외부 예약 내역을 먼저 확인해 주세요.</Notice>}
    </section>}
      {editing && conditions && <Modal titleId="condition-edit-title" onClose={() => setEditing(null)}>
        <h2 id="condition-edit-title">{({ schedule: '이용 날짜·시간', purpose: '이용 목적', equipment: '필요한 장비', space: '공간 조건', budget: '총예산' })[editing]}</h2>
        <form onSubmit={e => { e.preventDefault(); setEditing(null); setError(''); }}>
          {editing === 'schedule' && <><DatePicker label="이용 날짜" value={conditions.date} min={new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date())} onChange={date => setConditions({ ...conditions, date })}/><div className="form-grid"><TimePicker label="입실 시간" value={conditions.start} onChange={start => setConditions({ ...conditions, start })}/><TimePicker label="퇴실 시간" value={conditions.end} onChange={end => setConditions({ ...conditions, end })}/></div></>}
          {editing === 'purpose' && <label>이용 목적<input required autoFocus maxLength={100} value={conditions.purpose} onChange={e => setConditions({ ...conditions, purpose: e.target.value })}/></label>}
          {editing === 'equipment' && <><label>추가로 필요한 장비<input value={conditions.equipment.join(', ')} onChange={e => setConditions({ ...conditions, equipment: e.target.value.split(',').map(x => x.trim()).filter(Boolean) })}/></label><label>내가 가진 장비<input value={(conditions.ownedEquipment ?? []).join(', ')} onChange={e => setConditions({ ...conditions, ownedEquipment: e.target.value.split(',').map(x => x.trim()).filter(Boolean) })}/></label></>}
          {editing === 'space' && <><p>조용한 1인 독립 공간을 찾아요.</p><RegionSearch label="이용할 지역" value={conditions.region} onChange={region => setConditions({ ...conditions, region })}/></>}
          {editing === 'budget' && <BudgetInput label="총예산" value={conditions.budget} onChange={budget => setConditions({ ...conditions, budget })}/>}
          <Button type="submit">확인</Button><Button type="button" secondary onClick={() => setEditing(null)}>닫기</Button>
        </form>
      </Modal>}
    </section></>;
}
