import type { AgentTask, Candidate, Conditions, View } from './types';

export const compactRegion = (region: string) => region.replace(/특별자치도|특별자치시|특별시|광역시/g, '').trim();
export const budgetLabel = (value: number | null) => value === null ? '예산 확인 필요' : value === 0 ? '무료만' : value / 10000 + '만 원 이내';
export function calendarDays(month: string): string[] {
  if (!/^\d{4}-(?:0[1-9]|1[0-2])$/.test(month)) return [];
  const first = new Date(month + '-01T12:00:00Z');
  first.setUTCDate(1 - first.getUTCDay());
  return Array.from({ length: 42 }, (_, i) => { const day = new Date(first); day.setUTCDate(first.getUTCDate() + i); return day.toISOString().slice(0, 10); });
}
export function daysUntil(date: string, now = Date.now()): number {
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(now);
  return Math.round((Date.parse(date + 'T00:00:00+09:00') - Date.parse(today + 'T00:00:00+09:00')) / 86400000);
}

export function isPublicCandidate(c: Candidate): boolean {
  if (c.provider?.includes('공공')) return true;
  try { return ['eshare.go.kr', 'www.eshare.go.kr', 'yeyak.seoul.go.kr', 'youthjob.hscity.go.kr', 'youthjob.dongjak.go.kr', 'www.ulsannamgu.go.kr', 'commonzfield.kr', 'job.seoul.go.kr'].includes(new URL(c.url).hostname); }
  catch { return false; }
}

export function bookingGroups(tasks: AgentTask[], now = Date.now()) {
  const confirmed = tasks.filter(t => t.state === 'CONFIRMED');
  const ends = (t: AgentTask) => Date.parse(t.conditions.date + 'T' + (t.conditions.end || t.conditions.start || '00:00') + ':00+09:00');
  return {
    scheduled: confirmed.filter(t => ends(t) >= now || !Number.isFinite(ends(t))),
    completed: confirmed.filter(t => ends(t) < now),
    pending: tasks.filter(t => ['RECEIVED', 'PENDING'].includes(t.state)),
    working: tasks.filter(t => !['CONFIRMED', 'RECEIVED', 'PENDING', 'STOPPED', 'FAILED'].includes(t.state)),
  };
}
export function dateLabel(value: string, full = false): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return '날짜 확인 필요';
  const day = new Date(value + 'T12:00:00+09:00');
  if (!Number.isFinite(day.getTime())) return '날짜 확인 필요';
  const weekday = new Intl.DateTimeFormat('ko-KR', { weekday: 'short', timeZone: 'Asia/Seoul' }).format(day);
  const [, month, date] = value.split('-').map(Number);
  return full ? month + '월 ' + date + '일(' + weekday + ')' : month + '/' + date + '(' + weekday + ')';
}
export function conditionError(c: Conditions): string {
  if (!c.purpose.trim()) return '이용 목적을 입력해 주세요.';
  const day = Date.parse(c.date + 'T12:00:00+09:00');
  const clock = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(c.date) || !Number.isFinite(day) || new Date(day).toISOString().slice(0, 10) !== c.date || !clock.test(c.start) || !clock.test(c.end) || c.end <= c.start) return '이용 날짜와 입실·퇴실 시간을 확인해 주세요.';
  if (isPastSlot(c.date, c.start)) return '이미 지난 일정이에요. 이용할 날짜와 시간을 수정해 주세요.';
  if (!c.region.trim()) return '이용할 지역을 입력해 주세요.';
  if (c.budget === null || !Number.isInteger(c.budget) || c.budget < 0 || c.budget > 10000000) return '전체 이용 예산을 입력해 주세요.';
  return '';
}

export function isPastSlot(date: string, start: string, now = Date.now()): boolean {
  const [hour = '', minute = ''] = start.split(':');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !timeValue(hour, minute)) return false;
  const slot = Date.parse(`${date}T${start}:00+09:00`);
  return Number.isFinite(slot) && slot < now;
}

export function wonFromManwon(value: string): number | null {
  if (!/^\d+(?:\.\d{0,4})?$/.test(value)) return null;
  const [whole, fraction = ''] = value.split('.');
  const won = Number(whole) * 10000 + Number(fraction.padEnd(4, '0'));
  return Number.isSafeInteger(won) && won >= 0 && won <= 10000000 ? won : null;
}

export function taskView(task: AgentTask): View {
  if (task.state === 'ANALYZING' || (task.state === 'FAILED' && !task.conditions.purpose)) return 'analyzing';
  if (task.state === 'NEEDS_CONDITIONS') return 'extracted';
  if (['CONFIRMED', 'RECEIVED', 'PENDING'].includes(task.state)) return 'reservations';
  if (['SEARCHING', 'CANDIDATES'].includes(task.state)) return 'results';
  if (task.approval || task.state === 'PREPARING' || task.selectedId) return 'approval';
  return 'extracted';
}
export function timeValue(hour: string, minute: string): string {
  if (!/^\d{1,2}$/.test(hour) || !/^\d{1,2}$/.test(minute) || Number(hour) > 23 || Number(minute) > 59) return '';
  return `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`;
}
export function timeLabel(value: string): string {
  const [hour, minute] = value.split(':');
  if (!timeValue(hour ?? '', minute ?? '')) return '시간 선택';
  return `${Number(hour) < 12 ? '오전' : '오후'} ${Number(hour) % 12 || 12}:${minute}`;
}

export function age(birthDate: string, today = new Date()): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(birthDate)) return null;
  const [y, m, d] = birthDate.split('-').map(Number);
  const date = new Date(y, m - 1, d);
  if (date.getFullYear() !== y || date.getMonth() !== m - 1 || date.getDate() !== d || date > today || y < 1900) return null;
  return today.getFullYear() - y - (today.getMonth() < m - 1 || (today.getMonth() === m - 1 && today.getDate() < d) ? 1 : 0);
}
export const birthInput = (value: string) => value.replace(/\D/g, '').slice(0, 8).replace(/^(\d{4})(\d{1,2})?(\d{1,2})?$/, (_, y, m, d) => y + (m ? '-' + m : '') + (d ? '-' + d : ''));
export const passwordStrength = (value: string) => [value.length >= 10, /[a-zA-Z]/.test(value), /\d/.test(value), /[^a-zA-Z0-9]/.test(value)].filter(Boolean).length;
export const money = (value: number | null) => value === null ? '비용 확인 필요' : value === 0 ? '무료' : `${value.toLocaleString('ko-KR')}원`;
export const safeUrl = (value: string) => { try { const url = new URL(value); return url.protocol === 'https:' && !url.username && !url.password ? url.href : undefined; } catch { return undefined; } };
export const running = (state: string) => ['ANALYZING', 'SEARCHING', 'PREPARING', 'SUBMITTING', 'VERIFYING'].includes(state);
export const stateLabel: Record<string, string> = { ANALYZING: '안내문 분석 중', NEEDS_CONDITIONS: '조건 확인 필요', SEARCHING: '공간을 찾고 있어요', CANDIDATES: '후보 확인', PREPARING: '예약 조건 확인 중', AUTH_REQUIRED: '인증 필요', INPUT_REQUIRED: '추가 확인 필요', APPROVAL_REQUIRED: '최종 승인 대기', SUBMITTING: '신청 제출 중', VERIFYING: '예약 결과 확인 중', RECEIVED: '접수 완료', PENDING: '기관 승인 대기', CONFIRMED: '예약 확정', UNKNOWN: '결과 확인 필요', FAILED: '진행 불가', STOPPED: '작업 중단', HANDOFF: '시설 사이트에서 이어서 예약' };
