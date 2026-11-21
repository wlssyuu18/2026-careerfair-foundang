import assert from 'node:assert/strict';
import test from 'node:test';
import { api, ApiError } from './api.ts';

test('인증 후 CSRF를 갱신하고, 실패한 승인 요청은 자동 재전송하지 않는다', async () => {
  const original = globalThis.fetch;
  const calls: { path: string; method: string; token: string | null }[] = [];
  let token = 0; let rejectApproval = true;
  globalThis.fetch = async (input, init) => {
    const path = String(input); const method = init?.method ?? 'GET';
    calls.push({ path, method, token: new Headers(init?.headers).get('X-CSRF-TOKEN') });
    assert.equal(init?.credentials, 'same-origin');
    if (path === '/api/auth/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: `token-${++token}` });
    if (path === '/api/auth/logout') return new Response(null, { status: 204 });
    if (path.includes('/approve') && rejectApproval) { rejectApproval = false; return Response.json({ message: '승인 토큰 확인 필요' }, { status: 403 }); }
    return Response.json({ id: 'test', email: 'test@example.com' });
  };
  try {
    await api.login('test@example.com', 'test-only-password');
    await api.logout();
    assert.equal(calls[1].token, 'token-1');
    assert.equal(calls[3].token, 'token-2');
    await assert.rejects(api.approve('task-1', 'fingerprint'), e => e instanceof ApiError && e.status === 403);
    assert.equal(calls.filter(c => c.path.includes('/approve')).length, 1);
    await api.approve('task-1', 'fingerprint');
    assert.equal(calls.at(-1)?.token, 'token-4');
    await api.logout();
    globalThis.fetch = async () => { throw new TypeError('offline'); };
    await assert.rejects(api.login('test@example.com', 'test-only-password'), e => e instanceof ApiError && e.status === 0);
  } finally { globalThis.fetch = original; }
});
