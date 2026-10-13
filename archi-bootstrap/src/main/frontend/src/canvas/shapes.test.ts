import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { effectiveSize, hasOwnShape, knownShapeTypes, outlinePath, radiusOf, resolveBox, shapeOf } from './shapes';
import { paintOf, parseArchiFont } from './style';

const SPRITE = readFileSync(join(import.meta.dirname, '../../../resources/ui/icons.svg'), 'utf8');

describe('UI-019: фигуры по общей геометрии', () => {
  it('каждая иконка из shapes.json есть в спрайте', () => {
    const ids = new Set([...SPRITE.matchAll(/<symbol id="([^"]+)"/g)].map((m) => m[1]));
    for (const type of knownShapeTypes()) {
      const icon = shapeOf(type).corner_icon;
      if (icon) {
        expect(ids, `${type} → ${icon.sprite_id}`).toContain(icon.sprite_id);
      }
    }
  });

  it('иконка — 14×14 в правом верхнем углу', () => {
    const icon = shapeOf('archimate:ApplicationComponent').corner_icon!;
    expect(icon.size).toBe(14);
    const box = resolveBox({ x: icon.x, y: icon.y, width: icon.size, height: icon.size }, { width: 120, height: 55 });
    expect(box.x + box.width).toBeLessThan(120);
    expect(box.x).toBeGreaterThan(120 / 2);
  });

  it('радиус — из токена: поведение скруглено сильнее структуры', () => {
    expect(radiusOf(shapeOf('archimate:BusinessProcess'))).toBeGreaterThan(radiusOf(shapeOf('archimate:BusinessActor')));
  });

  it('сервис — «стадион», скругление в половину высоты', () => {
    const d = outlinePath(shapeOf('archimate:ApplicationService'), { width: 120, height: 54 }, 0);
    expect(d).toContain('A27 27');
  });

  it('размер -1 из файла Archi — размер по умолчанию для типа', () => {
    expect(effectiveSize('archimate:ApplicationComponent', -1, -1)).toEqual({ width: 120, height: 55 });
    expect(effectiveSize('archimate:Group', -1, -1)).toEqual({ width: 400, height: 140 });
    expect(effectiveSize('archimate:Note', 300, 90)).toEqual({ width: 300, height: 90 });
  });
});

describe('UI-020: тип без записи — заглушка, а не пропуск', () => {
  it('фаза 3 рисуется пунктирным силуэтом заглушки', () => {
    expect(hasOwnShape('archimate:WorkPackage')).toBe(false);
    expect(shapeOf('archimate:WorkPackage').outline).toBe('stub');
    expect(shapeOf('archimate:WorkPackage').dash).toBeTruthy();
  });

  it('цвет заглушки — слой самого элемента', () => {
    expect(paintOf(shapeOf('archimate:Equipment'), 'PHYSICAL').fill).toBe('var(--layer-technology)');
  });
});

describe('FR-08: фаза 2 рисуется своими силуэтами', () => {
  it('мотивация — прямоугольник со срезанными углами, тот же путь, что у сервера (OutlinesTest)', () => {
    const goal = shapeOf('archimate:Goal');
    expect(goal.outline).toBe('bevel');
    expect(outlinePath(goal, { width: 120, height: 55 })).toBe(
      'M8.5 0.5 H111.5 L119.5 8.5 V46.5 L111.5 54.5 H8.5 L0.5 46.5 V8.5 Z',
    );
    expect(paintOf(goal, 'MOTIVATION').fill).toBe('var(--layer-motivation)');
  });

  it('способность — поведение, скруглена сильнее ресурса', () => {
    expect(radiusOf(shapeOf('archimate:Capability'))).toBeGreaterThan(radiusOf(shapeOf('archimate:Resource')));
    expect(shapeOf('archimate:Capability').corner_icon?.sprite_id).toBe('i-capability');
    expect(paintOf(shapeOf('archimate:Capability'), 'STRATEGY').fill).toBe('var(--layer-strategy)');
  });
});

describe('UI-011: собственный стиль объекта сильнее токена слоя', () => {
  it('fillColor из файла вместо токена', () => {
    const entry = shapeOf('archimate:ApplicationComponent');
    expect(paintOf(entry, 'APPLICATION').fill).toBe('var(--layer-application)');
    expect(paintOf(entry, 'APPLICATION', { fillColor: '#c0c0c0' }).fill).toBe('#c0c0c0');
  });

  it('шрифт Archi разбирается из строки SWT', () => {
    expect(parseArchiFont('1|Segoe UI|10.0|1|WINDOWS|1|-13|0|0|0|700|0|0|0|0|3|2|1|34|Segoe UI')).toEqual({
      family: 'Segoe UI',
      sizePt: 10,
      bold: true,
      italic: false,
    });
  });
});
