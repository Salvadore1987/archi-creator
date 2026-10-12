import reference from '../../e2e/fixtures/reference-model.json';
import type { Finding, ModelTree, ViewPayload } from '../api/types';
import { fromTree, placementIndex, type ModelDoc } from '../model/doc';
import type { TreeContext } from './buildTree';

/** Эталонная модель банка — 402 элемента, 368 связей, 12 представлений. */
export const referenceTree = reference.tree as unknown as ModelTree;
export const referenceViews = reference.views as unknown as Record<string, ViewPayload>;
export const referenceFindings = reference.findings as unknown as Finding[];

/** Модель, размноженная до `factor` копий элементов, — для замеров на объёме NFR. */
export function scaledDoc(factor: number): ModelDoc {
  const doc = fromTree(referenceTree);
  if (factor <= 1) return doc;
  const elements = { ...doc.elements };
  for (let copy = 1; copy < factor; copy++) {
    for (const element of Object.values(doc.elements)) {
      const id = `${element.id}-${copy}`;
      elements[id] = { ...element, id, archiId: `${element.archiId}-${copy}`, name: `${element.name} ${copy}` };
    }
  }
  return { ...doc, elements };
}

export function contextFor(doc: ModelDoc, overrides: Partial<TreeContext> = {}): TreeContext {
  return {
    mode: 'folders',
    query: '',
    filter: 'all',
    expanded: new Set(),
    limits: new Map(),
    onView: new Set(),
    placements: placementIndex(doc),
    withFindings: new Set(),
    relationshipLabel: () => '',
    ...overrides,
  };
}
