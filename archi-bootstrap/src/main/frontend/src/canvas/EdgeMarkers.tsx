import type { MarkerId } from './edgeNotation';

const SHAPES: Record<MarkerId, { path: string; filled: boolean; refX: number; width: number; height: number }> = {
  'diamond-filled': { path: 'M1 5 L7 1.4 L13 5 L7 8.6 Z', filled: true, refX: 13, width: 14, height: 10 },
  'diamond-open': { path: 'M1 5 L7 1.4 L13 5 L7 8.6 Z', filled: false, refX: 13, width: 14, height: 10 },
  dot: { path: 'M1.5 5 A3 3 0 1 0 7.5 5 A3 3 0 1 0 1.5 5 Z', filled: true, refX: 7.5, width: 9, height: 10 },
  'arrow-filled': { path: 'M1.5 1 L9.5 5 L1.5 9 Z', filled: true, refX: 9.5, width: 11, height: 10 },
  'arrow-open': { path: 'M2.5 1.5 L9.5 5 L2.5 8.5', filled: false, refX: 9.5, width: 11, height: 10 },
  'triangle-open': { path: 'M1.5 1 L11.5 5 L1.5 9 Z', filled: false, refX: 11.5, width: 13, height: 10 },
};

export function markerUrl(id: MarkerId | undefined, place: 'start' | 'end', selected: boolean): string | undefined {
  return id ? `url(#m-${id}-${place}${selected ? '-sel' : ''})` : undefined;
}

/**
 * Наконечники связей. Фигура лежит на линии до точки `refX`; начальные
 * развёрнуты `auto-start-reverse`, поэтому и у источника фигура ложится
 * на линию, а стрелка смотрит наружу. У выделенной связи — акцентный цвет.
 */
export function EdgeMarkers() {
  const variants: Array<['start' | 'end', boolean]> = [
    ['start', false],
    ['end', false],
    ['start', true],
    ['end', true],
  ];
  return (
    <svg className="edge-markers" aria-hidden>
      <defs>
        {variants.flatMap(([place, selected]) =>
          (Object.keys(SHAPES) as MarkerId[]).map((id) => {
            const shape = SHAPES[id];
            const color = selected ? 'var(--accent)' : 'var(--edge-color)';
            return (
              <marker
                key={`${id}-${place}-${selected}`}
                id={`m-${id}-${place}${selected ? '-sel' : ''}`}
                markerWidth={shape.width}
                markerHeight={shape.height}
                refX={shape.refX}
                refY={5}
                orient={place === 'start' ? 'auto-start-reverse' : 'auto'}
                markerUnits="userSpaceOnUse"
              >
                <path
                  d={shape.path}
                  fill={shape.filled ? color : id === 'arrow-open' ? 'none' : 'var(--paper)'}
                  stroke={color}
                  strokeWidth={1.3}
                  strokeLinejoin="round"
                  strokeLinecap="round"
                />
              </marker>
            );
          }),
        )}
      </defs>
    </svg>
  );
}
