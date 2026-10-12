import {
  Background,
  BackgroundVariant,
  ReactFlow,
  applyEdgeChanges,
  applyNodeChanges,
  useReactFlow,
  useStoreApi,
  type EdgeChange,
  type NodeChange,
} from '@xyflow/react';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/endpoints';
import type { RelationType, Uuid, ViewNode } from '../api/types';
import { applyChanges, type Change, type ModelDoc, type ViewDoc } from '../model/doc';
import { Icon } from '../app/Icon';
import { useCanEdit } from '../app/session';
import { useToast } from '../app/toast';
import { useUi } from '../app/uiState';
import { t, typeName } from '../i18n';
import {
  conceptTypeOfEndpoint,
  copySelection,
  createAndPlace,
  drawRelationship,
  impliedRelationship,
  moveNodes,
  nestNodes,
  pasteClip,
  placeAnnotation,
  placeElement,
  removeFromView,
  resizeNode,
} from '../model/ops';
import { useEditor } from '../model/store';
import { DRAG_ELEMENT } from '../tree/TreePanel';
import { ArchiEdge } from './ArchiEdge';
import { ArchiNode, setNodeEditing } from './ArchiNode';
import {
  absolutePosition,
  containerAt,
  descendants,
  nodeSelected,
  toFlowEdges,
  toFlowNodes,
  type ArchiFlowEdge,
  type ArchiFlowNode,
} from './flowModel';
import { DRAG_PALETTE_TYPE, parsePaletteDrag } from './palette-drag';
import { effectiveSize } from './shapes';
import { CanvasTools } from './CanvasTools';
import { RelationPicker, type RelationChoice } from './RelationPicker';
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
  const flowStore = useStoreApi();
  const wrapper = useRef<HTMLDivElement>(null);
  const toast = useToast((s) => s.show);
  const queries = useQueryClient();
  const [connecting, setConnecting] = useState<{ sourceId: Uuid; from: Point; to: Point } | null>(null);
  const [choice, setChoice] = useState<(RelationChoice & { sourceId: Uuid; targetId: Uuid }) | null>(null);

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
      onConnectStart: (id, event) => {
        const box = wrapper.current?.getBoundingClientRect();
        const nodeBox = (event.currentTarget as HTMLElement).closest('.react-flow__node')?.getBoundingClientRect();
        if (!box || !nodeBox) return;
        const from = { x: nodeBox.left + nodeBox.width / 2 - box.left, y: nodeBox.top + nodeBox.height / 2 - box.top };
        setChoice(null);
        setConnecting({ sourceId: id, from, to: { x: event.clientX - box.left, y: event.clientY - box.top } });
      },
    });
  }, [canEdit, viewId, perform, doc.elements]);

  // Копирование и вставка, как в Archi: Ctrl+C, Ctrl+V. В другое представление элемент
  // вставляется ссылкой, в то, где он уже есть, — копией.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (!(event.metaKey || event.ctrlKey) || event.altKey || event.shiftKey) return;
      const key = event.key.toLowerCase();
      if (key !== 'c' && key !== 'v') return;
      const target = event.target as HTMLElement | null;
      if (target && target !== document.body && !wrapper.current?.contains(target)) return;
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) return;
      const state = useEditor.getState();
      const current = state.doc?.loadedViews[viewId];
      if (!current || !state.doc) return;
      if (key === 'c') {
        const chosen = new Set(state.selection.ids);
        const clip = copySelection(current, Object.values(current.nodes).filter((n) => nodeSelected(n, chosen)).map((n) => n.id));
        if (!clip) return;
        event.preventDefault();
        useUi.getState().setClipboard(clip);
        toast(t('canvas.copied', { n: clip.nodes.length }));
        return;
      }
      const clipboard = useUi.getState().clipboard;
      if (!clipboard || !canEdit) return;
      event.preventDefault();
      const step = useUi.getState().notePaste() * GRID;
      const shift = clipboard.clip.viewId === viewId ? step : step - GRID;
      const edit = pasteClip(state.doc, current, clipboard.clip, { x: shift, y: shift }, (name) =>
        name ? t('canvas.copyName', { name }) : name,
      );
      perform(t('tree_ops.pasteOp', { n: edit.pasted.length }), edit.changes, edit.affected);
      select(edit.pasted, 'canvas');
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [viewId, canEdit, perform, select, toast]);

  // Масштаб с клавиатуры, как в Archi: Ctrl+= крупнее, Ctrl+- мельче, Ctrl+0 — 100 %.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (!(event.metaKey || event.ctrlKey) || event.altKey) return;
      const target = event.target as HTMLElement | null;
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) return;
      if (event.key === '=' || event.key === '+') {
        event.preventDefault();
        void flow.zoomIn({ duration: 120 });
      } else if (event.key === '-') {
        event.preventDefault();
        void flow.zoomOut({ duration: 120 });
      } else if (event.key === '0') {
        event.preventDefault();
        void flow.zoomTo(1, { duration: 120 });
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [flow]);

  // Протягивание связи: линия идёт за указателем, по отпусканию над узлом или ребром
  // появляется список типов, допустимых матрицей для пары.
  const connectingSource = connecting?.sourceId;
  useEffect(() => {
    if (!connectingSource) return;
    const move = (event: PointerEvent) => {
      const box = wrapper.current?.getBoundingClientRect();
      if (!box) return;
      setConnecting((current) => current && { ...current, to: { x: event.clientX - box.left, y: event.clientY - box.top } });
    };
    const up = (event: PointerEvent) => {
      setConnecting(null);
      const target = endpointAt(event.clientX, event.clientY);
      if (!target || target === connectingSource) return;
      void offerRelations(connectingSource, target, event.clientX, event.clientY);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up, { once: true });
    return () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
    };
    // offerRelations читает документ в момент отпускания.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectingSource]);

  const offerRelations = async (sourceId: Uuid, targetId: Uuid, clientX: number, clientY: number) => {
    const current = useEditor.getState().doc!;
    const currentView = current.loadedViews[viewId]!;
    const sourceType = conceptTypeOfEndpoint(current, currentView, sourceId);
    const targetType = conceptTypeOfEndpoint(current, currentView, targetId);
    if (!sourceType || !targetType) {
      toast(t('canvas.relationEndpoint'));
      return;
    }
    const options = await queries.fetchQuery({
      queryKey: ['metamodel', 'relations', sourceType, targetType],
      queryFn: () => api.relationTypes(sourceType, targetType),
      staleTime: Infinity,
    });
    const names = { source: nameOfEndpoint(sourceId), target: nameOfEndpoint(targetId) };
    if (options.length === 0) {
      toast(t('canvas.relationNone', names));
      return;
    }
    const box = wrapper.current?.getBoundingClientRect();
    if (!box) return;
    setChoice({
      sourceId,
      targetId,
      x: clientX - box.left + 4,
      y: clientY - box.top + 4,
      title: t('canvas.relationTitle', names),
      options,
    });
  };

  const nameOfEndpoint = (id: Uuid): string => {
    const current = useEditor.getState().doc!;
    const currentView = current.loadedViews[viewId]!;
    const node = currentView.nodes[id];
    if (node?.elementId) return current.elements[node.elementId]?.name ?? '';
    const relationship = currentView.edges[id]?.relationshipId;
    return relationship ? typeName(current.relationships[relationship]?.archiType ?? '') : '';
  };

  const pickRelation = (option: RelationType) => {
    if (!choice) return;
    setChoice(null);
    const current = useEditor.getState().doc!;
    const edit = drawRelationship(current, current.loadedViews[viewId]!, choice.sourceId, choice.targetId, option.archiType);
    if (!edit) return;
    if (edit.changes.length === 0) {
      toast(t('canvas.relationDrawn'));
    } else {
      perform(
        t('tree_ops.connectOp', {
          type: typeName(option.archiType),
          source: nameOfEndpoint(choice.sourceId),
          target: nameOfEndpoint(choice.targetId),
        }),
        edit.changes,
        edit.affected,
      );
    }
    select([edit.relationshipId], 'canvas');
  };

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
    // Рамка вокруг выделенного перехватывала бы клики по узлам внутри неё: Ctrl+клик
    // по одному из выделенных должен снимать с него выделение, как в Archi.
    queueMicrotask(() => flowStore.setState({ nodesSelectionActive: false }));
  }, [viewId, select, flowStore]);

  // Отпускание узла над контейнером вкладывает его, над пустым местом — выносит в корень.
  // Элемент в элементе получает подразумеваемую связь той же операцией истории.
  const dropTarget = useRef<Uuid | undefined>(undefined);
  const nestingDrop = (event: MouseEvent | TouchEvent, dragged: ArchiFlowNode[]) => {
    const current = useEditor.getState().doc?.loadedViews[viewId];
    if (!current) return undefined;
    const ids = new Set(dragged.map((n) => n.id));
    const moving = new Set([...ids, ...descendants(current, ids).map((n) => n.id)]);
    const at = 'touches' in event ? (event.changedTouches[0] ?? event.touches[0]) : event;
    if (!at) return undefined;
    const point = flow.screenToFlowPosition({ x: at.clientX, y: at.clientY });
    return { current, ids, container: containerAt(current, point, moving) };
  };

  const onNodeDrag = useCallback(
    (event: MouseEvent | TouchEvent, _: ArchiFlowNode, dragged: ArchiFlowNode[]) => {
      const drop = nestingDrop(event, dragged);
      const target = drop?.container?.id;
      if (target === dropTarget.current) return;
      dropTarget.current = target;
      setNodes((current) => current.map((n) => ({ ...n, className: n.id === target ? 'is-drop-target' : undefined })));
    },
    // nestingDrop читает документ в момент события.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [viewId, flow],
  );

  const onNodeDragStop = useCallback(
    (event: MouseEvent | TouchEvent, _: ArchiFlowNode, dragged: ArchiFlowNode[]) => {
      dropTarget.current = undefined;
      const drop = nestingDrop(event, dragged);
      if (!drop) return;
      const { current, ids, container } = drop;
      const tops = dragged.filter((n) => !hasAncestorIn(current, n.id, ids));
      const parentAt = container ? absolutePosition(current, container.id) : { x: 0, y: 0 };
      const snapTo = (v: number) => (snap ? Math.round(v / GRID) * GRID : Math.round(v));
      const moves = tops.map((n) => {
        const node = current.nodes[n.id]!;
        const oldParent = node.parentId && current.nodes[node.parentId] ? absolutePosition(current, node.parentId) : { x: 0, y: 0 };
        if ((node.parentId ?? undefined) === container?.id) {
          return { id: n.id, parentId: container?.id, x: Math.round(n.position.x), y: Math.round(n.position.y) };
        }
        return {
          id: n.id,
          parentId: container?.id,
          x: snapTo(oldParent.x + n.position.x - parentAt.x),
          y: snapTo(oldParent.y + n.position.y - parentAt.y),
        };
      });
      const nested = moves.some((m) => (current.nodes[m.id]!.parentId ?? undefined) !== m.parentId);
      // Вложенные в переносимые едут вместе с ними: их координаты относительны родителю.
      const inner = dragged
        .filter((n) => !tops.includes(n))
        .map((n) => ({ id: n.id, parentId: current.nodes[n.id]!.parentId, x: Math.round(n.position.x), y: Math.round(n.position.y) }));
      if (!nested) {
        const edit = moveNodes(current, [...moves, ...inner]);
        perform(t('tree_ops.moveNodeOp', { n: edit.changes.length }), edit.changes, edit.affected);
        setNodes((list) => list.map((n) => (n.className ? { ...n, className: undefined } : n)));
        return;
      }
      void nest(moves, container);
    },
    // nestingDrop и nest читают документ в момент события.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [viewId, perform, snap, flow],
  );

  const nest = async (moves: Array<{ id: Uuid; parentId?: Uuid; x: number; y: number }>, container?: ViewNode) => {
    const before = useEditor.getState().doc!;
    const edit = nestNodes(before.loadedViews[viewId]!, moves);
    const changes: Change[] = [...edit.changes];
    const affected = [...edit.affected];
    const children = moves
      .filter((m) => (before.loadedViews[viewId]!.nodes[m.id]!.parentId ?? undefined) !== m.parentId)
      .map((m) => before.loadedViews[viewId]!.nodes[m.id]!.elementId)
      .filter((id): id is Uuid => !!id);
    const implied = await impliedChanges(applyChanges(before, changes), container, children);
    changes.push(...implied.changes);
    affected.push(...implied.affected);
    setNodes((list) => list.map((n) => (n.className ? { ...n, className: undefined } : n)));
    const name = container
      ? (container.elementId ? before.elements[container.elementId]?.name : container.label) ?? ''
      : '';
    perform(container ? t('tree_ops.nestOp', { n: moves.length, name }) : t('tree_ops.unnestOp', { n: moves.length }), changes, affected);
  };

  /** Связи, которые подразумевает вложение элементов в элемент-контейнер; тип называет сервер. */
  const impliedChanges = async (working: ModelDoc, container: ViewNode | undefined, childElementIds: Uuid[]) => {
    const changes: Change[] = [];
    const affected: Uuid[] = [];
    const parent = container?.elementId ? working.elements[container.elementId] : undefined;
    if (!parent) return { changes, affected };
    let doc = working;
    for (const childId of childElementIds) {
      const child = doc.elements[childId];
      if (!child) continue;
      const options = await queries.fetchQuery({
        queryKey: ['metamodel', 'relations', parent.archiType, child.archiType],
        queryFn: () => api.relationTypes(parent.archiType, child.archiType),
        staleTime: Infinity,
      });
      const nesting = options.find((o) => o.nesting);
      if (!nesting) continue;
      const relation = impliedRelationship(doc, parent.id, child.id, nesting.archiType);
      changes.push(...relation.changes);
      affected.push(...relation.affected);
      doc = applyChanges(doc, relation.changes);
    }
    return { changes, affected };
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (!canEdit || (event.key !== 'Delete' && event.key !== 'Backspace')) return;
    const target = event.target as HTMLElement;
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return;
    const ids = Object.values(view.nodes)
      .filter((n) => nodeSelected(n, selected))
      .map((n) => n.id);
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

  // Отпущенное над контейнером ложится внутрь него, как в Archi; элемент в элементе
  // получает подразумеваемую связь той же операцией.
  const onDrop = async (event: React.DragEvent) => {
    if (!canEdit) return;
    event.preventDefault();
    const point = flow.screenToFlowPosition({ x: event.clientX, y: event.clientY });
    const snapTo = (v: number) => (snap ? Math.round(v / GRID) * GRID : Math.round(v));
    const current = useEditor.getState().doc!;
    const currentView = current.loadedViews[viewId]!;
    const container = containerAt(currentView, point, new Set());
    const origin = container ? absolutePosition(currentView, container.id) : { x: 0, y: 0 };
    const at = (size: { width: number; height: number }) => ({
      x: snapTo(point.x - size.width / 2 - origin.x),
      y: snapTo(point.y - size.height / 2 - origin.y),
    });
    const placeWithImplied = async (label: string, edit: { changes: Change[]; affected: Uuid[] }, elementId: Uuid) => {
      const implied = await impliedChanges(applyChanges(current, edit.changes), container, [elementId]);
      perform(label, [...edit.changes, ...implied.changes], [...edit.affected, ...implied.affected]);
      select([elementId], 'canvas');
    };
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
      const size = effectiveSize(element.archiType, -1, -1);
      const { x, y } = at(size);
      const edit = placeElement(currentView, elementId, x, y, size, container?.id);
      await placeWithImplied(t('tree_ops.placeOp', { name: element.name }), edit, elementId);
      return;
    }
    const paletteType = parsePaletteDrag(event.dataTransfer.getData(DRAG_PALETTE_TYPE));
    if (!paletteType) return;
    if (paletteType.kind) {
      const size = effectiveSize(paletteType.archiType, -1, -1);
      const { x, y } = at(size);
      const edit = placeAnnotation(currentView, paletteType.kind, paletteType.defaultName, x, y, container?.id);
      perform(t('tree_ops.createOp', { name: paletteType.defaultName || typeName(paletteType.archiType) }), edit.changes, edit.affected);
      select([edit.nodeId], 'canvas');
      return;
    }
    const size = effectiveSize(paletteType.archiType, -1, -1);
    const { x, y } = at(size);
    const name = paletteType.defaultName;
    const edit = createAndPlace(current, currentView, paletteType.archiType, paletteType.layer, name, x, y, size, container?.id);
    if (!edit) return;
    await placeWithImplied(t('tree_ops.createOp', { name: name || typeName(paletteType.archiType) }), edit, edit.elementId);
  };

  return (
    <div
      ref={wrapper}
      className={`canvas${canEdit ? '' : ' canvas--readonly'}`}
      data-testid="canvas"
      data-view={viewId}
      onKeyDown={onKeyDown}
      onDragOver={onDragOver}
      onDrop={(event) => void onDrop(event)}
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
        onNodeDrag={onNodeDrag}
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
        attributionPosition="bottom-left"
        elevateNodesOnSelect={false}
      >
        {snap && <Background variant={BackgroundVariant.Lines} gap={GRID} color="var(--paper-line)" />}
      </ReactFlow>
      {connecting && (
        <svg className="connect-line" data-testid="connect-line">
          <line x1={connecting.from.x} y1={connecting.from.y} x2={connecting.to.x} y2={connecting.to.y} />
        </svg>
      )}
      {choice && <RelationPicker choice={choice} onPick={pickRelation} onCancel={() => setChoice(null)} />}
      <CanvasTools viewId={viewId} canEdit={canEdit} />
      <Zoomer />
    </div>
  );
}

/** Есть ли среди предков узла кто-то из набора. */
function hasAncestorIn(view: ViewDoc, id: Uuid, set: ReadonlySet<Uuid>): boolean {
  let parent = view.nodes[id]?.parentId;
  while (parent) {
    if (set.has(parent)) return true;
    parent = view.nodes[parent]?.parentId;
  }
  return false;
}

interface Point {
  x: number;
  y: number;
}

/**
 * Узел или ребро холста под указателем. Узел сильнее ребра: полоса захвата ребра,
 * проходящего через фигуру, не должна превращать связь с элементом в связь со связью.
 */
function endpointAt(clientX: number, clientY: number): Uuid | undefined {
  const hits = document.elementsFromPoint(clientX, clientY);
  const first = (selector: string) => {
    for (const element of hits) {
      const id = element.closest(selector)?.getAttribute('data-id');
      if (id) return id;
    }
    return undefined;
  };
  return first('.react-flow__node') ?? first('.react-flow__edge');
}

function Zoomer() {
  const flow = useReactFlow();
  const zoom = useEditor((s) => (s.activeViewId ? s.viewports[s.activeViewId]?.zoom : undefined));
  return (
    <div className="zoomer">
      <button type="button" className="btn btn--icon" title={t('canvas.zoomOut')} onClick={() => void flow.zoomOut()}>
        <Icon name="minus" />
      </button>
      <span className="zoomer__val" data-testid="zoom-value">{`${Math.round((zoom ?? 1) * 100)}%`}</span>
      <button type="button" className="btn btn--icon" title={t('canvas.zoomIn')} onClick={() => void flow.zoomIn()}>
        <Icon name="plus" />
      </button>
      <button type="button" className="btn btn--icon" title={t('canvas.fit')} onClick={() => void flow.fitView({ padding: 0.1 })}>
        <Icon name="fit" />
      </button>
    </div>
  );
}
