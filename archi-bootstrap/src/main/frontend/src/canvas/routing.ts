import type { Bendpoint } from '../api/types';

export interface Point {
  x: number;
  y: number;
}

export interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

const center = (r: Rect): Point => ({ x: r.x + r.width / 2, y: r.y + r.height / 2 });

/**
 * Абсолютные точки перегиба. Archi хранит каждую двумя смещениями — от центра
 * источника и от центра цели — и смешивает их с весом (i + 1) / (n + 1):
 * поэтому перегибы едут вместе с концами, когда узлы двигают.
 */
export function absoluteBendpoints(source: Rect, target: Rect, bendpoints: Bendpoint[]): Point[] {
  const sc = center(source);
  const tc = center(target);
  const n = bendpoints.length;
  return bendpoints.map((b, i) => {
    const w = (i + 1) / (n + 1);
    return {
      x: (sc.x + b.startX) * (1 - w) + (tc.x + b.endX) * w,
      y: (sc.y + b.startY) * (1 - w) + (tc.y + b.endY) * w,
    };
  });
}

/** Обратное преобразование: абсолютная точка → смещения от центров концов, как пишет Archi. */
export function relativeBendpoints(source: Rect, target: Rect, points: Point[]): Bendpoint[] {
  const sc = center(source);
  const tc = center(target);
  return points.map((p) => ({
    startX: Math.round(p.x - sc.x),
    startY: Math.round(p.y - sc.y),
    endX: Math.round(p.x - tc.x),
    endY: Math.round(p.y - tc.y),
  }));
}

/** Точка на границе прямоугольника по лучу из его центра к `toward` — якорь «chopbox», как у Archi. */
export function chopbox(rect: Rect, toward: Point): Point {
  const c = center(rect);
  const dx = toward.x - c.x;
  const dy = toward.y - c.y;
  if (dx === 0 && dy === 0) return c;
  const sx = dx !== 0 ? rect.width / 2 / Math.abs(dx) : Infinity;
  const sy = dy !== 0 ? rect.height / 2 / Math.abs(dy) : Infinity;
  const s = Math.min(sx, sy);
  return { x: c.x + dx * s, y: c.y + dy * s };
}

/**
 * Маршрут связи. С точками перегиба — через них, как нарисовал архитектор.
 * Без них — ортогонально: прямой отрезок, если концы перекрываются по одной
 * из осей, иначе ступенька с изломом посередине.
 */
export function route(source: Rect, target: Rect, bendpoints: Bendpoint[]): Point[] {
  if (bendpoints.length > 0) {
    const middle = absoluteBendpoints(source, target, bendpoints);
    return [chopbox(source, middle[0]!), ...middle, chopbox(target, middle[middle.length - 1]!)];
  }
  if (source === target || sameRect(source, target)) {
    return selfLoop(source);
  }
  const left = Math.max(source.x, target.x);
  const right = Math.min(source.x + source.width, target.x + target.width);
  const top = Math.max(source.y, target.y);
  const bottom = Math.min(source.y + source.height, target.y + target.height);
  const sc = center(source);
  const tc = center(target);

  if (right - left > 0 && !(bottom - top > 0)) {
    const x = (left + right) / 2;
    const down = tc.y > sc.y;
    return [
      { x, y: down ? source.y + source.height : source.y },
      { x, y: down ? target.y : target.y + target.height },
    ];
  }
  if (bottom - top > 0 && !(right - left > 0)) {
    const y = (top + bottom) / 2;
    const rightward = tc.x > sc.x;
    return [
      { x: rightward ? source.x + source.width : source.x, y },
      { x: rightward ? target.x : target.x + target.width, y },
    ];
  }
  if (right - left > 0 && bottom - top > 0) {
    // Вложенные или наложенные фигуры: ортогональный маршрут не существует.
    return [chopbox(source, tc), chopbox(target, sc)];
  }
  const rightward = tc.x > sc.x;
  const start = { x: rightward ? source.x + source.width : source.x, y: sc.y };
  const end = { x: rightward ? target.x : target.x + target.width, y: tc.y };
  const midX = (start.x + end.x) / 2;
  return [start, { x: midX, y: start.y }, { x: midX, y: end.y }, end];
}

function sameRect(a: Rect, b: Rect): boolean {
  return a.x === b.x && a.y === b.y && a.width === b.width && a.height === b.height;
}

function selfLoop(rect: Rect): Point[] {
  const x = rect.x + rect.width;
  const y = rect.y + rect.height / 3;
  return [
    { x, y },
    { x: x + 24, y },
    { x: x + 24, y: y + rect.height / 3 },
    { x, y: y + rect.height / 3 },
  ];
}

/** Путь SVG через точки со скруглёнными изломами. */
export function roundedPath(points: Point[], radius: number): string {
  if (points.length === 0) return '';
  let d = `M${points[0]!.x} ${points[0]!.y}`;
  for (let i = 1; i < points.length - 1; i++) {
    const prev = points[i - 1]!;
    const at = points[i]!;
    const next = points[i + 1]!;
    const inLen = Math.hypot(at.x - prev.x, at.y - prev.y);
    const outLen = Math.hypot(next.x - at.x, next.y - at.y);
    const r = Math.min(radius, inLen / 2, outLen / 2);
    if (r <= 0) {
      d += ` L${at.x} ${at.y}`;
      continue;
    }
    const a = { x: at.x - ((at.x - prev.x) / inLen) * r, y: at.y - ((at.y - prev.y) / inLen) * r };
    const b = { x: at.x + ((next.x - at.x) / outLen) * r, y: at.y + ((next.y - at.y) / outLen) * r };
    d += ` L${a.x} ${a.y} Q${at.x} ${at.y} ${b.x} ${b.y}`;
  }
  const last = points[points.length - 1]!;
  return `${d} L${last.x} ${last.y}`;
}

/** Середина ломаной по длине — место подписи связи и точка, куда цепляются связи со связью. */
export function midpoint(points: Point[]): Point {
  if (points.length === 1) return points[0]!;
  const lengths = points.slice(1).map((p, i) => Math.hypot(p.x - points[i]!.x, p.y - points[i]!.y));
  let rest = lengths.reduce((a, b) => a + b, 0) / 2;
  for (let i = 0; i < lengths.length; i++) {
    const len = lengths[i]!;
    if (rest <= len && len > 0) {
      const t = rest / len;
      const a = points[i]!;
      const b = points[i + 1]!;
      return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t };
    }
    rest -= len;
  }
  return points[points.length - 1]!;
}
