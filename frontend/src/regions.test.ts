import assert from 'node:assert/strict';
import test from 'node:test';
import { regionNames, searchRegions } from './regions.ts';

test('지역 검색은 동명이 지역의 전체 명칭을 구분하고 상위 지역으로 좁힌다', () => {
  const matches = searchRegions('양산').slice(0, 30);
  assert.ok(matches.includes('경상남도 양산시'));
  assert.ok(matches.some(name => name.endsWith(' 북구 양산동')));
  assert.ok(matches.includes('경기도 오산시 양산동'));
  assert.equal(searchRegions('광주 북구 양산').length, 1);
  assert.ok(searchRegions('광주 북구 양산')[0].endsWith(' 북구 양산동'));
  assert.deepEqual(searchRegions('   '), []);
  assert.deepEqual(searchRegions('없는지역이름'), []);
  assert.equal(regionNames.includes('양산'), false);
  assert.equal(new Set(regionNames).size, regionNames.length);
});
