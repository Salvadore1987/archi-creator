import shapesJson from '@shared-ui/shapes.json';
import tokens from 'virtual:design-tokens';

/** Запись силуэта: общий с серверным писателем SVG формат. */
export interface ShapeEntry {
  outline: Primitive;
  corner_icon: { sprite_id: string; x: number; y: number; size: number } | null;
  label_box: { x: number; y: number; width: number; height: number } | null;
  radius_token: string;
  layer: string | null;
  dash?: string;
}

export type Primitive = 'rect' | 'stadium' | 'circle' | 'tab' | 'dog-ear' | 'stub';

interface ShapesFile {
  default_size: Size;
  default_size_by_type: Record<string, Size>;
  shapes: Record<string, ShapeEntry>;
  fallback: ShapeEntry;
}

export interface Size {
  width: number;
  height: number;
}

export interface Box {
  x: number;
  y: number;
  width: number;
  height: number;
}

const SHAPES = shapesJson as unknown as ShapesFile;

/** Высота ярлыка группы и предел его ширины — как в описании примитива `tab`. */
const TAB_HEIGHT = 18;
const TAB_MAX_WIDTH = 120;
const DOG_EAR = 12;

/** Силуэт по xsi:type; тип без записи — заглушка с настоящим именем. */
export function shapeOf(archiType: string): ShapeEntry {
  return SHAPES.shapes[archiType] ?? SHAPES.fallback;
}

export function hasOwnShape(archiType: string): boolean {
  return archiType in SHAPES.shapes;
}

export function knownShapeTypes(): string[] {
  return Object.keys(SHAPES.shapes);
}

/** Размер `-1` в файле Archi означает «по умолчанию для типа». */
export function effectiveSize(archiType: string, width: number, height: number): Size {
  const fallback = SHAPES.default_size_by_type[archiType] ?? SHAPES.default_size;
  return {
    width: width > 0 ? width : fallback.width,
    height: height > 0 ? height : fallback.height,
  };
}

/** Отрицательное смещение — от дальнего края, неположительный размер — размер фигуры минус модуль. */
export function resolveBox(box: Box, size: Size): Box {
  const x = box.x < 0 ? size.width + box.x : box.x;
  const y = box.y < 0 ? size.height + box.y : box.y;
  const width = box.width <= 0 ? size.width + box.width - x : box.width;
  const height = box.height <= 0 ? size.height + box.height - y : box.height;
  return { x, y, width: Math.max(0, width), height: Math.max(0, height) };
}

export function radiusOf(entry: ShapeEntry): number {
  return parseFloat(tokens.values[entry.radius_token] ?? '0');
}

/**
 * Контур фигуры путём SVG в боксе `size`. Обводка рисуется по середине линии,
 * поэтому контур отступает внутрь на половину её толщины — иначе край обрезается.
 */
export function outlinePath(entry: ShapeEntry, size: Size, inset = 0.5): string {
  const x0 = inset;
  const y0 = inset;
  const x1 = size.width - inset;
  const y1 = size.height - inset;
  const w = x1 - x0;
  const h = y1 - y0;
  switch (entry.outline) {
    case 'stadium':
      return roundedRect(x0, y0, w, h, h / 2);
    case 'circle': {
      const r = Math.min(w, h) / 2;
      const cx = x0 + w / 2;
      const cy = y0 + h / 2;
      return `M${cx - r} ${cy} A${r} ${r} 0 1 0 ${cx + r} ${cy} A${r} ${r} 0 1 0 ${cx - r} ${cy} Z`;
    }
    case 'tab': {
      const tab = Math.min(TAB_MAX_WIDTH, w / 3);
      return `M${x0} ${y0} H${x0 + tab} V${y0 + TAB_HEIGHT} H${x1} V${y1} H${x0} Z M${x0} ${y0 + TAB_HEIGHT} H${x0 + tab}`;
    }
    case 'dog-ear':
      return `M${x0} ${y0} H${x1 - DOG_EAR} L${x1} ${y0 + DOG_EAR} V${y1} H${x0} Z M${x1 - DOG_EAR} ${y0} V${y0 + DOG_EAR} H${x1}`;
    case 'rect':
    case 'stub':
    default:
      return roundedRect(x0, y0, w, h, radiusOf(entry));
  }
}

function roundedRect(x: number, y: number, w: number, h: number, radius: number): string {
  const r = Math.max(0, Math.min(radius, w / 2, h / 2));
  if (r === 0) {
    return `M${x} ${y} H${x + w} V${y + h} H${x} Z`;
  }
  return (
    `M${x + r} ${y} H${x + w - r} A${r} ${r} 0 0 1 ${x + w} ${y + r} ` +
    `V${y + h - r} A${r} ${r} 0 0 1 ${x + w - r} ${y + h} ` +
    `H${x + r} A${r} ${r} 0 0 1 ${x} ${y + h - r} ` +
    `V${y + r} A${r} ${r} 0 0 1 ${x + r} ${y} Z`
  );
}
