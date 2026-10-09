import { describe, expect, it } from 'vitest';
import { fromTree } from '../model/doc';
import { referenceTree } from '../tree/fixture';
import { impactOf, relationsOf } from './impact';

const doc = fromTree(referenceTree);
const iabs = Object.values(doc.elements).find((e) => e.name === 'IABS')!;

describe('UI-014: карточка элемента на реальных данных', () => {
  it('IABS участвует в 60 связях', () => {
    expect(relationsOf(doc, iabs.id)).toHaveLength(60);
  });

  it('анализ влияния IABS: 12 напрямую, 12 вторым уровнем, 44 вложенных', () => {
    const impact = impactOf(doc, iabs.id);
    expect({ direct: impact.direct.length, second: impact.secondLevel, nested: impact.nested }).toEqual({
      direct: 12,
      second: 12,
      nested: 44,
    });
  });
});
