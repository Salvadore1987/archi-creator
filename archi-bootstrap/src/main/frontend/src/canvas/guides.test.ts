import { describe, expect, it } from 'vitest';
import { alignmentGuides } from './guides';

describe('UI-006: направляющие', () => {
  it('край в пределах порога совмещается точно, линия идёт через оба узла', () => {
    const guides = alignmentGuides({ x: 103, y: 300, width: 120, height: 55 }, [{ x: 100, y: 0, width: 200, height: 60 }], 6);
    expect(guides.dx).toBe(-3);
    expect(guides.dy).toBe(0);
    expect(guides.lines).toEqual([{ x1: 100, y1: 0, x2: 100, y2: 355 }]);
  });

  it('середина к середине; вне порога — направляющих нет', () => {
    const near = alignmentGuides({ x: 400, y: 98, width: 100, height: 40 }, [{ x: 0, y: 80, width: 100, height: 80 }], 6);
    expect(near.dy).toBe(2);
    expect(alignmentGuides({ x: 400, y: 400, width: 100, height: 40 }, [{ x: 0, y: 80, width: 100, height: 80 }], 6).lines).toEqual([]);
  });
});
