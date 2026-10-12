/** Перетаскивание с палитры: тип и слой нового элемента, имя по умолчанию — русское название типа. */
export const DRAG_PALETTE_TYPE = 'application/x-archi-type';

export interface PaletteDrag {
  archiType: string;
  layer: string;
  defaultName: string;
}

export function paletteDragData(drag: PaletteDrag): string {
  return JSON.stringify(drag);
}

export function parsePaletteDrag(raw: string): PaletteDrag | null {
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as PaletteDrag;
    return value.archiType && value.layer ? value : null;
  } catch {
    return null;
  }
}
