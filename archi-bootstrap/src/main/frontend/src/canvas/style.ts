import type { ShapeEntry } from './shapes';

/** Собственный стиль объекта из файла: сильнее токена слоя. */
export interface ObjectStyle {
  fillColor?: string | null;
  font?: string | null;
  fontColor?: string | null;
  lineColor?: string | null;
  textAlignment?: number | null;
}

/** Ключ слоя модели — в ключ токена. Физический слой красится как технологии, реализация — как прочее. */
export function layerToken(layer: string | null | undefined): string {
  switch ((layer ?? '').toUpperCase()) {
    case 'BUSINESS':
      return 'business';
    case 'APPLICATION':
      return 'application';
    case 'TECHNOLOGY':
    case 'PHYSICAL':
      return 'technology';
    case 'MOTIVATION':
      return 'motivation';
    case 'STRATEGY':
      return 'strategy';
    default:
      return 'other';
  }
}

export interface Paint {
  fill: string;
  stroke: string;
  text: string;
  font?: FontSpec;
  dash?: string;
}

export interface FontSpec {
  family: string;
  sizePt: number;
  bold: boolean;
  italic: boolean;
}

/**
 * Краски фигуры: собственный стиль объекта, иначе токен слоя.
 * Слой берётся из записи силуэта, а если там пусто — слой самого элемента.
 */
export function paintOf(entry: ShapeEntry, elementLayer: string | null | undefined, style?: ObjectStyle | null): Paint {
  const layer = entry.layer ?? layerToken(elementLayer);
  const base =
    layer === 'group'
      ? { fill: 'var(--group-fill)', stroke: 'var(--group-border)' }
      : layer === 'note'
        ? { fill: 'var(--note-fill)', stroke: 'var(--note-border)' }
        : layer === 'junction'
          ? { fill: 'var(--edge-color)', stroke: 'var(--edge-color)' }
        : { fill: `var(--layer-${layer})`, stroke: `var(--layer-${layer}-border)` };
  return {
    fill: style?.fillColor || base.fill,
    stroke: style?.lineColor || base.stroke,
    text: style?.fontColor || 'var(--paper-text)',
    font: style?.font ? parseArchiFont(style.font) : undefined,
    dash: entry.dash,
  };
}

/**
 * Шрифт Archi записан строкой SWT: `1|Segoe UI|10.0|1|WINDOWS|…` —
 * версия, семейство, кегль в пунктах, начертание (1 — жирный, 2 — курсив).
 */
export function parseArchiFont(value: string): FontSpec | undefined {
  const parts = value.split('|');
  const family = parts[1];
  const size = Number(parts[2]);
  const flags = Number(parts[3] ?? 0);
  if (!family || !Number.isFinite(size)) {
    return undefined;
  }
  return { family, sizePt: size, bold: (flags & 1) === 1, italic: (flags & 2) === 2 };
}

/** Выравнивание подписи Archi: 1 — влево, 2 — по центру, 4 — вправо. */
export function textAlign(alignment: number | null | undefined): 'left' | 'center' | 'right' {
  return alignment === 1 ? 'left' : alignment === 4 ? 'right' : 'center';
}
