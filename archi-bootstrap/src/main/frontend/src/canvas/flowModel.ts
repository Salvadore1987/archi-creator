import type { Edge, Node } from '@xyflow/react';
import type { Uuid, ViewEdge, ViewNode } from '../api/types';
import type { ModelDoc, ViewDoc } from '../model/doc';
import { effectiveSize } from './shapes';

export type ArchiNodeData = { viewId: Uuid };
export type ArchiEdgeData = { viewId: Uuid };
export type ArchiFlowNode = Node<ArchiNodeData, 'archi'>;
export type ArchiFlowEdge = Edge<ArchiEdgeData, 'archi'>;

/** Неподдержанный объект: двигается и выделяется, но не правится по существу. */
export function isOpaque(doc: ModelDoc, node: ViewNode): boolean {
  if (node.kind === 'OTHER') return true;
  if (node.kind !== 'DIAGRAM_OBJECT') return false;
  const element = node.elementId ? doc.elements[node.elementId] : undefined;
  return !element || !element.supported;
}

function depthOf(view: ViewDoc, node: ViewNode): number {
  let depth = 0;
  let parent = node.parentId;
  while (parent && view.nodes[parent]) {
    depth++;
    parent = view.nodes[parent]!.parentId;
  }
  return depth;
}

/** Выделен ли узел: по элементу модели, а у группы и заметки — по самому узлу. */
export function nodeSelected(node: ViewNode, selected: ReadonlySet<Uuid>): boolean {
  return selected.has(node.id) || (!!node.elementId && selected.has(node.elementId));
}

export function edgeSelected(edge: ViewEdge, selected: ReadonlySet<Uuid>): boolean {
  return selected.has(edge.id) || (!!edge.relationshipId && selected.has(edge.relationshipId));
}

/**
 * Узлы React Flow: родитель раньше детей, координаты — относительно родителя,
 * как в файле. Порядок внутри уровня — порядок файла: он же порядок отрисовки.
 */
export function toFlowNodes(
  view: ViewDoc,
  selected: ReadonlySet<Uuid>,
  editable: boolean,
): ArchiFlowNode[] {
  const ordered = Object.values(view.nodes)
    .map((node) => ({ node, depth: depthOf(view, node) }))
    .sort((a, b) => a.depth - b.depth || a.node.sortOrder - b.node.sortOrder);
  return ordered.map(({ node, depth }) => {
    const size = effectiveSize(node.archiType, node.width, node.height);
    return {
      id: node.id,
      type: 'archi',
      position: { x: node.x, y: node.y },
      parentId: node.parentId && view.nodes[node.parentId] ? node.parentId : undefined,
      data: { viewId: view.id },
      width: size.width,
      height: size.height,
      selected: nodeSelected(node, selected),
      draggable: editable,
      connectable: false,
      zIndex: depth,
    };
  });
}

/**
 * Рёбра React Flow. Конец ребра может быть другим ребром (связь со связью);
 * React Flow такого не умеет, поэтому ему отдаётся ближайший узел, а точку
 * на середине ребра-конца считает сама отрисовка ребра.
 */
export function toFlowEdges(view: ViewDoc, selected: ReadonlySet<Uuid>): ArchiFlowEdge[] {
  const nodeOf = (endpoint: Uuid, guard = 0): Uuid | undefined => {
    if (view.nodes[endpoint]) return endpoint;
    const edge = view.edges[endpoint];
    return edge && guard < 16 ? nodeOf(edge.sourceId, guard + 1) : undefined;
  };
  const result: ArchiFlowEdge[] = [];
  for (const edge of Object.values(view.edges).sort((a, b) => a.sortOrder - b.sortOrder)) {
    const source = nodeOf(edge.sourceId);
    const target = nodeOf(edge.targetId);
    if (!source || !target) continue;
    result.push({
      id: edge.id,
      type: 'archi',
      source,
      target,
      data: { viewId: view.id },
      selected: edgeSelected(edge, selected),
      zIndex: 1000,
    });
  }
  return result;
}

/** Абсолютная позиция узла: сумма смещений по цепочке родителей. */
export function absolutePosition(view: ViewDoc, nodeId: Uuid): { x: number; y: number } {
  let x = 0;
  let y = 0;
  let current: ViewNode | undefined = view.nodes[nodeId];
  while (current) {
    x += current.x;
    y += current.y;
    current = current.parentId ? view.nodes[current.parentId] : undefined;
  }
  return { x, y };
}

/** Узлы под удаляемыми — вглубь, чтобы отмена вернула их вместе с родителем. */
export function descendants(view: ViewDoc, ids: Iterable<Uuid>): ViewNode[] {
  const result: ViewNode[] = [];
  const pending = [...ids];
  while (pending.length > 0) {
    const parent = pending.pop()!;
    for (const node of Object.values(view.nodes)) {
      if (node.parentId === parent) {
        result.push(node);
        pending.push(node.id);
      }
    }
  }
  return result;
}

/** Рёбра, касающиеся узлов или рёбер из набора, — транзитивно через рёбра-концы. */
export function attachedEdges(view: ViewDoc, endpointIds: Iterable<Uuid>): ViewEdge[] {
  const ends = new Set(endpointIds);
  const found: ViewEdge[] = [];
  let grew = true;
  while (grew) {
    grew = false;
    for (const edge of Object.values(view.edges)) {
      if (!ends.has(edge.id) && (ends.has(edge.sourceId) || ends.has(edge.targetId))) {
        ends.add(edge.id);
        found.push(edge);
        grew = true;
      }
    }
  }
  return found;
}
