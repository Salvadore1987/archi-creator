import { produce, type Draft } from 'immer';
import type {
  Element,
  Folder,
  ModelSummary,
  ModelTree,
  Property,
  Relationship,
  Uuid,
  ViewEdge,
  ViewNode,
  ViewPayload,
  ViewSummary,
} from '../api/types';

/** Загруженное представление: шапка и узлы с рёбрами по идентификатору. */
export interface ViewDoc {
  id: Uuid;
  archiType: string;
  editable: boolean;
  viewpoint?: string;
  documentation?: string;
  properties: Property[];
  nodes: Record<Uuid, ViewNode>;
  edges: Record<Uuid, ViewEdge>;
}

/**
 * Документ модели в браузере. Правки меняют его сразу, а на сервер уходят
 * при сохранении. Представления подгружаются по мере открытия; для остальных
 * известно только, какие элементы и связи на них размещены.
 */
export interface ModelDoc {
  model: ModelSummary;
  folders: Record<Uuid, Folder>;
  elements: Record<Uuid, Element>;
  relationships: Record<Uuid, Relationship>;
  views: Record<Uuid, ViewSummary>;
  placements: Record<Uuid, { elementIds: Uuid[]; relationshipIds: Uuid[] }>;
  loadedViews: Record<Uuid, ViewDoc>;
}

export type Entity = 'folder' | 'element' | 'relationship' | 'view' | 'node' | 'edge';

interface EntityValue {
  folder: Folder;
  element: Element;
  relationship: Relationship;
  view: ViewSummary;
  node: ViewNode;
  edge: ViewEdge;
}

/**
 * Обратимое изменение одной сущности: было → стало. `null` слева — создание,
 * справа — удаление. `implicit` — сервер делает это сам как следствие соседней
 * команды (узлы элемента уходят вместе с ним), отдельной команды не нужно;
 * `inverseImplicit` — то же для обратного изменения.
 */
export type Change = {
  [E in Entity]: {
    entity: E;
    id: Uuid;
    viewId?: Uuid;
    before: EntityValue[E] | null;
    after: EntityValue[E] | null;
    implicit?: boolean;
    inverseImplicit?: boolean;
  };
}[Entity];

export function fromTree(tree: ModelTree): ModelDoc {
  const byId = <T extends { id: Uuid }>(items: T[]) => Object.fromEntries(items.map((item) => [item.id, item]));
  return {
    model: tree.model,
    folders: byId(tree.folders),
    elements: byId(tree.elements.map((e) => ({ ...e, properties: e.properties ?? [] }))),
    relationships: byId(tree.relationships.map((r) => ({ ...r, properties: r.properties ?? [] }))),
    views: byId(tree.views),
    placements: Object.fromEntries(
      (tree.placements ?? []).map((p) => [p.viewId, { elementIds: p.elementIds, relationshipIds: p.relationshipIds }]),
    ),
    loadedViews: {},
  };
}

export function viewDocOf(payload: ViewPayload): ViewDoc {
  return {
    id: payload.id,
    archiType: payload.archiType,
    editable: payload.editable,
    viewpoint: payload.viewpoint,
    documentation: payload.documentation,
    properties: payload.properties ?? [],
    nodes: Object.fromEntries(payload.nodes.map((n) => [n.id, n])),
    edges: Object.fromEntries(payload.edges.map((e) => [e.id, { ...e, bendpoints: e.bendpoints ?? [] }])),
  };
}

export function inverse(change: Change): Change {
  return {
    ...change,
    before: change.after,
    after: change.before,
    implicit: change.inverseImplicit,
    inverseImplicit: change.implicit,
  } as Change;
}

export function applyChanges(doc: ModelDoc, changes: Change[]): ModelDoc {
  return produce(doc, (draft) => {
    for (const change of changes) {
      applyOne(draft, change);
    }
  });
}

function applyOne(draft: Draft<ModelDoc>, change: Change): void {
  const target = tableOf(draft, change);
  if (!target) {
    return;
  }
  if (change.after === null) {
    delete target[change.id];
  } else {
    target[change.id] = change.after as never;
  }
}

function tableOf(draft: Draft<ModelDoc>, change: Change): Record<Uuid, unknown> | undefined {
  switch (change.entity) {
    case 'folder':
      return draft.folders;
    case 'element':
      return draft.elements;
    case 'relationship':
      return draft.relationships;
    case 'view':
      return draft.views;
    case 'node':
      return change.viewId ? draft.loadedViews[change.viewId]?.nodes : undefined;
    case 'edge':
      return change.viewId ? draft.loadedViews[change.viewId]?.edges : undefined;
  }
}

/**
 * Где размещён каждый элемент и каждая связь. Загруженное представление
 * отвечает своими узлами (там уже учтены несохранённые правки), прочие —
 * тем, что сообщил сервер при открытии модели.
 */
export function placementIndex(doc: ModelDoc): Map<Uuid, Uuid[]> {
  const index = new Map<Uuid, Uuid[]>();
  const add = (objectId: Uuid, viewId: Uuid) => {
    const views = index.get(objectId);
    if (!views) {
      index.set(objectId, [viewId]);
    } else if (!views.includes(viewId)) {
      views.push(viewId);
    }
  };
  for (const viewId of Object.keys(doc.views)) {
    const loaded = doc.loadedViews[viewId];
    if (loaded) {
      for (const node of Object.values(loaded.nodes)) {
        if (node.elementId) add(node.elementId, viewId);
      }
      for (const edge of Object.values(loaded.edges)) {
        if (edge.relationshipId) add(edge.relationshipId, viewId);
      }
    } else {
      const known = doc.placements[viewId];
      known?.elementIds.forEach((id) => add(id, viewId));
      known?.relationshipIds.forEach((id) => add(id, viewId));
    }
  }
  return index;
}

/** Связи, примыкающие к объектам, включая связи со связями, — транзитивно. */
export function attachedRelationships(doc: ModelDoc, objectIds: Iterable<Uuid>): Relationship[] {
  const pending = [...objectIds];
  const seen = new Set<Uuid>(pending);
  const found: Relationship[] = [];
  const all = Object.values(doc.relationships);
  while (pending.length > 0) {
    const id = pending.pop()!;
    for (const relationship of all) {
      if ((relationship.sourceId === id || relationship.targetId === id) && !seen.has(relationship.id)) {
        seen.add(relationship.id);
        found.push(relationship);
        pending.push(relationship.id);
      }
    }
  }
  return found;
}
