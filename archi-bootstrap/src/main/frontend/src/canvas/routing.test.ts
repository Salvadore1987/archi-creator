import { describe, expect, it } from 'vitest';
import { absoluteBendpoints, chopbox, midpoint, relativeBendpoints, roundedPath, route } from './routing';

const a = { x: 0, y: 0, width: 100, height: 50 };

describe('связи: геометрия по правилам Archi', () => {
  it('точка перегиба смешивает смещения от центров с весом (i+1)/(n+1)', () => {
    const b = { x: 300, y: 0, width: 100, height: 50 };
    const [p] = absoluteBendpoints(a, b, [{ startX: 100, startY: 100, endX: -100, endY: 100 }]);
    expect(p).toEqual({ x: 200, y: 125 });
  });

  it('относительные перегибы обратимы', () => {
    const b = { x: 300, y: 200, width: 100, height: 50 };
    const rel = relativeBendpoints(a, b, [{ x: 120, y: 260 }]);
    expect(absoluteBendpoints(a, b, rel)[0]).toEqual({ x: 120, y: 260 });
  });

  it('якорь — на границе по лучу из центра', () => {
    expect(chopbox(a, { x: 500, y: 25 })).toEqual({ x: 100, y: 25 });
    expect(chopbox(a, { x: 50, y: -300 })).toEqual({ x: 50, y: 0 });
  });

  it('перекрытие по горизонтали — вертикальный прямой отрезок', () => {
    const below = { x: 50, y: 200, width: 100, height: 50 };
    expect(route(a, below, [])).toEqual([
      { x: 75, y: 50 },
      { x: 75, y: 200 },
    ]);
  });

  it('без перекрытия — ступенька с изломом посередине', () => {
    const far = { x: 300, y: 200, width: 100, height: 50 };
    const points = route(a, far, []);
    expect(points).toHaveLength(4);
    expect(points[1]!.x).toBe(points[2]!.x);
    expect(points[0]!.y).toBe(points[1]!.y);
  });

  it('изломы скругляются', () => {
    const d = roundedPath(
      [
        { x: 0, y: 0 },
        { x: 100, y: 0 },
        { x: 100, y: 100 },
      ],
      6,
    );
    expect(d).toContain('Q100 0 100 6');
  });

  it('середина ломаной — по длине', () => {
    expect(
      midpoint([
        { x: 0, y: 0 },
        { x: 100, y: 0 },
        { x: 100, y: 100 },
      ]),
    ).toEqual({ x: 100, y: 0 });
  });
});
