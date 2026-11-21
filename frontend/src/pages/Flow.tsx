import DatePicker from '../DatePicker';
import CameraCapture from '../CameraCapture';
import RegionSearch from '../RegionSearch';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Asset, BudgetInput, Button, CandidateCard, Icon, InfoRow, Modal, Notice, Tag, TimePicker, Top } from '../components';
import { bookingGroups, budgetLabel, compactRegion, conditionError, dateLabel, isPastSlot, isPublicCandidate, money, running, safeUrl, stateLabel, taskView, timeLabel } from '../logic';
import type { AgentTask, Conditions, Profile, View } from '../types';

type Props = { view: View; go: (v: View) => void; profile: Profile; loggedIn: boolean; task: AgentTask | null; tasks: AgentTask[]; busy: boolean; perform: (action: () => Promise<unknown>) => Promise<void>; analyze: (text: string, file?: File) => Promise<void>; search: (conditions: Conditions) => Promise<void>; select: (id: string) => Promise<void>; respond: (answer: string, submitted?: boolean) => Promise<void>; approve: () => Promise<void>; stop: () => Promise<void> };
type Edit = 'schedule' | 'purpose' | 'equipment' | 'space' | 'budget';
export default function Flow(p: Props) {
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
  const candidates = task?.state === 'SEARCHING' ? [] : task?.candidates ?? [];
  const visible = candidates.filter(c => category === '전체' || (category === '공공' ? isPublicCandidate(c) : !isPublicCandidate(c)));
  const imageInputs = <><input ref={gallery} className="sr-only" type="file" accept="image/png,image/jpeg" onChange={e => { void chooseFile(e.target.files?.[0]); e.target.value = ''; }}/></>;
  const resultList = <>
    {task?.conditions && <section className="search-summary"><div className="row spread"><strong>검색 조건</strong><button type="button" className="text-link" disabled={active} onClick={() => p.go('extracted')}>수정</button></div><div className="chips small"><span>{dateLabel(task.conditions.date)} {task.conditions.start}–{task.conditions.end}</span><span>{task.conditions.region}</span><span>{budgetLabel(task.conditions.budget)}</span>{task.conditions.equipment.map(item => <span key={item}>{item}</span>)}</div></section>}
    <div className="result-tabs" aria-label="공간 구분">{['전체', '공공', '민간'].map(value => <button key={value} type="button" aria-pressed={category === value} onClick={() => setCategory(value)}>{value} {value === '전체' ? candidates.length : candidates.filter(c => value === '공공' ? isPublicCandidate(c) : !isPublicCandidate(c)).length}</button>)}</div>
    {candidates.length > 0 && <p className="observed-at"><Asset id="ed98e" size={13}/>{new Date(candidates[0].observedAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })} 기준 사이트 정보를 확인했어요</p>}
    <div className="results-list">{visible.map(c => <CandidateCard key={c.id} candidate={c} recommended={c.id === candidates[0]?.id} onSelect={() => void p.perform(() => p.select(c.id))}/>)}</div>
    {!visible.length && !active && <Notice>확인된 후보가 아직 없어요. 조건을 바꿔 다시 찾아보세요.</Notice>}
  </>;
  return <>{imageInputs}
    {p.view === 'capture' ? <header className="page-top camera-header"><button type="button" className="icon-button" aria-label="촬영 닫기" onClick={() => p.go('home')}><Icon name="close"/></button><strong>면접 안내문 촬영</strong><span className="header-space"/></header> : <Top title={p.view === 'home' ? '홈' : p.view === 'results' || p.view === 'approval' ? task?.state === 'SEARCHING' ? '공간 검색 중' : '추천 공간 ' + candidates.length + '곳' : p.view === 'analyzing' ? '안내문 분석' : '추출 결과 확인'} brand={p.view === 'home'} back={p.view === 'home' ? undefined : () => p.go('home')} right={p.view === 'home' ? <button type="button" className="icon-button" aria-label="예약 알림 확인" onClick={() => p.go('reservations')}><Asset id="fa917"/></button> : undefined}/>}
    <section className={'page flow ' + p.view + '-page'}>
      {fileError && <Notice error>{fileError}</Notice>}
      {p.view === 'home' && <>
        <section className="home-upload"><h1>{p.profile.name ? p.profile.name + '님,' : '안녕하세요,'}<br/>면접 안내문을 올리면<br/><em>면접 볼 공간</em>을 찾아드려요!</h1><div className="home-dropzone">
          <button type="button" className="home-plus" aria-label="안내문 이미지 선택" onClick={() => gallery.current?.click()}><Asset id="47ead" size={26}/></button><strong>면접 안내 캡처 올리기</strong><p>메일·문자 캡처를 올리면 시간·장비 조건을 자동으로 읽어요</p><div className="upload-actions"><button type="button" onClick={() => { setFileError(''); p.go('capture'); }}><Asset id="fb54d" size={16}/>촬영하기</button><button type="button" onClick={() => gallery.current?.click()}><Asset id="23c16" size={16}/>앨범에서 선택</button></div>
        </div></section>
        <div className="request-divider"><span/>또는 말로 요청하기<span/></div>
        <form className="home-search" onSubmit={e => void submit(e)}><Asset id="21029" size={20}/><label className="sr-only" htmlFor="request">AI에게 요청하기</label><textarea id="request" value={text} maxLength={6000} onChange={e => setText(e.target.value)} placeholder="금요일 2시 화상면접, 집 근처 1만 원 이내" rows={text.length > 35 ? 3 : 1}/><button type="submit" className="send" aria-label="AI 분석 시작" disabled={p.busy || active || !text.trim()}><Asset id="4d9ac" size={18}/></button></form>
        <div className="home-categories"><span className="selected"><Asset id="c4800" size={14}/>화상면접 공간</span><span aria-disabled="true" title="준비 중"><Asset id="f8733" size={14}/>면접 정장</span><span aria-disabled="true" title="준비 중"><Asset id="31e79" size={14}/>촬영 공간</span></div>
        {p.profile.notifications && upcoming.length > 0 && <button type="button" className="booking-reminder" onClick={() => p.go('reservations')}><Asset id="6fe04" size={18}/><span>다가오는 예약 {upcoming.length}건<strong>{dateLabel(upcoming[0].conditions.date)} {upcoming[0].conditions.start} · {upcoming[0].approval?.name || upcoming[0].conditions.purpose}</strong></span><Asset id="67a56" size={18}/></button>}
        {task && !['CONFIRMED', 'STOPPED'].includes(task.state) && <button className="text-button" type="button" onClick={() => p.go(taskView(task))}>진행 중인 준비 이어보기</button>}
      </>}
      {p.view === 'capture' && <CameraCapture busy={p.busy || active} onCapture={chooseFile} onGallery={() => gallery.current?.click()}/>}
      {p.view === 'analyzing' && <section className="analysis-screen" aria-live="polite" aria-busy={p.busy || task?.state === 'ANALYZING'}><span className="analysis-symbol"><Icon name="spark" size={42}/></span><h1>{task?.state === 'FAILED' ? '안내문을 읽지 못했어요' : task?.state === 'STOPPED' ? '분석을 중단했어요' : '안내문을 읽고 있어요'}</h1><p>목적 · 날짜와 시간 · 필요한 장비를 정리해요.</p><p className="caption">{task?.message || '입력하신 내용을 확인하고 있어요.'}</p>{(!task || task.state === 'ANALYZING') && <><span className="spinner"/><p className="caption">분석이 끝나면 내용 확인 화면으로 이동해요.</p></>}{task?.state === 'ANALYZING' && <button className="text-button" type="button" onClick={() => void p.perform(async () => { await p.stop(); p.go('home'); })}>분석 중단</button>}{task && ['FAILED', 'STOPPED'].includes(task.state) && <Button secondary onClick={() => p.go('home')}>다시 입력하기</Button>}</section>}
      {p.view === 'extracted' && conditions && <form className="extracted-form" onSubmit={search}>
        <div className="extraction-intro"><div className="document-symbol" aria-hidden="true"><i/><i/><i/><i/></div><div><Tag>AI 추출 완료</Tag><h1>안내문에서 이용 정보를 읽었어요</h1><p>틀린 내용은 눌러서 바로 고칠 수 있어요</p></div></div>
        <h2 className="group-title">{conditions.purpose.includes('면접') ? '면접 정보' : '이용 정보'}</h2><section className="info-group">
          <InfoRow icon="clock" label="이용 일시" value={conditions.date ? dateLabel(conditions.date, true) + ' ' + timeLabel(conditions.start) : '입력이 필요해요'} badge={conditions.date && conditions.start ? 'AI 추출' : '확인 필요'} danger={!conditions.date || !conditions.start} onClick={() => setEditing('schedule')}/>
          <InfoRow icon="monitor" label="이용 목적" value={conditions.purpose || '입력이 필요해요'} badge={conditions.purpose ? 'AI 추출' : '확인 필요'} onClick={() => setEditing('purpose')}/>
          <InfoRow icon="monitor" label="필수 장비" value={conditions.equipment.join(' · ') || '필요한 장비를 확인해 주세요'} badge="확인·수정" onClick={() => setEditing('equipment')}/>
        </section>
        <h2 className="group-title with-icon"><Icon name="spark" size={18}/>AI가 제안한 예약 조건</h2><section className="info-group suggested">
          <InfoRow icon="clock" label="예약 시간" value={conditions.start && conditions.end ? conditions.start + ' ~ ' + conditions.end : '입실·퇴실 시간을 확인해 주세요'} onClick={() => setEditing('schedule')} danger={!conditions.start || !conditions.end}/>
          <InfoRow icon="room" label="공간 조건" value={'1인 독립 공간 · ' + (compactRegion(conditions.region).replace(/^(서울|부산|대구|인천|광주|대전|울산|세종)\s+/, '') || '지역 확인 필요')} badge={(conditions.ownedEquipment ?? []).includes('노트북') ? '노트북 보유' : undefined} onClick={() => setEditing('space')}/>
          <InfoRow icon="bag" label="예산" value={conditions.budget === null ? '입력이 필요해요' : budgetLabel(conditions.budget)} badge={conditions.budget === null ? '확인 필요' : undefined} danger={conditions.budget === null} onClick={() => setEditing('budget')}/>
        </section>
        <p className="privacy-note"><Icon name="shield" size={14}/>기업명·접속 링크는 저장하지 않고 자동으로 가렸어요</p>
        {isPastSlot(conditions.date, conditions.start) && <Notice error>안내문의 일정은 이미 지났어요. 이용할 날짜와 시간을 수정해 주세요.</Notice>}
        {error && <Notice error>{error}</Notice>}
        {conditions.notes.length > 0 && <details className="extraction-notes"><summary>추가 확인 사항</summary>{conditions.notes.map((note, i) => <p key={i}>{note}</p>)}</details>}
        <footer className="extraction-footer"><p>추출 내용이 안내문과 같은지 확인해 주세요</p><div><Button type="button" secondary onClick={() => { setFileError(''); p.go('capture'); }}>다시 촬영</Button><Button className="dark" type="submit" disabled={p.busy || active}>이 조건으로 공간 찾기</Button></div></footer>
      </form>}
      {(p.view === 'results' || p.view === 'approval') && resultList}
      {p.view === 'approval' && task?.approval && <Modal titleId="approval-title" className="approval-sheet" onClose={() => p.go('results')}>
        <span className="sheet-handle"/><h2 id="approval-title">이 내용으로 예약할까요?</h2><p className="sheet-subtitle">승인하기 전에는 실제 예약이 진행되지 않아요.</p>
        <dl className="approval-details"><dt>공간</dt><dd>{task.approval.name}</dd><dt>일시</dt><dd>{dateLabel(task.approval.date)} {task.approval.start}–{task.approval.end}</dd><dt>주소</dt><dd>{candidates.find(c => c.id === task.selectedId)?.address || '주소 확인 필요'}</dd><dt>비용</dt><dd className="green-text">{money(task.approval.totalPrice)}</dd><dt>준비물</dt><dd>시설 안내 확인 필요</dd><dt>취소</dt><dd>{task.approval.cancellation}</dd></dl>
        <details className="approval-verification"><summary className="approval-check"><Icon name="check" size={17}/>예약 일시·총금액·취소 조건을 확인해 주세요</summary><p>{task.approval.evidence}</p>{safeUrl(task.approval.url) && <a href={safeUrl(task.approval.url)} target="_blank" rel="noreferrer">예약 원문 확인</a>}</details>
        <div className="notice yellow"><Icon name="shield" size={16}/><span>예약에 필요한 정보는 시설에 전달돼요.<br/>결제·변경·취소는 다시 확인받은 뒤 진행해요.</span></div>
        <div className="approval-actions"><Button secondary onClick={() => p.go('results')}>취소</Button><Button disabled={active || p.busy || task.state !== 'APPROVAL_REQUIRED'} onClick={() => void p.perform(p.approve)}>{p.busy || active ? '예약 진행 중…' : '승인하고 예약하기'}</Button></div>
      </Modal>}
    {p.view !== 'analyzing' && p.view !== 'capture' && task && (active || needResponse || ['FAILED', 'HANDOFF', 'AUTH_REQUIRED', 'UNKNOWN', 'STOPPED'].includes(task.state)) && <section className="agent-panel" aria-live="polite">
      <div className="row"><span className={active ? 'spinner' : 'agent-symbol'}>{!active && <Icon name="spark" size={21}/>}</span><h2>{stateLabel[task.state] ?? task.state}</h2></div>
      <p>{task.message}</p>
      {needResponse && <form onSubmit={e => { e.preventDefault(); void p.perform(async () => { await p.respond(answer); setAnswer(''); }); }}><Notice>{task.question || '예약에 필요한 추가 정보를 알려주세요.'}</Notice><label>추가 정보<input required value={answer} onChange={e => setAnswer(e.target.value)} maxLength={2000}/></label><Button disabled={p.busy}>확인하고 이어가기</Button></form>}
      {['HANDOFF', 'AUTH_REQUIRED'].includes(task.state) && <Button secondary onClick={() => p.go('results')}>다른 공간 선택하기</Button>}
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
