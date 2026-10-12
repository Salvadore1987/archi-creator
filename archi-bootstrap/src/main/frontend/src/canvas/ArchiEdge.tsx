import { BaseEdge, useStore, type EdgeProps, type ReactFlowState } from '@xyflow/react';
import { memo } from 'react';
import type { Uuid } from '../api/types';
import type { ViewDoc } from '../model/doc';
import { useEditor } from '../model/store';
import { markerUrl } from './EdgeMarkers';
import { notationOf } from './edgeNotation';
import type { ArchiFlowEdge } from './flowModel';
import { midpoint, roundedPath, route, type Rect } from './routing';
import tokens from 'virtual:design-tokens';

const CORNER = parseFloat(tokens.values.edge_corner_radius ?? '6');

/**
 * Прямоугольник конца ребра в координатах холста. Конец — узел (берётся
 * живая позиция React Flow, чтобы ребро шло за узлом во время перетаскивания)
 * или другое ребро — тогда точка в середине его маршрута.
 */
function endpointRect(state: ReactFlowState, view: ViewDoc, id: Uuid, depth = 0): Rect | null {
  const internal = state.nodeLookup.get(id);
  if (internal) {
    const width = internal.measured.width ?? internal.width ?? 0;
    const height = internal.measured.height ?? internal.height ?? 0;
    return { x: internal.internals.positionAbsolute.x, y: internal.internals.positionAbsolute.y, width, height };
  }
  const edge = view.edges[id];
  if (!edge || depth > 8) return null;
  const s = endpointRect(state, view, edge.sourceId, depth + 1);
  const t = endpointRect(state, view, edge.targetId, depth + 1);
  if (!s || !t) return null;
  const p = midpoint(route(s, t, edge.bendpoints));
  return { x: p.x - 0.5, y: p.y - 0.5, width: 1, height: 1 };
}

const sameRect = (a: Rect | null, b: Rect | null) =>
  a === b || (!!a && !!b && a.x === b.x && a.y === b.y && a.width === b.width && a.height === b.height);

export const ArchiEdge = memo(function ArchiEdge({ id, data, selected }: EdgeProps<ArchiFlowEdge>) {
  const view = useEditor((s) => (data ? s.doc?.loadedViews[data.viewId] : undefined));
  const edge = view?.edges[id];
  const relationship = useEditor((s) => (edge?.relationshipId ? s.doc?.relationships[edge.relationshipId] : undefined));
  const ends = useStore(
    (state) =>
      view && edge
        ? ([endpointRect(state, view, edge.sourceId), endpointRect(state, view, edge.targetId)] as const)
        : ([null, null] as const),
    (a, b) => sameRect(a[0], b[0]) && sameRect(a[1], b[1]),
  );
  if (!edge || !ends[0] || !ends[1]) return null;

  const points = route(ends[0], ends[1], edge.bendpoints);
  const notation = notationOf(relationship?.archiType ?? edge.archiType, relationship?.accessType, relationship?.directed);
  const label = relationship?.name;
  const at = midpoint(points);
  const stroke = selected ? 'var(--accent)' : (edge.style?.lineColor ?? 'var(--edge-color)');

  return (
    <>
      <BaseEdge
        id={id}
        path={roundedPath(points, CORNER)}
        markerStart={markerUrl(notation.start, 'start', !!selected)}
        markerEnd={markerUrl(notation.end, 'end', !!selected)}
        interactionWidth={12}
        style={{
          stroke,
          strokeWidth: selected ? 'var(--edge-width-selected)' : 'var(--edge-width)',
          strokeDasharray: notation.dash,
          fill: 'none',
        }}
      />
      {label && (
        <text className="archi-edge__label" x={at.x} y={at.y - 4} textAnchor="middle" style={{ fill: edge.style?.fontColor }}>
          {label}
        </text>
      )}
    </>
  );
});
