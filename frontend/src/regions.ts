import { regionNames } from './region-data.ts';

export { regionNames };

export function searchRegions(query: string): string[] {
  const words = query.trim().split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  const last = words.at(-1)!;
  return regionNames.filter(name => words.every(word => name.replaceAll(' ', '').includes(word)))
    .sort((a, b) => Number(b.split(' ').at(-1)!.includes(last)) - Number(a.split(' ').at(-1)!.includes(last)));
}
