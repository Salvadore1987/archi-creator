import { describe, expect, it } from 'vitest';
import { buildIndex, buildRows } from '../src/tree/buildTree';
import { contextFor, scaledDoc } from '../src/tree/fixture';
import { measure } from './measure';

/**
 * Живой поиск на 2 000 элементах обязан отвечать за 100 мс. Замер — вся
 * работа на нажатие клавиши: отбор, раскрытие ветвей, пересчёт счётчиков.
 * Порог проверяется, а не только печатается: регрессия роняет сборку.
 */
describe('UI-013: отклик поиска по дереву на 2 000 элементов', () => {
  const doc = scaledDoc(5);
  const index = buildIndex(doc);

  it('модель замера не меньше 2 000 элементов', () => {
    expect(Object.keys(doc.elements).length).toBeGreaterThanOrEqual(2000);
  });

  for (const query of ['i', 'iabs', 'сервис', 'нет такого']) {
    it(`запрос «${query}» — медиана меньше 100 мс`, () => {
      const result = measure(20, () => buildRows(doc, index, contextFor(doc, { query })));
      console.info(`tree-search «${query}»: median ${result.median.toFixed(2)} ms, max ${result.max.toFixed(2)} ms`);
      expect(result.median).toBeLessThan(100);
    });
  }
});
