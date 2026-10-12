import type {
  AutoLayoutRequest,
  Bendpoint,
  Element,
  Folder,
  LayoutPatch,
  LayoutStrategy,
  Property,
  Relationship,
  Style,
  Uuid,
  ViewEdge,
  ViewNode,
} from '../api/types';
import { attachedEdges, descendants } from '../canvas/flowModel';
import { applyChanges, attachedRelationships, type Change, type ModelDoc, type ViewDoc } from './doc';
import { newArchiId, uuidv7 } from './ids';

/** Готовая правка: изменения и объекты, которые пометить в дереве. */
export interface Edit {
  changes: Change[];
  affected: Uuid[];
}

const SORT_STEP = 1000;

/** Следующая позиция в конце родителя — разреженная нумерация, соседи не трогаются. */
export function nextSortOrder(orders: number[]): number {
  return orders.length === 0 ? SORT_STEP : Math.max(...orders) + SORT_STEP;
}

export function moveNodes(
  view: ViewDoc,
  positions: Array<{ id: Uuid; x: number; y: number }>,
): Edit {
  const changes: Change[] = [];
  for (const { id, x, y } of positions) {
    const before = view.nodes[id];
    if (!before || (before.x === x && before.y === y)) continue;
    changes.push({ entity: 'node', id, viewId: view.id, before, after: { ...before, x, y } });
  }
  return { changes, affected: changes.map((c) => (c.after as ViewNode).elementId ?? c.id) };
}

export function resizeNode(view: ViewDoc, id: Uuid, bounds: { x: number; y: number; width: number; height: number }): Edit {
  const before = view.nodes[id];
  if (!before) return { changes: [], affected: [] };
  const after = { ...before, ...bounds };
  if (after.x === before.x && after.y === before.y && after.width === before.width && after.height === before.height) {
    return { changes: [], affected: [] };
  }
  return {
    changes: [{ entity: 'node', id, viewId: view.id, before, after }],
    affected: [before.elementId ?? id],
  };
}

/**
 * Убрать узлы с представления: элемент остаётся в модели. Вложенные узлы
 * и рёбра сервер убирает сам вместе с узлом, но отмена обязана вернуть
 * их явно — отсюда разная пометка в разные стороны.
 */
export function removeFromView(view: ViewDoc, ids: Uuid[]): Edit {
  const roots = ids.filter((id) => view.nodes[id] && !ids.some((other) => other !== id && isAncestor(view, other, id)));
  const nested = descendants(view, roots);
  const edges = attachedEdges(view, [...roots, ...nested.map((n) => n.id)]);
  const changes: Change[] = [
    ...edges.map((edge) => ({ entity: 'edge' as const, id: edge.id, viewId: view.id, before: edge, after: null, implicit: true })),
    ...[...nested].reverse().map((node) => ({
      entity: 'node' as const,
      id: node.id,
      viewId: view.id,
      before: node,
      after: null,
      implicit: true,
    })),
    ...roots.map((id) => ({ entity: 'node' as const, id, viewId: view.id, before: view.nodes[id]!, after: null })),
  ];
  return { changes, affected: [...roots, ...nested.map((n) => n.id)].map((id) => view.nodes[id]!.elementId ?? id) };
}

function isAncestor(view: ViewDoc, ancestor: Uuid, id: Uuid): boolean {
  let current = view.nodes[id]?.parentId;
  while (current) {
    if (current === ancestor) return true;
    current = view.nodes[current]?.parentId;
  }
  return false;
}

/** Следующая позиция в корне представления или в содержимом контейнера. */
function nextOrderIn(view: ViewDoc, parentId?: Uuid): number {
  return parentId
    ? nextOrderInEndpoint(view, parentId)
    : nextSortOrder(Object.values(view.nodes).filter((n) => !n.parentId).map((n) => n.sortOrder));
}

/** Размещение существующего элемента — узел в корне представления или в контейнере. */
export function placeElement(
  view: ViewDoc,
  elementId: Uuid,
  x: number,
  y: number,
  size = { width: 120, height: 55 },
  parentId?: Uuid,
): Edit & { nodeId: Uuid } {
  const id = uuidv7();
  const node: ViewNode = {
    id,
    archiId: newArchiId(),
    archiType: 'archimate:DiagramObject',
    kind: 'DIAGRAM_OBJECT',
    elementId,
    x: Math.round(x),
    y: Math.round(y),
    width: size.width,
    height: size.height,
    sortOrder: nextOrderIn(view, parentId),
  };
  if (parentId) node.parentId = parentId;
  return { nodeId: id, changes: [{ entity: 'node', id, viewId: view.id, before: null, after: node }], affected: [elementId] };
}

/** Группа или заметка — узел без элемента модели; подпись у группы, текст у заметки. */
export function placeAnnotation(
  view: ViewDoc,
  kind: 'GROUP' | 'NOTE',
  text: string,
  x: number,
  y: number,
  parentId?: Uuid,
): Edit & { nodeId: Uuid } {
  const id = uuidv7();
  const node: ViewNode = {
    id,
    archiId: newArchiId(),
    archiType: kind === 'GROUP' ? 'archimate:Group' : 'archimate:Note',
    kind,
    x: Math.round(x),
    y: Math.round(y),
    width: kind === 'GROUP' ? 400 : 185,
    height: kind === 'GROUP' ? 140 : 80,
    sortOrder: nextOrderIn(view, parentId),
  };
  if (parentId) node.parentId = parentId;
  if (kind === 'GROUP') node.label = text;
  else if (text) node.content = text;
  return { nodeId: id, changes: [{ entity: 'node', id, viewId: view.id, before: null, after: node }], affected: [id] };
}

/** Подпись группы или текст заметки. */
export function editNodeText(view: ViewDoc, nodeId: Uuid, patch: { label?: string; content?: string }): Edit {
  const before = view.nodes[nodeId];
  if (!before) return { changes: [], affected: [] };
  const after: ViewNode = { ...before };
  if (patch.label !== undefined) {
    if (patch.label) after.label = patch.label;
    else delete after.label;
  }
  if (patch.content !== undefined) {
    if (patch.content) after.content = patch.content;
    else delete after.content;
  }
  if ((after.label ?? '') === (before.label ?? '') && (after.content ?? '') === (before.content ?? '')) {
    return { changes: [], affected: [] };
  }
  return { changes: [{ entity: 'node', id: nodeId, viewId: view.id, before, after }], affected: [nodeId] };
}

/** Корневая папка слоя: новый элемент кладётся только в поддерево своего корня. */
export function layerRoot(doc: ModelDoc, layer: string): Folder | undefined {
  const folderType = (
    {
      BUSINESS: 'BUSINESS',
      APPLICATION: 'APPLICATION',
      TECHNOLOGY: 'TECHNOLOGY',
      PHYSICAL: 'TECHNOLOGY',
      MOTIVATION: 'MOTIVATION',
      STRATEGY: 'STRATEGY',
      IMPLEMENTATION: 'IMPLEMENTATION_MIGRATION',
    } as Record<string, string>
  )[layer] ?? 'OTHER';
  return Object.values(doc.folders).find((f) => !f.parentId && f.folderType === folderType);
}

/** Новый элемент в корне слоя и сразу узел на представлении. */
export function createAndPlace(
  doc: ModelDoc,
  view: ViewDoc,
  archiType: string,
  layer: string,
  name: string,
  x: number,
  y: number,
  size = { width: 120, height: 55 },
  parentId?: Uuid,
): (Edit & { elementId: Uuid }) | null {
  const folder = layerRoot(doc, layer);
  if (!folder) return null;
  const element: Element = {
    id: uuidv7(),
    folderId: folder.id,
    archiId: newArchiId(),
    archiType,
    layer,
    name,
    properties: [],
    supported: true,
    sortOrder: nextSortOrder(
      [...Object.values(doc.elements), ...Object.values(doc.folders).map((f) => ({ ...f, folderId: f.parentId }))]
        .filter((item) => item.folderId === folder.id)
        .map((item) => item.sortOrder),
    ),
  };
  const placed = placeElement(view, element.id, x, y, size, parentId);
  return {
    elementId: element.id,
    changes: [{ entity: 'element', id: element.id, before: null, after: element }, ...placed.changes],
    affected: [element.id],
  };
}

/** Правка имени, документации и свойств элемента или связи: меняется объект модели, а не узел. */
export function updateContent(
  doc: ModelDoc,
  id: Uuid,
  patch: { name?: string; documentation?: string; properties?: Property[] },
): Edit {
  const element = doc.elements[id];
  if (element) {
    const after = { ...element, ...patch };
    return { changes: [{ entity: 'element', id, before: element, after }], affected: [id] };
  }
  const relationship = doc.relationships[id];
  if (relationship) {
    const after = { ...relationship, ...patch };
    return { changes: [{ entity: 'relationship', id, before: relationship, after }], affected: [id] };
  }
  return { changes: [], affected: [] };
}

/** Удаление объектов модели из дерева: со связями и со всех загруженных представлений. */
export function deleteObjects(doc: ModelDoc, ids: Uuid[]): Edit {
  const elementIds = ids.filter((id) => doc.elements[id]);
  const relationships = new Map(
    [...ids.filter((id) => doc.relationships[id]).map((id) => doc.relationships[id]!), ...attachedRelationships(doc, ids)].map((r) => [r.id, r]),
  );
  const changes: Change[] = [];
  // Узлы и рёбра на загруженных представлениях сервер убирает вместе с объектом.
  for (const view of Object.values(doc.loadedViews)) {
    const nodes = Object.values(view.nodes).filter((n) => n.elementId && elementIds.includes(n.elementId));
    const nested = descendants(view, nodes.map((n) => n.id));
    const removedEdges = new Map<Uuid, Change>();
    for (const edge of attachedEdges(view, [...nodes, ...nested].map((n) => n.id))) {
      removedEdges.set(edge.id, { entity: 'edge', id: edge.id, viewId: view.id, before: edge, after: null, implicit: true });
    }
    for (const edge of Object.values(view.edges)) {
      if (edge.relationshipId && relationships.has(edge.relationshipId)) {
        removedEdges.set(edge.id, { entity: 'edge', id: edge.id, viewId: view.id, before: edge, after: null, implicit: true });
      }
    }
    changes.push(...removedEdges.values());
    for (const node of [...nested.reverse(), ...nodes]) {
      changes.push({ entity: 'node', id: node.id, viewId: view.id, before: node, after: null, implicit: true });
    }
  }
  for (const r of deletionOrder([...relationships.values()])) {
    changes.push({ entity: 'relationship', id: r.id, before: r, after: null });
  }
  for (const id of elementIds) {
    changes.push({ entity: 'element', id, before: doc.elements[id]!, after: null });
  }
  return { changes, affected: [...elementIds, ...relationships.keys()] };
}

/** Связь со связью удаляется раньше той, к которой она примыкает. */
function deletionOrder<T extends { id: Uuid; sourceId: Uuid; targetId: Uuid }>(items: T[]): T[] {
  const remaining = new Map(items.map((r) => [r.id, r]));
  const ordered: T[] = [];
  while (remaining.size > 0) {
    const free = [...remaining.values()].filter(
      (r) => ![...remaining.values()].some((other) => other.sourceId === r.id || other.targetId === r.id),
    );
    // Цикл связей невозможен в модели Archi; на всякий случай — без зависания.
    const batch = free.length > 0 ? free : [remaining.values().next().value!];
    for (const r of batch) {
      ordered.push(r);
      remaining.delete(r.id);
    }
  }
  return ordered;
}

/** Корневая папка, в поддереве которой лежит объект: структуру корней задаёт формат Archi. */
export function rootOf(doc: ModelDoc, id: Uuid): Uuid | undefined {
  let folderId: Uuid | undefined =
    doc.elements[id]?.folderId ?? doc.relationships[id]?.folderId ?? doc.views[id]?.folderId ?? (doc.folders[id] ? id : undefined);
  while (folderId && doc.folders[folderId]?.parentId) {
    folderId = doc.folders[folderId]!.parentId;
  }
  return folderId;
}

export function isSystemFolder(doc: ModelDoc, id: Uuid): boolean {
  const folder = doc.folders[id];
  return !!folder && !folder.parentId;
}

/** Имя папки, элемента или представления; связи в дереве не переименовываются. */
export function renameItem(doc: ModelDoc, id: Uuid, name: string): Edit {
  const folder = doc.folders[id];
  if (folder) return { changes: [{ entity: 'folder', id, before: folder, after: { ...folder, name } }], affected: [id] };
  const view = doc.views[id];
  if (view) return { changes: [{ entity: 'view', id, before: view, after: { ...view, name } }], affected: [id] };
  return updateContent(doc, id, { name });
}

/** Папка не может переехать в себя или в своё поддерево. */
function insideOf(doc: ModelDoc, folderId: Uuid, ancestor: Uuid): boolean {
  let current: Uuid | undefined = folderId;
  while (current) {
    if (current === ancestor) return true;
    current = doc.folders[current]?.parentId;
  }
  return false;
}

/**
 * Перенос в папку. Меняется только папка: позиция — в конце новой, соседи
 * не перенумеровываются, порядок в старой не трогается. Объект остаётся
 * в поддереве своего корня — иначе Archi переложит его сам при открытии.
 */
export function moveItems(doc: ModelDoc, ids: Uuid[], targetFolderId: Uuid): Edit | null {
  const targetRoot = rootOf(doc, targetFolderId);
  const movable = ids.filter((id) => !isSystemFolder(doc, id));
  if (movable.length === 0 || movable.some((id) => rootOf(doc, id) !== targetRoot)) return null;
  if (movable.some((id) => doc.folders[id] && insideOf(doc, targetFolderId, id))) return null;
  const siblings = [
    ...Object.values(doc.folders).filter((f) => f.parentId === targetFolderId),
    ...[...Object.values(doc.elements), ...Object.values(doc.relationships), ...Object.values(doc.views)].filter(
      (item) => item.folderId === targetFolderId,
    ),
  ].map((item) => item.sortOrder);
  let order = nextSortOrder(siblings);
  const changes: Change[] = [];
  for (const id of movable) {
    const sortOrder = order;
    const folder = doc.folders[id];
    if (folder) {
      if (folder.parentId === targetFolderId) continue;
      changes.push({ entity: 'folder', id, before: folder, after: { ...folder, parentId: targetFolderId, sortOrder } });
    } else if (doc.elements[id]) {
      const e = doc.elements[id]!;
      if (e.folderId === targetFolderId) continue;
      changes.push({ entity: 'element', id, before: e, after: { ...e, folderId: targetFolderId, sortOrder } });
    } else if (doc.relationships[id]) {
      const r = doc.relationships[id]!;
      if (r.folderId === targetFolderId) continue;
      changes.push({ entity: 'relationship', id, before: r, after: { ...r, folderId: targetFolderId, sortOrder } });
    } else if (doc.views[id]) {
      const v = doc.views[id]!;
      if (v.folderId === targetFolderId) continue;
      changes.push({ entity: 'view', id, before: v, after: { ...v, folderId: targetFolderId, sortOrder } });
    }
    order += 1000;
  }
  return { changes, affected: changes.map((c) => c.id) };
}

export function createFolder(doc: ModelDoc, parentId: Uuid, name: string): Edit & { folderId: Uuid } {
  const id = uuidv7();
  const siblings = [
    ...Object.values(doc.folders).filter((f) => f.parentId === parentId),
    ...[...Object.values(doc.elements), ...Object.values(doc.relationships), ...Object.values(doc.views)].filter(
      (item) => item.folderId === parentId,
    ),
  ].map((item) => item.sortOrder);
  const folder: Folder = { id, parentId, archiId: newArchiId(), name, sortOrder: nextSortOrder(siblings) };
  return { folderId: id, changes: [{ entity: 'folder', id, before: null, after: folder }], affected: [id] };
}

/** Пустые папки удаляются; с содержимым — нет, это каскад, от которого модель отказывается. */
export function deleteFolders(doc: ModelDoc, ids: Uuid[]): Edit {
  const changes: Change[] = ids.map((id) => ({ entity: 'folder' as const, id, before: doc.folders[id]!, after: null }));
  return { changes, affected: ids };
}

export function folderIsEmpty(doc: ModelDoc, id: Uuid): boolean {
  return (
    !Object.values(doc.folders).some((f) => f.parentId === id) &&
    ![...Object.values(doc.elements), ...Object.values(doc.relationships), ...Object.values(doc.views)].some(
      (item) => item.folderId === id,
    )
  );
}

/** Концепт модели, который изображает конец ребра: элемент узла или связь ребра. */
export function conceptOfEndpoint(view: ViewDoc, endpointId: Uuid): Uuid | undefined {
  return view.nodes[endpointId]?.elementId ?? view.edges[endpointId]?.relationshipId;
}

/** Тип концепта конца — для матрицы допустимых связей. */
export function conceptTypeOfEndpoint(doc: ModelDoc, view: ViewDoc, endpointId: Uuid): string | undefined {
  const concept = conceptOfEndpoint(view, endpointId);
  return concept ? (doc.elements[concept]?.archiType ?? doc.relationships[concept]?.archiType) : undefined;
}

/** Следующая позиция в содержимом конца: дети-узлы и исходящие рёбра делят одну нумерацию. */
function nextOrderInEndpoint(view: ViewDoc, endpointId: Uuid): number {
  return nextSortOrder([
    ...Object.values(view.nodes).filter((n) => n.parentId === endpointId).map((n) => n.sortOrder),
    ...Object.values(view.edges).filter((e) => e.sourceId === endpointId).map((e) => e.sortOrder),
  ]);
}

/** Ребро связи между концами на представлении — в конце содержимого источника. */
function edgeOf(view: ViewDoc, relationship: Relationship, sourceId: Uuid, targetId: Uuid): ViewEdge {
  return {
    id: uuidv7(),
    archiId: newArchiId(),
    archiType: 'archimate:Connection',
    relationshipId: relationship.id,
    sourceId,
    targetId,
    bendpoints: [],
    sortOrder: nextOrderInEndpoint(view, sourceId),
  };
}

/**
 * Нарисовать связь между концами на представлении. Связь того же типа между той же
 * парой уже есть — рисуется её ребро, дубль в модели не создаётся; есть и ребро —
 * правки нет.
 */
export function drawRelationship(
  doc: ModelDoc,
  view: ViewDoc,
  sourceEnd: Uuid,
  targetEnd: Uuid,
  archiType: string,
): (Edit & { relationshipId: Uuid; created: boolean }) | null {
  const source = conceptOfEndpoint(view, sourceEnd);
  const target = conceptOfEndpoint(view, targetEnd);
  const root = relationsRoot(doc);
  if (!source || !target || !root) return null;
  const existing = Object.values(doc.relationships).find(
    (r) => r.archiType === archiType && r.sourceId === source && r.targetId === target,
  );
  if (existing) {
    const drawn = Object.values(view.edges).some(
      (e) => e.relationshipId === existing.id && e.sourceId === sourceEnd && e.targetId === targetEnd,
    );
    if (drawn) return { relationshipId: existing.id, created: false, changes: [], affected: [existing.id] };
    const edge = edgeOf(view, existing, sourceEnd, targetEnd);
    return {
      relationshipId: existing.id,
      created: false,
      changes: [{ entity: 'edge', id: edge.id, viewId: view.id, before: null, after: edge }],
      affected: [existing.id],
    };
  }
  const relationship = newRelationship(doc, root.id, archiType, source, target);
  const edge = edgeOf(view, relationship, sourceEnd, targetEnd);
  return {
    relationshipId: relationship.id,
    created: true,
    changes: [
      { entity: 'relationship', id: relationship.id, before: null, after: relationship },
      { entity: 'edge', id: edge.id, viewId: view.id, before: null, after: edge },
    ],
    affected: [relationship.id, source, target],
  };
}

function relationsRoot(doc: ModelDoc): Folder | undefined {
  return Object.values(doc.folders).find((f) => !f.parentId && f.folderType === 'RELATIONS');
}

/** Новая связь в корне папки связей — в конце, соседи не перенумеровываются. */
function newRelationship(doc: ModelDoc, folderId: Uuid, archiType: string, sourceId: Uuid, targetId: Uuid): Relationship {
  return {
    id: uuidv7(),
    folderId,
    archiId: newArchiId(),
    archiType,
    sourceId,
    targetId,
    properties: [],
    supported: true,
    sortOrder: nextSortOrder(
      [...Object.values(doc.relationships), ...Object.values(doc.folders).map((f) => ({ ...f, folderId: f.parentId }))]
        .filter((item) => item.folderId === folderId)
        .map((item) => item.sortOrder),
    ),
  };
}

/** Узел, способный содержать другие: элемент или группа, но не заметка. */
export function isContainer(node: ViewNode): boolean {
  return node.kind === 'DIAGRAM_OBJECT' || node.kind === 'GROUP';
}

/**
 * Перенос узлов в контейнер или в корень. Координаты — относительно нового родителя;
 * в другом родителе узел встаёт в конец содержимого, дети едут вместе с ним.
 */
export function nestNodes(view: ViewDoc, moves: Array<{ id: Uuid; parentId?: Uuid; x: number; y: number }>): Edit {
  const changes: Change[] = [];
  let working = view;
  for (const { id, parentId, x, y } of moves) {
    const before = working.nodes[id];
    if (!before) continue;
    const sameParent = (before.parentId ?? undefined) === parentId;
    if (sameParent && before.x === x && before.y === y) continue;
    const after: ViewNode = { ...before, x, y };
    if (!sameParent) {
      after.parentId = parentId;
      if (!parentId) delete after.parentId;
      after.sortOrder = nextOrderIn(working, parentId);
    }
    changes.push({ entity: 'node', id, viewId: view.id, before, after });
    working = { ...working, nodes: { ...working.nodes, [id]: after } };
  }
  return { changes, affected: changes.map((c) => (c.after as ViewNode).elementId ?? c.id) };
}

/** Связи, которые считаются уже выражающими вложенность: с ними новая не нужна. */
const STRUCTURAL = [
  'archimate:CompositionRelationship',
  'archimate:AggregationRelationship',
  'archimate:AssignmentRelationship',
  'archimate:RealizationRelationship',
  'archimate:SpecializationRelationship',
];

/**
 * Подразумеваемая связь вложения от родителя к ребёнку. Её тип называет метамодель;
 * структурная связь между ними уже есть — новая не создаётся.
 */
export function impliedRelationship(doc: ModelDoc, parentElementId: Uuid, childElementId: Uuid, archiType: string): Edit {
  const root = relationsRoot(doc);
  const exists = Object.values(doc.relationships).some(
    (r) => r.sourceId === parentElementId && r.targetId === childElementId && STRUCTURAL.includes(r.archiType),
  );
  if (!root || exists) return { changes: [], affected: [] };
  const relationship = newRelationship(doc, root.id, archiType, parentElementId, childElementId);
  return {
    changes: [{ entity: 'relationship', id: relationship.id, before: null, after: relationship }],
    affected: [relationship.id, parentElementId, childElementId],
  };
}

/** Граф представления для авторазметки — как он сейчас в документе, с несохранёнными узлами. */
export function layoutRequest(doc: ModelDoc, view: ViewDoc, strategy: LayoutStrategy): AutoLayoutRequest {
  const nodes = Object.values(view.nodes)
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((n) => ({
      id: n.id,
      parentId: n.parentId,
      conceptType: n.elementId ? doc.elements[n.elementId]?.archiType : undefined,
      x: n.x,
      y: n.y,
      width: n.width,
      height: n.height,
    }));
  const edges = Object.values(view.edges).map((e) => ({ id: e.id, sourceId: e.sourceId, targetId: e.targetId }));
  return { strategy, nodes, edges };
}

/** Предложение раскладки — одна правка: новые координаты узлов и сброшенные точки перегиба. */
export function applyLayoutPatch(view: ViewDoc, patch: LayoutPatch): Edit {
  const changes: Change[] = [];
  for (const bounds of patch.nodes) {
    const before = view.nodes[bounds.id];
    if (!before || (before.x === bounds.x && before.y === bounds.y && before.width === bounds.width && before.height === bounds.height)) {
      continue;
    }
    changes.push({ entity: 'node', id: before.id, viewId: view.id, before, after: { ...before, ...bounds } });
  }
  for (const { id, bendpoints } of patch.edges) {
    const before = view.edges[id];
    if (!before || JSON.stringify(before.bendpoints) === JSON.stringify(bendpoints)) continue;
    changes.push({ entity: 'edge', id, viewId: view.id, before, after: { ...before, bendpoints } });
  }
  return {
    changes,
    affected: changes.map((c) =>
      c.entity === 'node' ? ((c.after as ViewNode).elementId ?? c.id) : ((c.after as ViewEdge).relationshipId ?? c.id),
    ),
  };
}

/** Пустой стиль и отсутствующий — одно и то же: действует токен слоя. */
function cleanStyle(style: Style): Style | undefined {
  const entries = Object.entries(style).filter(([, v]) => v !== undefined && v !== '');
  return entries.length > 0 ? (Object.fromEntries(entries) as Style) : undefined;
}

/**
 * Собственный стиль узлов и рёбер представления. Значение `undefined` в правке
 * снимает поле — объект возвращается к токену слоя.
 */
export function restyle(view: ViewDoc, nodeIds: Uuid[], edgeIds: Uuid[], patch: Partial<Record<keyof Style, unknown>>): Edit {
  const changes: Change[] = [];
  const next = (style: Style | undefined) => cleanStyle({ ...(style ?? {}), ...(patch as Style) });
  const same = (a?: Style, b?: Style) => JSON.stringify(cleanStyle(a ?? {}) ?? {}) === JSON.stringify(b ?? {});
  for (const id of nodeIds) {
    const before = view.nodes[id];
    if (!before) continue;
    const style = next(before.style);
    if (same(before.style, style)) continue;
    const after: ViewNode = { ...before, style };
    if (!style) delete after.style;
    changes.push({ entity: 'node', id, viewId: view.id, before, after });
  }
  for (const id of edgeIds) {
    const before = view.edges[id];
    if (!before) continue;
    const style = next(before.style);
    if (same(before.style, style)) continue;
    const after: ViewEdge = { ...before, style };
    if (!style) delete after.style;
    changes.push({ entity: 'edge', id, viewId: view.id, before, after });
  }
  return {
    changes,
    affected: changes.map((c) =>
      c.entity === 'node' ? ((c.after as ViewNode).elementId ?? c.id) : ((c.after as ViewEdge).relationshipId ?? c.id),
    ),
  };
}

/** Скопированное с представления: узлы со всем вложенным и рёбра между ними. */
export interface Clip {
  viewId: Uuid;
  nodes: ViewNode[];
  edges: ViewEdge[];
}

/** Что копировать: выбранные узлы, всё внутри них и рёбра, оба конца которых скопированы. */
export function copySelection(view: ViewDoc, nodeIds: Uuid[]): Clip | null {
  const roots = nodeIds.filter((id) => view.nodes[id]);
  if (roots.length === 0) return null;
  const ids = new Set([...roots, ...descendants(view, roots).map((n) => n.id)]);
  const nodes = Object.values(view.nodes).filter((n) => ids.has(n.id));
  const ends = new Set(ids);
  const edges: ViewEdge[] = [];
  let grew = true;
  while (grew) {
    grew = false;
    for (const edge of Object.values(view.edges)) {
      if (!ends.has(edge.id) && ends.has(edge.sourceId) && ends.has(edge.targetId)) {
        ends.add(edge.id);
        edges.push(edge);
        grew = true;
      }
    }
  }
  return { viewId: view.id, nodes, edges };
}

/**
 * Вставка как в Archi: элемент, которого на представлении нет, вставляется ссылкой на тот же
 * элемент; уже размещённый — копией элемента. Связь между ссылками переиспользуется,
 * связь с копией копируется. Вложенность сохраняется, верхние узлы смещаются на `offset`.
 */
export function pasteClip(
  doc: ModelDoc,
  view: ViewDoc,
  clip: Clip,
  offset: { x: number; y: number },
  copyName: (name: string) => string,
): Edit & { pasted: Uuid[] } {
  const changes: Change[] = [];
  const affected: Uuid[] = [];
  const pasted: Uuid[] = [];
  let working = view;
  let model = doc;
  const nodeMap = new Map<Uuid, Uuid>();
  const conceptMap = new Map<Uuid, Uuid>();
  const placedBefore = new Set(Object.values(view.nodes).map((n) => n.elementId).filter(Boolean));
  const inClip = new Set(clip.nodes.map((n) => n.id));
  const depth = (node: ViewNode) => {
    let d = 0;
    let parent = node.parentId;
    while (parent && inClip.has(parent)) {
      d++;
      parent = clip.nodes.find((n) => n.id === parent)?.parentId;
    }
    return d;
  };
  const ordered = [...clip.nodes].sort((a, b) => depth(a) - depth(b) || a.sortOrder - b.sortOrder);
  const push = (change: Change) => {
    changes.push(change);
    if (change.entity === 'node' || change.entity === 'edge') {
      working = applyToView(working, change);
    } else {
      model = applyChanges(model, [change]);
    }
  };

  for (const source of ordered) {
    let elementId = source.elementId;
    if (elementId && (placedBefore.has(elementId) || conceptMap.has(elementId))) {
      const original = model.elements[elementId];
      if (!original) continue;
      const copy: Element = {
        ...original,
        id: uuidv7(),
        archiId: newArchiId(),
        name: copyName(original.name),
        properties: original.properties.map((p) => ({ ...p })),
        sortOrder: nextSortOrder(
          [...Object.values(model.elements), ...Object.values(model.folders).map((f) => ({ ...f, folderId: f.parentId }))]
            .filter((item) => item.folderId === original.folderId)
            .map((item) => item.sortOrder),
        ),
      };
      push({ entity: 'element', id: copy.id, before: null, after: copy });
      conceptMap.set(original.id, copy.id);
      affected.push(copy.id);
      elementId = copy.id;
    }
    const parentId = source.parentId && nodeMap.get(source.parentId);
    const node: ViewNode = {
      ...source,
      id: uuidv7(),
      archiId: newArchiId(),
      x: parentId ? source.x : source.x + offset.x,
      y: parentId ? source.y : source.y + offset.y,
      sortOrder: nextOrderIn(working, parentId),
    };
    if (parentId) node.parentId = parentId;
    else delete node.parentId;
    if (elementId) node.elementId = elementId;
    nodeMap.set(source.id, node.id);
    push({ entity: 'node', id: node.id, viewId: view.id, before: null, after: node });
    pasted.push(elementId ?? node.id);
    affected.push(elementId ?? node.id);
  }

  const edgeMap = new Map<Uuid, Uuid>();
  const endOf = (id: Uuid) => nodeMap.get(id) ?? edgeMap.get(id);
  let remaining = [...clip.edges];
  while (remaining.length > 0) {
    const ready = remaining.filter((e) => endOf(e.sourceId) && endOf(e.targetId));
    if (ready.length === 0) break;
    remaining = remaining.filter((e) => !ready.includes(e));
    for (const edge of ready) {
      const relationship = edge.relationshipId ? model.relationships[edge.relationshipId] : undefined;
      if (!relationship) continue;
      const sourceConcept = conceptMap.get(relationship.sourceId) ?? relationship.sourceId;
      const targetConcept = conceptMap.get(relationship.targetId) ?? relationship.targetId;
      let relationshipId = relationship.id;
      if (sourceConcept !== relationship.sourceId || targetConcept !== relationship.targetId) {
        const copy: Relationship = {
          ...relationship,
          id: uuidv7(),
          archiId: newArchiId(),
          sourceId: sourceConcept,
          targetId: targetConcept,
          properties: relationship.properties.map((p) => ({ ...p })),
          sortOrder: nextSortOrder(
            Object.values(model.relationships).filter((r) => r.folderId === relationship.folderId).map((r) => r.sortOrder),
          ),
        };
        delete copy.edgeId;
        push({ entity: 'relationship', id: copy.id, before: null, after: copy });
        conceptMap.set(relationship.id, copy.id);
        affected.push(copy.id);
        relationshipId = copy.id;
      }
      const sourceId = endOf(edge.sourceId)!;
      const targetId = endOf(edge.targetId)!;
      const copy: ViewEdge = {
        ...edge,
        id: uuidv7(),
        archiId: newArchiId(),
        relationshipId,
        sourceId,
        targetId,
        bendpoints: edge.bendpoints.map((b) => ({ ...b })),
        sortOrder: nextOrderInEndpoint(working, sourceId),
      };
      edgeMap.set(edge.id, copy.id);
      push({ entity: 'edge', id: copy.id, viewId: view.id, before: null, after: copy });
    }
  }
  return { changes, affected, pasted };
}

function applyToView(view: ViewDoc, change: Change): ViewDoc {
  if (change.entity === 'node') {
    return { ...view, nodes: { ...view.nodes, [change.id]: change.after as ViewNode } };
  }
  if (change.entity === 'edge') {
    return { ...view, edges: { ...view.edges, [change.id]: change.after as ViewEdge } };
  }
  return view;
}

export type AlignMode = 'left' | 'center' | 'right' | 'top' | 'middle' | 'bottom' | 'width' | 'height';

/**
 * Выравнивание узлов по опорному — последнему выделенному, как в Archi. Считается
 * в координатах холста, пишется относительно своего родителя у каждого узла.
 */
export function alignNodes(
  view: ViewDoc,
  ids: Uuid[],
  mode: AlignMode,
  sizeOf: (node: ViewNode) => { width: number; height: number },
  absoluteOf: (id: Uuid) => { x: number; y: number },
): Edit {
  const nodes = ids.map((id) => view.nodes[id]).filter((n): n is ViewNode => !!n);
  const anchor = nodes[nodes.length - 1];
  if (!anchor || nodes.length < 2) return { changes: [], affected: [] };
  const a = { ...absoluteOf(anchor.id), ...sizeOf(anchor) };
  const changes: Change[] = [];
  for (const node of nodes.slice(0, -1)) {
    const at = absoluteOf(node.id);
    const size = sizeOf(node);
    let { x, y, width, height } = { x: at.x, y: at.y, width: node.width, height: node.height };
    switch (mode) {
      case 'left': x = a.x; break;
      case 'center': x = a.x + a.width / 2 - size.width / 2; break;
      case 'right': x = a.x + a.width - size.width; break;
      case 'top': y = a.y; break;
      case 'middle': y = a.y + a.height / 2 - size.height / 2; break;
      case 'bottom': y = a.y + a.height - size.height; break;
      case 'width': width = a.width; break;
      case 'height': height = a.height; break;
    }
    const after = {
      ...node,
      x: Math.round(node.x + x - at.x),
      y: Math.round(node.y + y - at.y),
      width,
      height,
    };
    if (after.x === node.x && after.y === node.y && after.width === node.width && after.height === node.height) continue;
    changes.push({ entity: 'node', id: node.id, viewId: view.id, before: node, after });
  }
  return { changes, affected: changes.map((c) => (c.after as ViewNode).elementId ?? c.id) };
}

/** Точки перегиба ребра — уже в формате Archi: смещения от центров концов. */
export function setBendpoints(view: ViewDoc, edgeId: Uuid, bendpoints: Bendpoint[]): Edit {
  const before = view.edges[edgeId];
  if (!before || JSON.stringify(before.bendpoints) === JSON.stringify(bendpoints)) return { changes: [], affected: [] };
  return {
    changes: [{ entity: 'edge', id: edgeId, viewId: view.id, before, after: { ...before, bendpoints } }],
    affected: [before.relationshipId ?? edgeId],
  };
}
