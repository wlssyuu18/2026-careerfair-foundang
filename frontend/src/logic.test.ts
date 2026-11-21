import assert from 'node:assert/strict';
import test from 'node:test';
import { age, birthInput, bookingGroups, budgetLabel, compactRegion, conditionError, dateLabel, daysUntil, isPastSlot, isPublicCandidate, passwordStrength, safeUrl, taskView, timeLabel, timeValue, wonFromManwon } from './logic.ts';
import type { AgentTask, Candidate, Conditions } from './types.ts';
import { calendarDays } from './logic.ts';

test('달력은 윤년과 연도 경계에서도 일요일부터 6주를 표시한다', () => {
  const leap = calendarDays('2024-02');
  assert.equal(leap.length, 42);
  assert.equal(leap[0], '2024-01-28');
  assert.ok(leap.includes('2024-02-29'));
  assert.equal(calendarDays('2025-02').includes('2025-02-29'), false);
  assert.equal(calendarDays('2027-01')[0], '2026-12-27');
  assert.deepEqual(calendarDays('2026-13'), []);
});

test('예약은 확정·승인 대기·이용 완료를 구분하고 중단을 완료로 세지 않는다', () => {
  const task = { conditions: { date: '2026-11-27', start: '13:30', end: '15:30' } } as AgentTask;
  const result = bookingGroups([{ ...task, state: 'CONFIRMED' }, { ...task, state: 'PENDING' }, { ...task, state: 'STOPPED' }, { ...task, state: 'SEARCHING' }, { ...task, state: 'CONFIRMED', conditions: { ...task.conditions, date: '2026-11-20' } }], Date.parse('2026-11-21T12:00:00+09:00'));
  assert.equal(result.scheduled.length, 1);
  assert.equal(result.pending.length, 1);
  assert.equal(result.completed.length, 1);
  assert.equal(result.working.length, 1);
  assert.equal(dateLabel('2026-11-27'), '11/27(금)');
  assert.equal(daysUntil('2026-11-27', Date.parse('2026-11-20T18:00:00Z')), 6);
});

test('검색 전 필수 조건을 검증하고 실제 포털 호스트로 후보를 분류한다', () => {
  const value = { purpose: '화상면접', date: '2099-11-27', start: '13:30', end: '15:30', region: '부산 사하구', budget: 10000, equipment: [], ownedEquipment: [], notes: [] } as Conditions;
  assert.equal(conditionError(value), '');
  assert.ok(conditionError({ ...value, budget: null }));
  assert.ok(conditionError({ ...value, start: '16:00' }));
  assert.ok(conditionError({ ...value, start: '24:00' }));
  assert.ok(conditionError({ ...value, date: '2099-02-31' }));
  assert.ok(conditionError({ ...value, region: '' }));
  assert.equal(isPublicCandidate({ url: 'https://www.eshare.go.kr/' } as Candidate), true);
  assert.equal(isPublicCandidate({ url: 'https://yeyak.seoul.go.kr/' } as Candidate), true);
  assert.equal(isPublicCandidate({ url: 'https://www.eshare.go.kr.example.com/' } as Candidate), false);
});

test('안내문 과거 일정은 한국 시간 기준으로 구분한다', () => {
  const now = Date.parse('2026-11-21T18:44:00+09:00');
  assert.equal(isPastSlot('2026-11-21', '12:30', now), true);
  assert.equal(isPastSlot('2026-11-27', '18:00', now), false);
  assert.equal(isPastSlot('2026-11-21', '', now), false);
});

test('만원 입력은 원 단위로 정확하게 변환하고 범위를 검증한다', () => {
  assert.equal(wonFromManwon('1'), 10000);
  assert.equal(wonFromManwon('0'), 0);
  assert.equal(wonFromManwon('0.8'), 8000);
  assert.equal(wonFromManwon('0.0003'), 3);
  assert.equal(wonFromManwon('1.'), 10000);
  assert.equal(wonFromManwon('1000'), 10000000);
  assert.equal(budgetLabel(10000), '1만 원 이내');
  assert.equal(budgetLabel(8000), '0.8만 원 이내');
  assert.equal(budgetLabel(0), '무료만');
  assert.equal(budgetLabel(null), '예산 확인 필요');
  assert.equal(compactRegion('부산광역시 사하구'), '부산 사하구');
  for (const invalid of ['', '-1', '1e3', '1만원', '0.00001', '1001']) assert.equal(wonFromManwon(invalid), null);
});
test('생년월일은 실제 날짜와 생일 경계로 계산한다', () => {
  const today = new Date(2026, 10, 21);
  assert.equal(age('2000-11-22', today), 25);
  assert.equal(age('2000-11-21', today), 26);
  assert.equal(age('2001-02-29', today), null);
  assert.equal(age('2027-01-01', today), null);
  assert.equal(birthInput('20001121'), '2000-11-21');
});
test('분석 중에는 분석 화면, 완료 후에는 내용 확인 화면으로 이동한다', () => {
  const task = { state: 'ANALYZING', conditions: { purpose: '' }, candidates: [] } as unknown as AgentTask;
  assert.equal(taskView(task), 'analyzing');
  assert.equal(taskView({ ...task, state: 'NEEDS_CONDITIONS' }), 'extracted');
  assert.equal(taskView({ ...task, state: 'FAILED' }), 'analyzing');
  assert.equal(taskView({ ...task, state: 'SEARCHING' }), 'results');
});
test('시간 선택은 자정·정오와 분 단위를 보존하고 빈 시간을 만들지 않는다', () => {
  assert.equal(timeLabel('00:00'), '오전 12:00');
  assert.equal(timeLabel('12:00'), '오후 12:00');
  assert.equal(timeLabel('18:07'), '오후 6:07');
  assert.equal(timeValue('6', '7'), '06:07');
  assert.equal(timeValue('', '00'), '');
  assert.equal(timeValue('24', '00'), '');
  assert.equal(timeValue('12', '60'), '');
});
test('외부 링크는 https만 허용하고 자격정보를 거부한다', () => {
  assert.equal(safeUrl('javascript:alert(1)'), undefined);
  assert.equal(safeUrl('https://user:pass@example.com'), undefined);
  assert.equal(safeUrl('https://yeyak.seoul.go.kr/web/main.do'), 'https://yeyak.seoul.go.kr/web/main.do');
  assert.equal(passwordStrength('Abcdef123!'), 4);
});
