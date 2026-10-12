import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/**
 * Матрица допустимых связей для подменного сервера — тот же файл, что читает
 * сервер: расхождение копии с оригиналом сделало бы E2E проверкой копии.
 */
const MATRIX = fileURLToPath(
  new URL(
    '../../../../../../archi-modeling/archi-modeling-domain/src/main/resources/uz/salvadore/hamkorbank/archi/modeling/domain/metamodel/archimate-3.2-relationships.xml',
    import.meta.url,
  ),
);

const LETTERS: Record<string, string> = {
  a: 'ACCESS',
  c: 'COMPOSITION',
  f: 'FLOW',
  g: 'AGGREGATION',
  i: 'ASSIGNMENT',
  n: 'INFLUENCE',
  o: 'ASSOCIATION',
  r: 'REALIZATION',
  s: 'SPECIALIZATION',
  t: 'TRIGGERING',
  v: 'SERVING',
};

/** Порядок предложения — как у сервера: частые в ландшафте впереди. */
const PREFERENCE = [
  'SERVING',
  'REALIZATION',
  'ASSIGNMENT',
  'COMPOSITION',
  'AGGREGATION',
  'FLOW',
  'TRIGGERING',
  'ACCESS',
  'INFLUENCE',
  'SPECIALIZATION',
  'ASSOCIATION',
];

const ARCHI_TYPE: Record<string, string> = {
  ACCESS: 'archimate:AccessRelationship',
  COMPOSITION: 'archimate:CompositionRelationship',
  FLOW: 'archimate:FlowRelationship',
  AGGREGATION: 'archimate:AggregationRelationship',
  ASSIGNMENT: 'archimate:AssignmentRelationship',
  INFLUENCE: 'archimate:InfluenceRelationship',
  ASSOCIATION: 'archimate:AssociationRelationship',
  REALIZATION: 'archimate:RealizationRelationship',
  SPECIALIZATION: 'archimate:SpecializationRelationship',
  TRIGGERING: 'archimate:TriggeringRelationship',
  SERVING: 'archimate:ServingRelationship',
};

let table: Map<string, Map<string, string[]>> | undefined;

function load(): Map<string, Map<string, string[]>> {
  if (table) return table;
  table = new Map();
  const xml = readFileSync(MATRIX, 'utf8');
  for (const block of xml.matchAll(/<source concept="([^"]+)">([\s\S]*?)<\/source>/g)) {
    const targets = new Map<string, string[]>();
    for (const target of block[2]!.matchAll(/<target concept="([^"]+)" relations="([^"]*)"/g)) {
      targets.set(target[1]!, [...target[2]!].map((letter) => LETTERS[letter]!).filter(Boolean));
    }
    table.set(block[1]!, targets);
  }
  return table;
}

/** Концепт матрицы: имя типа без префикса, связь как конец — «Relationship». */
function concept(archiType: string): string {
  const name = archiType.replace(/^archimate:/, '');
  return name.endsWith('Relationship') ? 'Relationship' : name;
}

export function relationTypes(source: string, target: string) {
  const permitted = load().get(concept(source))?.get(concept(target)) ?? [];
  const ordered = [...permitted].sort((a, b) => PREFERENCE.indexOf(a) - PREFERENCE.indexOf(b));
  const nesting = ['COMPOSITION', 'ASSIGNMENT'].find((t) => permitted.includes(t));
  return ordered.map((type, i) => ({ archiType: ARCHI_TYPE[type]!, type, byDefault: i === 0, nesting: type === nesting }));
}
