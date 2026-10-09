import type { Uuid } from '../api/types';

/**
 * Выделение по клику: Ctrl/Cmd — переключение, Shift — диапазон по видимым
 * строкам от якоря, без модификаторов — одна строка.
 */
export function nextSelection(
  current: Uuid[],
  anchor: Uuid | null,
  clicked: Uuid,
  visible: Uuid[],
  modifiers: { toggle: boolean; range: boolean },
): Uuid[] {
  if (modifiers.range && anchor) {
    const from = visible.indexOf(anchor);
    const to = visible.indexOf(clicked);
    if (from >= 0 && to >= 0) {
      const [a, b] = from <= to ? [from, to] : [to, from];
      return visible.slice(a, b + 1);
    }
  }
  if (modifiers.toggle) {
    return current.includes(clicked) ? current.filter((id) => id !== clicked) : [...current, clicked];
  }
  return [clicked];
}
