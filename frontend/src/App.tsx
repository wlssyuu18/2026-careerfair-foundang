import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';
import { Asset, DeviceBar, Icon, Notice } from './components';
import { running, taskView } from './logic';
import Start, { emptyProfile } from './pages/Start';
import Flow from './pages/Flow';
import Account from './pages/Account';
import type { AgentTask, Conditions, Profile, View } from './types';

const startViews: View[] = ['splash', 'intro', 'conditions', 'signup', 'birth', 'login'];
export default function App() {
  const [view, setView] = useState<View>('intro'); const [profile, setProfile] = useState<Profile>(emptyProfile); const [loggedIn, setLoggedIn] = useState(false); const [ready, setReady] = useState(false);
  const [task, setTask] = useState<AgentTask | null>(null); const [tasks, setTasks] = useState<AgentTask[]>([]); const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const busyRef = useRef(false);
  const go = useCallback((next: View) => { setView(next); setError(''); window.scrollTo({ top: 0 }); }, []);
  const refresh = useCallback(async () => { setTasks(await api.tasks()); }, []);
  useEffect(() => { let alive = true; void api.me().then(user => { if (alive) { setProfile(user); setLoggedIn(true); setView('home'); } }).catch(e => { if (alive && !(e instanceof ApiError && e.status === 401)) setError(e.message); }).finally(() => { if (alive) setReady(true); }); return () => { alive = false; }; }, []);
  useEffect(() => { if (loggedIn && ['reservations', 'account'].includes(view)) void refresh().catch(e => setError(e.message)); }, [view, loggedIn, refresh]);
  useEffect(() => { if (loggedIn) void refresh().catch(e => setError(e.message)); }, [loggedIn, refresh]);
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
  const nav = ['home', 'reservations', 'account'].includes(view);
  return <div className="app-layout"><main className={'app ' + (nav ? 'with-nav ' : '') + (view === 'capture' ? 'camera-app' : '')} aria-busy={busy}>
    <DeviceBar/>
    {error && <div className="global-error"><Notice error>{error}</Notice><button className="icon-button" aria-label="오류 메시지 닫기" onClick={() => setError('')}><Icon name="close" size={18}/></button></div>}
    {!ready ? <div className="loading-screen"><span className="spinner"/><p>준비를 이어갈게요…</p></div> : startViews.includes(view) ? <Start view={view} go={go} profile={profile} setProfile={setProfile} onLogin={onLogin}/> : ['account', 'reservations'].includes(view) ? <Account key={view} view={view} profile={profile} tasks={tasks} loggedIn={loggedIn} go={go} onSave={setProfile} logout={logout} openTask={t => { setTask(t); go(taskView(t)); }} perform={perform}/> : <Flow view={view} go={go} profile={profile} loggedIn={loggedIn} task={task} tasks={tasks} busy={busy} perform={perform} analyze={analyze} search={search} select={select} respond={async (answer, submitted) => { if (task) syncTask(await api.respond(task.id, answer, submitted)); }} approve={async () => { if (task?.approval) syncTask(await api.approve(task.id, task.approval.fingerprint)); }} stop={async () => { if (task) syncTask(await api.cancel(task.id)); }}/>}
    {nav && <nav className="bottom-nav" aria-label="하단 메뉴">{(['home', 'reservations', 'account'] as const).map((v, i) => {
      const selected = view === v || (v === 'account' && view === 'reservations');
      return <button key={v} onClick={() => go(v)} aria-current={(v === 'home' && view === 'home') || (v === 'account' && ['account', 'reservations'].includes(view)) ? 'page' : undefined}><Asset id={i === 0 ? (view === 'home' ? 'eb9ae' : '2027e') : i === 1 ? 'd9bf1' : (selected ? 'e363b' : 'bd67d')}/><span>{['홈', '예약내역', '마이'][i]}</span></button>;
    })}</nav>}
  </main></div>;
}
