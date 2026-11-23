import assert from 'node:assert/strict';
import { createServer } from 'vite';
import React from 'react';
import test from 'node:test';
import { renderToStaticMarkup } from 'react-dom/server';

test('onboarding has two working pages for booking and reservation management', async () => {
  const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  const originalWindow = globalThis.window;
  try {
    const { default: Start, emptyProfile } = await server.ssrLoadModule('/src/pages/Start.tsx');
    const states = []; let cursor = 0; const visits = []; const effects = []; let tick, duration, cleared;
    globalThis.window = { matchMedia: () => ({ matches: false }), setInterval: (callback, delay) => { tick = callback; duration = delay; return 42; }, clearInterval: id => { cleared = id; } };
    React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = {
      useState: initial => { const key = cursor++; if (!(key in states)) states[key] = initial; return [states[key], value => { states[key] = typeof value === 'function' ? value(states[key]) : value; }]; },
      useEffect: effect => { if (!effects.length) effects.push(effect); },
    };
    const render = () => { cursor = 0; return Start({ view: 'intro', profile: emptyProfile, go: view => visits.push(view) }); };
    const find = (node, predicate) => Array.isArray(node) ? node.flatMap(child => find(child, predicate)) : node?.props ? [...(predicate(node) ? [node] : []), ...find(node.props.children, predicate)] : [];
    assert.equal(find(render(), node => node.type === 'button' && 'aria-pressed' in node.props).length, 2);
    const cleanup = effects[0]();
    assert.equal(duration, 2500);
    tick();
    assert.ok(find(render(), node => node.props.className === 'intro-reservations').length);
    tick();
    assert.ok(find(render(), node => node.props.className === 'intro-services').length);
    find(render(), node => node.props.children === '시작하기')[0].props.onClick();
    assert.deepEqual(visits, ['conditions']);
    find(render(), node => node.props['aria-label'] === '공간 예약')[0].props.onClick();
    assert.ok(find(render(), node => node.props.children === '시작하기').length);
    cleanup(); assert.equal(cleared, 42);
  } finally {
    React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = null;
    globalThis.window = originalWindow;
    await server.close();
  }
});

test('startup shows the named splash with an empty status-bar spacer', async () => {
  const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  try {
    const { default: App } = await server.ssrLoadModule('/src/App.tsx');
    const html = renderToStaticMarkup(React.createElement(App));
    assert.match(html, /splash-page/);
    assert.match(html, /<h1>찾았당<\/h1>/);
    assert.match(html, /class="top-spacer" aria-hidden="true"><\/div>/);
    assert.doesNotMatch(html, /9:41|▴|▰|준비를 이어갈게요/);
  } finally { await server.close(); }
});

test('reservations switch between upcoming and past while My only shows account information', async () => {
  const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  try {
    const { default: Account } = await server.ssrLoadModule('/src/pages/Account.tsx');
    const { emptyProfile } = await server.ssrLoadModule('/src/pages/Start.tsx');
    const profile = { ...emptyProfile, name: '테스트', email: 'test@example.com', birthDate: '2000-01-01', region: '부산광역시 사하구' };
    const task = { id: 'next', state: 'CONFIRMED', approval: { name: '다가올 공간' }, conditions: { date: '2099-11-27', start: '13:30', end: '15:30' }, events: [] };
    const tasks = [task, { ...task, id: 'old', approval: { name: '지난 공간' }, conditions: { ...task.conditions, date: '2020-11-20' } }, { ...task, id: 'cancelled', state: 'STOPPED', bookingReference: 'DEMO-1', approval: { name: '취소한 공간' } }, { ...task, id: 'draft', state: 'STOPPED', approval: { name: '중단한 분석' } }];
    const states = []; let cursor = 0;
    React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = {
      useState: initial => { const key = cursor++; if (!(key in states)) states[key] = initial; return [states[key], value => { states[key] = typeof value === 'function' ? value(states[key]) : value; }]; },
      useEffect() {},
    };
    const find = (node, predicate) => Array.isArray(node) ? node.flatMap(child => find(child, predicate)) : node?.props ? [...(predicate(node) ? [node] : []), ...find(node.props.children, predicate)] : [];
    const text = node => Array.isArray(node) ? node.map(text).join(' ') : typeof node === 'string' ? node : node?.props ? text(node.props.children) : '';
    const render = () => { cursor = 0; return Account({ view: 'reservations', loggedIn: true, profile, tasks }); };
    assert.ok(text(render()).includes('다가올 공간'));
    find(render(), node => node.type === 'button' && node.props.children === '지난 예약')[0].props.onClick();
    assert.ok(text(render()).includes('지난 공간'));
    assert.ok(text(render()).includes('취소한 공간'));
    assert.ok(!text(render()).includes('다가올 공간'));
    assert.ok(!text(render()).includes('중단한 분석'));
    React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = null;
    const my = renderToStaticMarkup(React.createElement(Account, { view: 'account', loggedIn: true, profile, tasks }));
    assert.match(my, /계정 정보/); assert.match(my, /보유 장비/); assert.match(my, /개인정보 · 캡처/);
    assert.doesNotMatch(my, /reservation-tabs|profile-stats|예약내역|다가올 공간|지난 공간/);
  } finally {
    React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = null;
    await server.close();
  }
});

test('booking cards and approval show verified conditions without diagnostic toggles', async () => {
  const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  try {
    const { CandidateCard } = await server.ssrLoadModule('/src/components.tsx');
    const { default: Flow } = await server.ssrLoadModule('/src/pages/Flow.tsx');
    const { default: Account } = await server.ssrLoadModule('/src/pages/Account.tsx');
    const candidate = { id: 'room', name: '청년센터 화상면접실', provider: '공공', url: 'https://youthjob.hscity.go.kr/place/place_form.jsp', address: '경기도 화성시', price: 0, equipment: ['웹캠'], eligibility: '신분증 지참', observedAt: '2026-11-22T00:00:00Z' };
    const card = renderToStaticMarkup(React.createElement(CandidateCard, { candidate: { ...candidate, price: 8000 }, booking: { status: 'READY', totalPrice: 8000 } }));
    assert.match(card, /8,000원/); assert.match(card, /이 공간 예약하기/);
    assert.doesNotMatch(card, /예상|미공개|시설 사이트에서|button secondary|disabled/);
    const conditions = { purpose: '화상면접', date: '2099-11-27', start: '12:00', end: '15:00', region: '경기도 화성시', budget: 10000, equipment: ['웹캠'], ownedEquipment: ['노트북'], notes: ['안내문 일정 12:30~14:50. 준비시간을 포함했어요.'] };
    const approval = { candidateId: 'room', name: candidate.name, url: candidate.url, date: conditions.date, start: conditions.start, end: conditions.end, totalPrice: 0, cancellation: '이용 전 취소 가능', fingerprint: 'checked' };
    const task = { id: 'task', state: 'APPROVAL_REQUIRED', conditions, candidates: [candidate], bookingChecks: { room: { status: 'READY', totalPrice: 0 } }, selectedId: 'room', approval, events: [] };
    const props = { task, tasks: [], profile: {}, view: 'approval', go() {} };
    const extracted = renderToStaticMarkup(React.createElement(Flow, { ...props, view: 'extracted' }));
    assert.match(extracted, /안내문 일정 12:30~14:50/); assert.doesNotMatch(extracted, /<details|시간은 직접 수정할 수 있어요/);
    const sheet = renderToStaticMarkup(React.createElement(Flow, props));
    assert.match(sheet, /이 내용으로 예약할까요/); assert.match(sheet, /승인하고 예약하기/); assert.match(sheet, /이용 전 취소 가능/); assert.match(sheet, /신분증 · 노트북/); assert.doesNotMatch(sheet, /<details|시설 기본정보는 DB|가상|예약 조건이 확인된 공간/);
    const reservations = renderToStaticMarkup(React.createElement(Account, { view: 'reservations', loggedIn: true, profile: { name: '테스트', birthDate: '2000-01-01', region: '경기도 화성시' }, tasks: [{ ...task, state: 'CONFIRMED', bookingReference: 'TEST-1234' }, { ...task, id: 'pending', state: 'PENDING' }] }));
    assert.match(reservations, /예약 확정/); assert.match(reservations, /기관 승인 대기/); assert.match(reservations, /TEST-1234/); assert.match(reservations, /무료/);
  } finally { await server.close(); }
});

test('analysis carousel cycles three pages, pauses, cancels and clears its timer', async () => {
  const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  const originalWindow = globalThis.window;
  try {
    const { AnalysisCarousel } = await server.ssrLoadModule('/src/pages/Flow.tsx');
    const states = [], effects = [];
    let cursor = 0, tick, cleared, canceled = 0;
    globalThis.window = {
      matchMedia: () => ({ matches: false, addEventListener() {}, removeEventListener() {} }),
      setInterval: callback => { tick = callback; return 123; },
      clearInterval: timer => { cleared = timer; },
    };
    React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = {
      useState: initial => {
        const key = cursor++;
        if (!(key in states)) states[key] = initial;
        return [states[key], value => { states[key] = typeof value === 'function' ? value(states[key]) : value; }];
      },
      useEffect: effect => { if (effects.length < 2) effects.push(effect); },
    };
    const render = () => { cursor = 0; return AnalysisCarousel({ onCancel: () => canceled++ }); };
    const find = (node, predicate) => Array.isArray(node) ? node.flatMap(child => find(child, predicate)) : node?.props ? [...(predicate(node) ? [node] : []), ...find(node.props.children, predicate)] : [];
    const current = () => find(render(), node => node.type === 'button' && node.props['aria-pressed'] === true)[0].props['aria-label'];
    assert.match(current(), /^1 \/ 3/);
    const disposeMotion = effects[0](), disposeTimer = effects[1]();
    tick(); assert.match(current(), /^2 \/ 3/);
    tick(); assert.match(current(), /^3 \/ 3/);
    tick(); assert.match(current(), /^1 \/ 3/);
    const pages = find(render(), node => node.type === 'button' && 'aria-pressed' in node.props);
    pages[2].props.onClick();
    assert.match(current(), /^3 \/ 3/);
    assert.equal(states[1], true);
    find(render(), node => node.props.className?.includes('analysis-cancel'))[0].props.onClick();
    assert.equal(canceled, 1);
    disposeMotion(); disposeTimer();
    assert.equal(cleared, 123);
  } finally {
    React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = null;
    globalThis.window = originalWindow;
    await server.close();
  }
});

test('every file upload analyzes directly and capture opens only from the camera button', async () => {
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
try {
  const { default: Flow } = await server.ssrLoadModule('/src/pages/Flow.tsx');
  const calls = [];
  const state = [];
  React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = {
    useState: initial => [initial, value => state.push(value)],
    useRef: () => ({ current: null }),
    useEffect: () => {},
  };
  const props = { view: 'home', task: null, tasks: [], profile: {}, loggedIn: true, busy: false,
    go: view => calls.push(['go', view]), perform: action => action(), analyze: (...args) => { calls.push(['analyze', ...args]); return Promise.resolve(); } };
  const tree = Flow(props);
  const inputs = [tree.props.children[0].props.children].flat();
  const file = new File(['image'], 'capture.png', { type: 'image/png' });
  inputs[0].props.onChange({ target: { files: [file], value: 'capture.png' } });
  assert.deepEqual(calls, [['analyze', '', file]]);
  calls.length = 0;
  assert.equal(inputs.length, 1);
  const find = (node, predicate) => Array.isArray(node) ? node.flatMap(child => find(child, predicate)) : node?.props ? [...(predicate(node) ? [node] : []), ...find(node.props.children, predicate)] : [];
  const cameraButton = find(tree, node => node.type === 'button' && node.props.children?.[1] === '촬영하기')[0];
  cameraButton.props.onClick();
  assert.deepEqual(calls, [['go', 'capture']]);
  calls.length = 0;
  const captureTree = Flow({ ...props, view: 'capture' });
  const captureInput = captureTree.props.children[0].props.children;
  captureInput.props.onChange({ target: { files: [file], value: 'capture.png' } });
  assert.deepEqual(calls, [['analyze', '', file]]);
  calls.length = 0;
  inputs[0].props.onChange({ target: { files: [], value: '' } });
  inputs[0].props.onChange({ target: { files: [new File(['bad'], 'bad.txt', { type: 'text/plain' })], value: '' } });
  assert.deepEqual(calls, []);
  assert.ok(state.some(value => typeof value === 'string' && value.includes('8MB')));
  const task = { state: 'SEARCHING', conditions: { purpose: '화상면접', date: '2099-11-27', start: '13:00', end: '14:00', region: '서울 마포구', budget: 10000, equipment: [], notes: [] }, candidates: [], events: [{ message: '클릭 상세 조작 로그' }], message: '조건에 맞는 공간을 찾고 있어요.' };
  const text = node => Array.isArray(node) ? node.map(text).join(' ') : typeof node === 'string' ? node : node?.props ? text(node.props.children) : '';
  const progress = text(Flow({ ...props, view: 'results', task }));
  assert.ok(progress.includes(task.message));
  assert.ok(!progress.includes('상세 조작 로그'));
  assert.ok(!progress.includes('직접 조작하기'));
} finally {
  React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = null;
  await server.close();
}
});

test('region input saves only a selected search result', async () => {
  const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  try {
    const { default: RegionSearch } = await server.ssrLoadModule('/src/RegionSearch.tsx');
    const states = [];
    let cursor = 0;
    const changes = [];
    const props = { value: '', onChange: region => { changes.push(region); props.value = region; } };
    React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = {
      useState: initial => {
        const index = cursor++;
        if (!(index in states)) states[index] = initial;
        return [states[index], value => { states[index] = value; }];
      },
      useId: () => 'region-help',
    };
    const render = () => { cursor = 0; return RegionSearch(props); };
    const input = tree => tree.props.children[0].props.children[1];
    let validity = '';
    let prevented = false, focused = false;
    const target = { value: '양산', setCustomValidity: message => { validity = message; }, focus: () => { focused = true; } };
    const initial = render();
    assert.equal(input(initial).props.placeholder, '지역 검색 (예: 강남구)');
    assert.ok(initial.props.children[2].props.children.some(item => item.props.children.props.children === '서울특별시 강남구'));
    input(initial).props.onChange({ target });
    assert.ok(validity);
    assert.deepEqual(changes, []);
    input(render()).props.onInvalid({ preventDefault: () => { prevented = true; }, currentTarget: target });
    assert.equal(prevented, true);
    assert.equal(focused, true);
    assert.equal(render().props.children[1].props.role, 'alert');
    assert.equal(input(render()).props['aria-invalid'], true);
    const tree = render();
    const options = tree.props.children[2].props.children;
    const option = options.find(item => item.props.children.props.children === '경상남도 양산시');
    option.props.children.props.onClick();
    input(render()).props.ref(target);
    assert.equal(render().props.children[2].type, 'ul');
    assert.equal(validity, '');
    assert.deepEqual(changes, ['경상남도 양산시']);
    assert.equal(render().props.children[1], false);
    assert.equal(input(render()).props['aria-invalid'], false);
    assert.equal(render().props.children[3].props.children[1].type, 'br');
    target.value = '양산동';
    input(render()).props.onChange({ target });
    assert.ok(validity);
    assert.equal(props.value, '경상남도 양산시');
    target.value = '없는지역이름';
    input(render()).props.onChange({ target });
    assert.equal(render().props.children[2].type, 'ul');
    assert.equal(render().props.children[2].props.children.length, 0);
  } finally {
    React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = null;
    await server.close();
  }
});

test('camera opens live video, submits the shutter frame and stops on close', async () => {
  const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  const originalMedia = Object.getOwnPropertyDescriptor(navigator, 'mediaDevices');
  const originalDocument = globalThis.document;
  try {
    const { default: CameraCapture } = await server.ssrLoadModule('/src/CameraCapture.tsx');
    let stopped = 0; let played = 0; let constraints;
    const stream = { getTracks: () => [{ stop: () => { stopped++; } }] };
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: async options => { constraints = options; return stream; } } });
    const video = { srcObject: null, videoWidth: 1920, videoHeight: 1080, play: async () => { played++; } };
    const states = []; const refs = [{ current: video }, { current: false }];
    let stateIndex = 0; let refIndex = 0; let mount;
    React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = {
      useState: initial => { const index = stateIndex++; if (!(index in states)) states[index] = initial; return [states[index], value => { states[index] = value; }]; },
      useRef: () => refs[refIndex++],
      useEffect: effect => { mount ??= effect; },
    };
    let submitted;
    const finished = new Promise(resolve => { submitted = resolve; });
    const render = () => { stateIndex = 0; refIndex = 0; return CameraCapture({ busy: false, onCapture: async file => submitted(file), onGallery: () => {} }); };
    render();
    const close = mount();
    await Promise.resolve(); await Promise.resolve();
    assert.equal(video.srcObject, stream);
    assert.equal(played, 1);
    assert.equal(constraints.audio, false);
    assert.equal(constraints.video.facingMode.ideal, 'environment');
    render().props.children[1].props.children[4].props.onLoadedData();
    const drawCalls = [];
    globalThis.document = { createElement: () => ({ getContext: () => ({ drawImage: (...args) => drawCalls.push(args) }), toBlob: callback => callback(new Blob(['frame'], { type: 'image/jpeg' })) }) };
    const shutter = render().props.children[3].props.children[0].props.children[1];
    assert.equal(shutter.props.disabled, false);
    shutter.props.onClick();
    const file = await finished;
    assert.equal(file.type, 'image/jpeg');
    assert.equal(file.name, 'camera.jpg');
    assert.equal(drawCalls.length, 1);
    close();
    assert.equal(stopped, 1);
  } finally {
    if (originalMedia) Object.defineProperty(navigator, 'mediaDevices', originalMedia); else delete navigator.mediaDevices;
    globalThis.document = originalDocument;
    React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE.H = null;
    await server.close();
  }
});
