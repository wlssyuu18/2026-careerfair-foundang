import type { AgentTask, Conditions, Profile, SystemStatus } from './types';

let csrf: { token: string; headerName: string } | undefined;
export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) { super(message); this.status = status; }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = init.method ?? 'GET';
  if (!['GET', 'HEAD'].includes(method) && !csrf) {
    let response: Response;
    try { response = await fetch('/api/auth/csrf', { credentials: 'same-origin' }); }
    catch { throw new ApiError('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.', 0); }
    if (!response.ok) throw new ApiError('서버에 연결할 수 없습니다.', response.status);
    csrf = await response.json();
  }
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (csrf && !['GET', 'HEAD'].includes(method)) headers.set(csrf.headerName, csrf.token);
  let response: Response;
  try { response = await fetch(`/api${path}`, { ...init, headers, credentials: 'same-origin' }); }
  catch { throw new ApiError('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.', 0); }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    if (response.status === 403) csrf = undefined;
    throw new ApiError(body.message || '요청을 처리하지 못했습니다.', response.status);
  }
  if (path === '/auth/login' || path === '/auth/logout') csrf = undefined;
  return response.status === 204 ? undefined as T : response.json();
}
const post = <T>(path: string, body: unknown = {}) => request<T>(path, { method: 'POST', body: JSON.stringify(body) });
export const api = {
  me: () => request<Profile>('/account'),
  signup: (body: unknown) => post<Profile>('/auth/signup', body),
  login: (email: string, password: string) => post<Profile>('/auth/login', { email, password }),
  logout: () => post<void>('/auth/logout'),
  saveProfile: (profile: Profile) => request<Profile>('/account', { method: 'PUT', body: JSON.stringify(profile) }),
  status: () => request<SystemStatus>('/system'),
  tasks: () => request<AgentTask[]>('/tasks'),
  task: (id: string) => request<AgentTask>(`/tasks/${id}`),
  analyze: (text: string, file?: File) => {
    const body = new FormData(); body.set('text', text); if (file) body.set('image', file);
    return request<AgentTask>('/tasks', { method: 'POST', body });
  },
  search: (id: string, conditions: Conditions) => post<AgentTask>(`/tasks/${id}/search`, conditions),
  select: (id: string, candidateId: string) => post<AgentTask>(`/tasks/${id}/select`, { candidateId }),
  respond: (id: string, answer: string, submitted = false) => post<AgentTask>(`/tasks/${id}/respond`, { answer, submitted }),
  approve: (id: string, fingerprint: string) => post<AgentTask>(`/tasks/${id}/approve`, { fingerprint }),
  cancel: (id: string) => post<AgentTask>(`/tasks/${id}/stop`),
};
