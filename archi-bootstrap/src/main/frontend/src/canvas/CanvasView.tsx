import {
  Background,
  BackgroundVariant,
  ReactFlow,
  applyEdgeChanges,
  applyNodeChanges,
  useReactFlow,
  type EdgeChange,
  type NodeChange,
} from '@xyflow/react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { Uuid } from '../api/types';
import { Icon } from '../app/Icon';
import { useCanEdit } from '../app/session';
import { useToast } from '../app/toast';
import { useUi } from '../app/uiState';
import { t } from '../i18n';
import { createAndPlace, moveNodes, placeElement, removeFromView, resizeNode } from '../model/ops';
import { useEditor } from '../model/store';
import { DRAG_ELEMENT } from '../tree/TreePanel';
import { ArchiEdge } from './ArchiEdge';
import { ArchiNode, setNodeEditing } from './ArchiNode';
import {
  absolutePosition,
  nodeSelected,
  toFlowEdges,
  toFlowNodes,
  type ArchiFlowEdge,
  type ArchiFlowNode,
} from './flowModel';
import { DRAG_PALETTE_TYPE, parsePaletteDrag } from './palette-drag';
import tokens from 'virtual:design-tokens';

const nodeTypes = { archi: ArchiNode };
const edgeTypes = { archi: ArchiEdge };
const GRID = parseFloat(tokens.values.grid_step ?? '20');

/**
 * Холст одного представления. Узлы React Flow — производная документа:
 * во время перетаскивания они живут локально, а по отпусканию превращаются
 * в одну операцию истории.
 */
export function CanvasView({ viewId }: { viewId: Uuid }) {
  const doc = useEditor((s) => s.doc!);
  const view = doc.loadedViews[viewId]!;
  const selection = useEditor((s) => s.selection);
  const viewport = useEditor((s) => s.viewports[viewId]);
  const setViewport = useEditor((s) => s.setViewport);
  const select = useEditor((s) => s.select);
  const perform = useEditor((s) => s.perform);
  const snap = useUi((s) => s.snapToGrid);
  const canEdit = useCanEdit() && view.editable;
  const flow = useReactFlow();
  const wrapper = useRef<HTMLDivElement>(null);
  const toast = useToast((s) => s.show);

  const selected = useMemo(() => new Set(selection.ids), [selection.ids]);
  const derivedNodes = useMemo(() => toFlowNodes(view, selected, canEdit), [view, selected, canEdit]);
  const derivedEdges = useMemo(() => toFlowEdges(view, selected), [view, selected]);
  const [nodes, setNodes] = useState<ArchiFlowNode[]>(derivedNodes);
  const [edges, setEdges] = useState<ArchiFlowEdge[]>(derivedEdges);
  useEffect(() => setNodes(derivedNodes), [derivedNodes]);
  useEffect(() => setEdges(derivedEdges), [derivedEdges]);

  useEffect(() => {
    setNodeEditing({
      resizable: canEdit,
      onResizeEnd: (id, bounds) => {
        const current = useEditor.getState().doc?.loadedViews[viewId];
        if (!current) return;
        const node = current.nodes[id];
        const edit = resizeNode(current, id, bounds);
        perform(t('tree_ops.resizeOp', { name: node?.elementId ? (doc.elements[node.elementId]?.name ?? '') : '' }), edit.changes, edit.affected);
      },
    });
  }, [canEdit, viewId, perform, doc.elements]);

  // Выделение извне — из дерева, карточки, находки — показывает узел, если он за краем экрана.
  const lastSeq = useRef(selection.seq);
  useEffect(() => {
    if (selection.seq === lastSeq.current) return;
    lastSeq.current = selection.seq;
    if (selection.origin === 'canvas') return;
    const node = Object.values(view.nodes).find((n) => nodeSelected(n, selected));
    const box = wrapper.current?.getBoundingClientRect();
    if (!node || !box) return;
    const at = absolutePosition(view, node.id);
    const center = { x: at.x + Math.max(node.width, 60) / 2, y: at.y + Math.max(node.height, 30) / 2 };
    const screen = flow.flowToScreenPosition(center);
    if (screen.x < box.left || screen.x > box.right || screen.y < box.top || screen.y > box.bottom) {
      void flow.setCenter(center.x, center.y, { zoom: flow.getZoom(), duration: 250 });
    }
  }, [selection, selected, view, flow]);

  // Движение и размер в процессе жеста живут локально и попадают в документ
  // по отпусканию одной операцией. Выделение ведёт только документ: изменения
  // `select` от React Flow не применяются, иначе два источника перетягивают
  // его друг у друга и уходят в бесконечный цикл обновлений. Исключение —
  // рамка: пока она тянется, выделение копится локально.
  const boxSelecting = useRef(false);
  const onNodesChange = useCallback((changes: NodeChange<ArchiFlowNode>[]) => {
    const local = changes.filter(
      (c) => c.type === 'position' || c.type === 'dimensions' || (c.type === 'select' && boxSelecting.current),
    );
    if (local.length > 0) setNodes((current) => applyNodeChanges(local, current));
  }, []);

  const onEdgesChange = useCallback((changes: EdgeChange<ArchiFlowEdge>[]) => {
    const local = changes.filter((c) => c.type === 'select' && boxSelecting.current);
    if (local.length > 0) setEdges((current) => applyEdgeChanges(local, current));
  }, []);

  const objectOfNode = (id: Uuid) => view.nodes[id]?.elementId ?? id;
  const objectOfEdge = (id: Uuid) => view.edges[id]?.relationshipId ?? id;

  const clickObject = (objectId: Uuid, event: React.MouseEvent) => {
    const toggle = event.metaKey || event.ctrlKey || event.shiftKey;
    const current = useEditor.getState().selection.ids;
    const next = toggle
      ? current.includes(objectId)
        ? current.filter((id) => id !== objectId)
        : [...current, objectId]
      : [objectId];
    select(next, 'canvas');
  };

  const onSelectionEnd = useCallback(() => {
    boxSelecting.current = false;
    setNodes((current) => {
      const v = useEditor.getState().doc?.loadedViews[viewId];
      const ids = current.filter((n) => n.selected).map((n) => v?.nodes[n.id]?.elementId ?? n.id);
      queueMicrotask(() => select([...new Set(ids)], 'canvas'));
      return current;
    });
  }, [viewId, select]);

  const onNodeDragStop = useCallback(
    (_: unknown, __: ArchiFlowNode, dragged: ArchiFlowNode[]) => {
      const current = useEditor.getState().doc?.loadedViews[viewId];
      if (!current) return;
      const edit = moveNodes(
        current,
        dragged.map((n) => ({ id: n.id, x: Math.round(n.position.x), y: Math.round(n.position.y) })),
      );
      perform(t('tree_ops.moveNodeOp', { n: edit.changes.length }), edit.changes, edit.affected);
    },
    [viewId, perform],
  );

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (!canEdit || (event.key !== 'Delete' && event.key !== 'Backspace')) return;
    const target = event.target as HTMLElement;
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return;
    const ids = Object.values(view.nodes)
      .filter((n) => nodeSelected(n, selected))
      .filter((n) => n.kind === 'DIAGRAM_OBJECT' || n.kind === 'OTHER')
      .map((n) => n.id);
    if (Object.values(view.nodes).some((n) => nodeSelected(n, selected) && (n.kind === 'GROUP' || n.kind === 'NOTE'))) {
      toast(t('errors.unsupported', { detail: t('canvas.groupRemoval') }), { tone: 'error' });
    }
    if (ids.length === 0) return;
    event.preventDefault();
    const edit = removeFromView(view, ids);
    perform(t('tree_ops.removeFromViewOp', { n: ids.length }), edit.changes, edit.affected);
  };

  const onDragOver = (event: React.DragEvent) => {
    if (!canEdit) return;
    const types = event.dataTransfer.types;
    if (types.includes(DRAG_ELEMENT) || types.includes(DRAG_PALETTE_TYPE)) {
      event.preventDefault();
      event.dataTransfer.dropEffect = 'copy';
    }
  };

  const onDrop = (event: React.DragEvent) => {
    if (!canEdit) return;
    event.preventDefault();
    const point = flow.screenToFlowPosition({ x: event.clientX, y: event.clientY });
    const snapTo = (v: number) => (snap ? Math.round(v / GRID) * GRID : Math.round(v));
    const x = snapTo(point.x - 60);
    const y = snapTo(point.y - 27);
    const current = useEditor.getState().doc!;
    const currentView = current.loadedViews[viewId]!;
    const elementId = event.dataTransfer.getData(DRAG_ELEMENT);
    if (elementId) {
      const existing = Object.values(currentView.nodes).find((n) => n.elementId === elementId);
      if (existing) {
        // Повторное размещение не создаёт дубля — показываем, где элемент уже есть.
        select([elementId], 'external');
        toast(t('canvas.alreadyPlaced'));
        return;
      }
      const element = current.elements[elementId];
      if (!element) return;
      const edit = placeElement(currentView, elementId, x, y);
      perform(t('tree_ops.placeOp', { name: element.name }), edit.changes, edit.affected);
      select([elementId], 'canvas');
      return;
    }
    const paletteType = parsePaletteDrag(event.dataTransfer.getData(DRAG_PALETTE_TYPE));
    if (paletteType) {
      const name = paletteType.defaultName;
      const edit = createAndPlace(current, currentView, paletteType.archiType, paletteType.layer, name, x, y);
      if (!edit) return;
      perform(t('tree_ops.createOp', { name }), edit.changes, edit.affected);
      select([edit.elementId], 'canvas');
    }
  };

  return (
    <div
      ref={wrapper}
      className={`canvas${canEdit ? '' : ' canvas--readonly'}`}
      data-testid="canvas"
      data-view={viewId}
      onKeyDown={onKeyDown}
      onDragOver={onDragOver}
      onDrop={onDrop}
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={(event, node) => clickObject(objectOfNode(node.id), event)}
        onEdgeClick={(event, edge) => clickObject(objectOfEdge(edge.id), event)}
        onPaneClick={() => {
          if (useEditor.getState().selection.ids.length > 0) select([], 'canvas');
        }}
        onNodeDragStart={(_, node) => {
          const objectId = objectOfNode(node.id);
          if (!useEditor.getState().selection.ids.includes(objectId)) select([objectId], 'canvas');
        }}
        onSelectionStart={() => {
          boxSelecting.current = true;
        }}
        onSelectionEnd={onSelectionEnd}
        onNodeDragStop={onNodeDragStop}
        defaultViewport={viewport}
        fitView={!viewport}
        fitViewOptions={{ padding: 0.1, maxZoom: 1 }}
        onMoveEnd={(_, vp) => setViewport(viewId, vp)}
        minZoom={0.1}
        maxZoom={4}
        snapToGrid={snap}
        snapGrid={[GRID, GRID]}
        nodesDraggable={canEdit}
        nodesConnectable={false}
        elementsSelectable
        deleteKeyCode={null}
        multiSelectionKeyCode={['Meta', 'Control', 'Shift']}
        selectionKeyCode="Shift"
        onlyRenderVisibleElements
        proOptions={{ hideAttribution: true }}
        elevateNodesOnSelect={false}
      >
        <Background variant={BackgroundVariant.Lines} gap={GRID} color="var(--paper-line)" />
      </ReactFlow>
      <Zoomer />
    </div>
  );
}

function Zoomer() {
  const flow = useReactFlow();
  const zoom = useEditor((s) => (s.activeViewId ? s.viewports[s.activeViewId]?.zoom : undefined));
  return (
    <div className="zoomer">
      <button type="button" className="btn btn--icon" title={t('canvas.zoomOut')} onClick={() => void flow.zoomOut()}>
        <Icon name="minus" />
      </button>
      <span className="zoomer__val">{`${Math.round((zoom ?? 1) * 100)}%`}</span>
      <button type="button" className="btn btn--icon" title={t('canvas.zoomIn')} onClick={() => void flow.zoomIn()}>
        <Icon name="plus" />
      </button>
      <button type="button" className="btn btn--icon" title={t('canvas.fit')} onClick={() => void flow.fitView({ padding: 0.1 })}>
        <Icon name="fit" />
      </button>
    </div>
  );
}
