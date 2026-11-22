import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';
import { Icon, Notice } from './components';
import { running, taskView } from './logic';
import Start, { emptyProfile } from './pages/Start';
import Flow from './pages/Flow';
import Account from './pages/Account';
import type { AgentTask, Conditions, Profile, View } from './types';

const startViews: View[] = ['splash', 'intro', 'conditions', 'signup', 'birth', 'login'];
export default function App() {
  const [view, setView] = useState<View>('splash'); const [profile, setProfile] = useState<Profile>(emptyProfile); const [loggedIn, setLoggedIn] = useState(false); const [ready, setReady] = useState(false);
  const [splashElapsed, setSplashElapsed] = useState(false);
  useEffect(() => { const timer = setTimeout(() => setSplashElapsed(true), 1800); return () => clearTimeout(timer); }, []);
  useEffect(() => { if (ready && splashElapsed) setView(current => current === 'splash' ? loggedIn ? 'home' : 'intro' : current); }, [ready, splashElapsed, loggedIn]);
  const [task, setTask] = useState<AgentTask | null>(null); const [tasks, setTasks] = useState<AgentTask[]>([]); const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const busyRef = useRef(false);
  const go = useCallback((next: View) => { setView(next); setError(''); window.scrollTo({ top: 0 }); }, []);
  const previousTasks = useRef<AgentTask[]>([]);
  const [notification, setNotification] = useState('');
  const refresh = useCallback(async () => {
    const next = await api.tasks();
    const approved = next.find(t => t.state === 'CONFIRMED' && previousTasks.current.some(old => old.id === t.id && old.state === 'PENDING'));
    if (approved) setNotification((approved.approval?.name || '공간') + ' 예약이 승인됐어요!');
    previousTasks.current = next; setTasks(next);
  }, []);
  useEffect(() => { if (!notification) return; const timer = setTimeout(() => setNotification(''), 7000); return () => clearTimeout(timer); }, [notification]);
  useEffect(() => { let alive = true; void api.me().then(user => { if (alive) { setProfile(user); setLoggedIn(true); } }).catch(e => { if (alive && !(e instanceof ApiError && e.status === 401)) setError(e.message); }).finally(() => { if (alive) setReady(true); }); return () => { alive = false; }; }, []);
  useEffect(() => { if (loggedIn && ['reservations', 'account'].includes(view)) void refresh().catch(e => setError(e.message)); }, [view, loggedIn, refresh]);
  useEffect(() => { if (loggedIn) void refresh().catch(e => setError(e.message)); }, [loggedIn, refresh]);
  useEffect(() => {
    if (!loggedIn || !tasks.some(t => t.state === 'PENDING')) return;
    const timer = window.setInterval(() => void refresh().catch(e => setError(e.message)), 2000);
    return () => window.clearInterval(timer);
  }, [loggedIn, tasks, refresh]);
  const syncTask = useCallback((next: AgentTask) => {
    setTask(next);
    if (next.state === 'NEEDS_CONDITIONS') go(taskView(next));
    else if (next.state === 'CANDIDATES') go('results');
    else if (next.state === 'APPROVAL_REQUIRED') go('approval');
    else if (['CONFIRMED', 'RECEIVED', 'PENDING'].includes(next.state)) { go('reservations'); void refresh().catch(e => setError(e.message)); }
  }, [go, refresh]);
  useEffect(() => {
    if (!task || !running(task.state)) return;
    let alive = true; let timer: ReturnType<typeof setTimeout>;
    const poll = async () => { try { const next = await api.task(task.id); if (!alive) return; syncTask(next); if (running(next.state)) timer = setTimeout(poll, 1800); } catch (e) { if (!alive) return; setError((e as Error).message); timer = setTimeout(poll, 5000); } };
    timer = setTimeout(poll, 1200); return () => { alive = false; clearTimeout(timer); };
  }, [task?.id, task?.state, syncTask]);
  async function perform(action: () => Promise<unknown>) { if (busyRef.current) return; busyRef.current = true; setBusy(true); setError(''); try { await action(); } catch (e) { setError((e as Error).message); } finally { busyRef.current = false; setBusy(false); } }
  function onLogin(user: Profile) { setProfile(user); setLoggedIn(true); go('home'); }
  async function logout() { await api.logout(); setLoggedIn(false); setProfile(emptyProfile); setTask(null); setTasks([]); go('login'); }
  async function analyze(text: string, file?: File) {
    const previous = task;
    setTask(null); go('analyzing');
    try { const next = await api.analyze(text, file); syncTask(next); }
    catch (e) { setTask(previous); go('home'); throw e; }
  }
  async function search(conditions: Conditions) { if (task) { syncTask(await api.search(task.id, conditions)); go('results'); } }
  async function select(candidateId: string) { if (task) { syncTask(await api.select(task.id, candidateId)); go('approval'); } }
  async function browse() {
    if (!loggedIn) { go('login'); return; }
    const date = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(Date.now() + 86400000);
    setTask(await api.draft({ purpose: '화상면접', date, start: '13:30', end: '15:30', region: profile.region || '부산광역시 사하구', budget: profile.budget, equipment: ['웹캠', '마이크', '인터넷'], ownedEquipment: profile.equipment, notes: [] })); go('extracted');
  }
  const nav = ['home', 'reservations', 'account'].includes(view);
  return <div className="app-layout"><main className={'app ' + (nav ? 'with-nav ' : '') + (view === 'capture' ? 'camera-app' : '')} aria-busy={busy}>
    <div className="top-spacer" aria-hidden="true"/>
    {notification && <button type="button" className="approval-toast" role="status" onClick={() => { go('reservations'); setNotification(''); }}><Icon name="check" size={18}/>{notification}</button>}
    {error && <div className="global-error"><Notice error>{error}</Notice><button className="icon-button" aria-label="오류 메시지 닫기" onClick={() => setError('')}><Icon name="close" size={18}/></button></div>}
    {!ready && view !== 'splash' ? <div className="loading-screen"><span className="spinner"/><p>준비를 이어갈게요…</p></div> : startViews.includes(view) ? <Start view={view} go={go} profile={profile} setProfile={setProfile} onLogin={onLogin}/> : ['account', 'reservations'].includes(view) ? <Account cancelBooking={t => perform(async () => { await api.cancel(t.id); await refresh(); })} key={view} view={view} profile={profile} tasks={tasks} loggedIn={loggedIn} go={go} onSave={setProfile} logout={logout} openTask={t => { setTask(t); go(taskView(t)); }} perform={perform}/> : <Flow browse={() => perform(browse)} view={view} go={go} profile={profile} loggedIn={loggedIn} task={task} tasks={tasks} busy={busy} perform={perform} analyze={analyze} search={search} select={select} respond={async (answer, submitted) => { if (task) syncTask(await api.respond(task.id, answer, submitted)); }} approve={async () => { if (task?.approval) syncTask(await api.approve(task.id, task.approval.fingerprint)); }} stop={async () => { if (task) syncTask(await api.cancel(task.id)); }}/>}
    {nav && <nav className="bottom-nav" aria-label="하단 메뉴">{(['home', 'reservations', 'account'] as const).map((v, i) => {
      return <button key={v} onClick={() => go(v)} aria-label={['홈', '예약내역', '마이'][i]} aria-current={view === v ? 'page' : undefined}><Icon name={['home', 'calendar', 'user'][i]} size={22}/></button>;
    })}</nav>}
  </main></div>;
}
