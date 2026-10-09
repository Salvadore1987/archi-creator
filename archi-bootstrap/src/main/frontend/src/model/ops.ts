import type { Element, Folder, Property, Uuid, ViewNode } from '../api/types';
import { attachedEdges, descendants } from '../canvas/flowModel';
import { attachedRelationships, type Change, type ModelDoc, type ViewDoc } from './doc';
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

/** Размещение существующего элемента — узел в корне представления. */
export function placeElement(view: ViewDoc, elementId: Uuid, x: number, y: number, size = { width: 120, height: 55 }): Edit & { nodeId: Uuid } {
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
    sortOrder: nextSortOrder(Object.values(view.nodes).filter((n) => !n.parentId).map((n) => n.sortOrder)),
  };
  return { nodeId: id, changes: [{ entity: 'node', id, viewId: view.id, before: null, after: node }], affected: [elementId] };
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
  const placed = placeElement(view, element.id, x, y);
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
