import type { Element, Relationship, Uuid } from '../api/types';
import type { ModelDoc } from '../model/doc';

/** Связи, по которым отказ распространяется: кого элемент обслуживает, реализует, запускает, кому передаёт и к чему обращается. */
const PROPAGATING = new Set([
  'archimate:ServingRelationship',
  'archimate:RealizationRelationship',
  'archimate:TriggeringRelationship',
  'archimate:FlowRelationship',
  'archimate:AccessRelationship',
]);

const CONTAINING = new Set(['archimate:CompositionRelationship', 'archimate:AggregationRelationship']);

export interface Impact {
  direct: Element[];
  secondLevel: number;
  nested: number;
}

function outgoing(doc: ModelDoc, id: Uuid, types: Set<string>): Uuid[] {
  return Object.values(doc.relationships)
    .filter((r) => r.sourceId === id && types.has(r.archiType))
    .map((r) => r.targetId)
    .filter((target) => doc.elements[target]);
}

/**
 * Анализ влияния: что затронет отказ элемента напрямую, что — через
 * затронутых, и сколько элементов он включает составом и агрегацией.
 */
export function impactOf(doc: ModelDoc, elementId: Uuid): Impact {
  const direct = [...new Set(outgoing(doc, elementId, PROPAGATING))].filter((id) => id !== elementId);
  const reached = new Set([elementId, ...direct]);
  const second = new Set<Uuid>();
  for (const id of direct) {
    for (const next of outgoing(doc, id, PROPAGATING)) {
      if (!reached.has(next)) second.add(next);
    }
  }
  const nested = new Set<Uuid>();
  const pending = [elementId];
  while (pending.length > 0) {
    for (const child of outgoing(doc, pending.pop()!, CONTAINING)) {
      if (child !== elementId && !nested.has(child)) {
        nested.add(child);
        pending.push(child);
      }
    }
  }
  return { direct: direct.map((id) => doc.elements[id]!), secondLevel: second.size, nested: nested.size };
}

export interface RelationEntry {
  relationship: Relationship;
  outgoing: boolean;
  otherId: Uuid;
}

/** Все связи объекта с направлением — сначала исходящие, в порядке файла. */
export function relationsOf(doc: ModelDoc, id: Uuid): RelationEntry[] {
  const all = Object.values(doc.relationships).sort((a, b) => a.sortOrder - b.sortOrder);
  return [
    ...all.filter((r) => r.sourceId === id).map((r) => ({ relationship: r, outgoing: true, otherId: r.targetId })),
    ...all.filter((r) => r.targetId === id && r.sourceId !== id).map((r) => ({ relationship: r, outgoing: false, otherId: r.sourceId })),
  ];
}
