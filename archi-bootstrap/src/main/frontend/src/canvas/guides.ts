import type { Rect } from './routing';

export interface GuideLine {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

export interface Guides {
  dx: number;
  dy: number;
  lines: GuideLine[];
}

/**
 * Направляющие при перетаскивании: край или середина узла в пределах `threshold`
 * от края или середины другого узла. Возвращает сдвиг, который совмещает их точно,
 * и линии для показа — в координатах холста.
 */
export function alignmentGuides(moving: Rect, others: Rect[], threshold: number): Guides {
  const xs = (r: Rect) => [r.x, r.x + r.width / 2, r.x + r.width];
  const ys = (r: Rect) => [r.y, r.y + r.height / 2, r.y + r.height];
  let best: { dx?: { d: number; at: number; other: Rect }; dy?: { d: number; at: number; other: Rect } } = {};
  for (const other of others) {
    for (const a of xs(moving)) {
      for (const b of xs(other)) {
        const d = b - a;
        if (Math.abs(d) <= threshold && (!best.dx || Math.abs(d) < Math.abs(best.dx.d))) best = { ...best, dx: { d, at: b, other } };
      }
    }
    for (const a of ys(moving)) {
      for (const b of ys(other)) {
        const d = b - a;
        if (Math.abs(d) <= threshold && (!best.dy || Math.abs(d) < Math.abs(best.dy.d))) best = { ...best, dy: { d, at: b, other } };
      }
    }
  }
  const lines: GuideLine[] = [];
  const dx = best.dx?.d ?? 0;
  const dy = best.dy?.d ?? 0;
  if (best.dx) {
    const top = Math.min(moving.y + dy, best.dx.other.y);
    const bottom = Math.max(moving.y + dy + moving.height, best.dx.other.y + best.dx.other.height);
    lines.push({ x1: best.dx.at, y1: top, x2: best.dx.at, y2: bottom });
  }
  if (best.dy) {
    const left = Math.min(moving.x + dx, best.dy.other.x);
    const right = Math.max(moving.x + dx + moving.width, best.dy.other.x + best.dy.other.width);
    lines.push({ x1: left, y1: best.dy.at, x2: right, y2: best.dy.at });
  }
  return { dx, dy, lines };
}
