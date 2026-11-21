import assert from 'node:assert/strict';
import { createServer } from 'vite';
import React from 'react';
import test from 'node:test';

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
    const target = { value: '양산', setCustomValidity: message => { validity = message; } };
    const initial = render();
    assert.equal(input(initial).props.placeholder, '지역 검색 (예: 강남구)');
    assert.ok(initial.props.children[2].props.children.some(item => item.props.children.props.children === '서울특별시 강남구'));
    input(initial).props.onChange({ target });
    assert.ok(validity);
    assert.deepEqual(changes, []);
    const tree = render();
    const options = tree.props.children[2].props.children;
    const option = options.find(item => item.props.children.props.children === '경상남도 양산시');
    option.props.children.props.onClick();
    input(render()).props.ref(target);
    assert.equal(render().props.children[2].type, 'ul');
    assert.equal(validity, '');
    assert.deepEqual(changes, ['경상남도 양산시']);
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
