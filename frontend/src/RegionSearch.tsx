import { useId, useState } from 'react';
import { regionNames, searchRegions } from './regions';

const suggestedRegions = ['서울특별시 강남구', '서울특별시 마포구', '서울특별시 송파구', '서울특별시 서초구', '서울특별시 영등포구'];

export default function RegionSearch({ value, onChange, label = '거주 지역' }: { value: string; onChange: (region: string) => void; label?: string }) {
  const [query, setQuery] = useState(value);
  const [selected, setSelected] = useState(regionNames.includes(value));
  const [invalid, setInvalid] = useState(false);
  const id = useId();
  const results = selected ? [value] : query.trim() ? searchRegions(query) : suggestedRegions;
  return <div className="region-search">
    <label>{label}<input autoFocus required type="search" value={query} maxLength={100} autoComplete="off"
      placeholder="지역 검색 (예: 강남구)" aria-describedby={id + '-help' + (selected ? '' : ' ' + id)} aria-invalid={invalid}
      ref={input => { input?.setCustomValidity(selected ? '' : '검색 결과에서 정확한 지역을 선택해 주세요.'); }}
      onInvalid={e => { e.preventDefault(); setInvalid(true); e.currentTarget.focus(); }}
      onChange={e => { setQuery(e.target.value); setSelected(false); setInvalid(false); e.target.setCustomValidity('검색 결과에서 정확한 지역을 선택해 주세요.'); }}/></label>
    {!selected && <p id={id} className={'caption region-status' + (invalid ? ' region-error' : '')} role={invalid ? 'alert' : 'status'}>{invalid ? '검색 결과에서 정확한 지역을 선택해 주세요.' : !query.trim() ? '추천 지역 · 원하는 지역을 검색할 수도 있어요.' : !results.length ? '검색 결과가 없어요. 지역명을 다시 확인해 주세요.' : results.length + '곳 · 상위 지역을 확인하고 선택해 주세요.'}</p>}
    <ul className="region-results" aria-label="지역 검색 결과">{results.slice(0, 30).map(region => <li key={region}><button type="button" aria-pressed={selected && region === value} onClick={() => { setQuery(region); setSelected(true); setInvalid(false); onChange(region); }}>{region}</button></li>)}</ul>
    <p id={id + '-help'} className="caption region-status">시·군·구·동으로 검색하고 결과에서 선택해 주세요.<br/>검색 결과는 최대 30곳까지 표시해요.</p>
  </div>;
}
