import { BaseEdge, EdgeLabelRenderer, useReactFlow, useStore, type EdgeProps, type ReactFlowState } from '@xyflow/react';
import { memo, useRef, useState } from 'react';
import { t } from '../i18n';
import { setBendpoints } from '../model/ops';
import type { Uuid } from '../api/types';
import type { ViewDoc } from '../model/doc';
import { useEditor } from '../model/store';
import { markerUrl } from './EdgeMarkers';
import { notationOf } from './edgeNotation';
import type { ArchiFlowEdge } from './flowModel';
import { absoluteBendpoints, chopbox, midpoint, relativeBendpoints, roundedPath, route, type Point, type Rect } from './routing';
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
  const [drag, setDrag] = useState<{ bends: Point[] } | null>(null);
  if (!edge || !ends[0] || !ends[1]) return null;

  const [source, target] = ends as readonly [Rect, Rect];
  const preview = drag ? relativeBendpoints(source, target, drag.bends) : edge.bendpoints;
  const points = route(source, target, preview);
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
      {selected && data?.editable && (
        <BendpointHandles
          edgeId={id}
          viewId={data.viewId}
          source={source}
          target={target}
          points={points}
          bends={drag?.bends ?? absoluteBendpoints(source, target, edge.bendpoints)}
          onPreview={(bends) => setDrag(bends ? { bends } : null)}
        />
      )}
      {label && (
        <text className="archi-edge__label" x={at.x} y={at.y - 4} textAnchor="middle" style={{ fill: edge.style?.fontColor }}>
          {label}
        </text>
      )}
    </>
  );
});

/**
 * Ручки точек перегиба выделенного ребра: квадрат — точка, её можно тянуть, двойной
 * клик убирает; кружок на середине отрезка — тянется, чтобы добавить новую. Правка
 * фиксируется по отпусканию одной операцией.
 */
function BendpointHandles(props: {
  edgeId: string;
  viewId: string;
  source: Rect;
  target: Rect;
  points: Point[];
  bends: Point[];
  onPreview(bends: Point[] | null): void;
}) {
  const flow = useReactFlow();
  const { bends } = props;
  const chain = bends.length > 0
    ? [chopbox(props.source, bends[0]!), ...bends, chopbox(props.target, bends[bends.length - 1]!)]
    : props.points;
  // Без точек у ребра один отрезок для вставки — середина маршрута.
  const inserts = bends.length > 0
    ? chain.slice(1).map((p, i) => ({ index: i, at: { x: (p.x + chain[i]!.x) / 2, y: (p.y + chain[i]!.y) / 2 } }))
    : [{ index: 0, at: midpoint(props.points) }];

  const commit = (next: Point[]) => {
    props.onPreview(null);
    const state = useEditor.getState();
    const view = state.doc?.loadedViews[props.viewId];
    if (!view) return;
    const edit = setBendpoints(view, props.edgeId, relativeBendpoints(props.source, props.target, next));
    state.perform(t('tree_ops.bendpointsOp'), edit.changes, edit.affected);
  };

  // Двойной щелчок ловится по двум нажатиям без движения: preventDefault на pointerdown
  // гасит браузерный dblclick.
  const lastPress = useRef<{ index: number; at: number } | null>(null);
  const start = (event: React.PointerEvent, build: (point: Point) => Point[], removeIndex?: number) => {
    if (event.button !== 0) return;
    const now = event.timeStamp;
    if (removeIndex !== undefined && lastPress.current?.index === removeIndex && now - lastPress.current.at < 400) {
      lastPress.current = null;
      event.stopPropagation();
      event.preventDefault();
      commit(bends.filter((_, j) => j !== removeIndex));
      return;
    }
    lastPress.current = removeIndex !== undefined ? { index: removeIndex, at: now } : null;
    event.stopPropagation();
    event.preventDefault();
    const handle = event.currentTarget as HTMLElement;
    handle.setPointerCapture(event.pointerId);
    let last: Point[] | null = null;
    const move = (e: PointerEvent) => {
      last = build(flow.screenToFlowPosition({ x: e.clientX, y: e.clientY }));
      lastPress.current = null;
      props.onPreview(last);
    };
    const up = () => {
      handle.removeEventListener('pointermove', move);
      handle.removeEventListener('pointerup', up);
      if (last) commit(last);
      else props.onPreview(null);
    };
    handle.addEventListener('pointermove', move);
    handle.addEventListener('pointerup', up);
  };

  const at = (p: Point) => ({ transform: `translate(-50%, -50%) translate(${p.x}px, ${p.y}px)` });
  return (
    <EdgeLabelRenderer>
      {bends.map((p, i) => (
        <div
          key={`b${i}`}
          className="bend-handle nodrag nopan"
          data-bendpoint={i}
          title={t('canvas.bendpointHint')}
          style={at(p)}
          onPointerDown={(e) => start(e, (point) => bends.map((b, j) => (j === i ? point : b)), i)}
        />
      ))}
      {inserts.map(({ index, at: p }) => (
        <div
          key={`i${index}`}
          className="bend-insert nodrag nopan"
          data-bend-insert={index}
          title={t('canvas.bendInsertHint')}
          style={at(p)}
          onPointerDown={(e) => start(e, (point) => [...bends.slice(0, index), point, ...bends.slice(index)])}
        />
      ))}
    </EdgeLabelRenderer>
  );
}
