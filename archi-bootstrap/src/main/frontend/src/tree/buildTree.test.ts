import { describe, expect, it } from 'vitest';
import { BRANCH_LIMIT, ancestorsOf, buildIndex, buildRows, folderPath } from './buildTree';
import { contextFor, scaledDoc } from './fixture';

const doc = scaledDoc(1);
const index = buildIndex(doc);
const rootRows = () => buildRows(doc, index, contextFor(doc)).rows;

describe('UI-012: дерево модели повторяет папки файла', () => {
  it('девять корневых папок в порядке файла', () => {
    const roots = rootRows().filter((r) => r.kind === 'folder' && r.root);
    expect(roots).toHaveLength(9);
    expect(roots.map((r) => (r as { name: string }).name)).toEqual(
      Object.values(doc.folders)
        .filter((f) => !f.parentId)
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((f) => f.name),
    );
  });

  it('счётчик ветви рекурсивный: Application — 242, Relations — 368', () => {
    const counts = Object.fromEntries(
      rootRows()
        .filter((r) => r.kind === 'folder')
        .map((r) => [doc.folders[r.id]!.folderType, r.kind === 'folder' ? r.count : 0]),
    );
    expect(counts.APPLICATION).toBe(242);
    expect(counts.RELATIONS).toBe(368);
    expect(counts.DIAGRAMS).toBe(12);
  });

  it('поиск раскрывает ветви с совпадениями и прячет остальные', () => {
    const result = buildRows(doc, index, contextFor(doc, { query: 'IABS' }));
    const elements = result.rows.filter((r) => r.kind === 'element');
    expect(elements.length).toBeGreaterThan(0);
    expect(elements.every((r) => r.kind === 'element' && r.name.toLowerCase().includes('iabs'))).toBe(true);
    expect(result.rows.filter((r) => r.kind === 'folder').every((r) => r.kind === 'folder' && r.open && r.count > 0)).toBe(true);
    const first = elements[0]!;
    expect(first.kind === 'element' && first.match).toBeTruthy();
  });

  it('фильтр «Не размещены» — элементы без единого представления', () => {
    const result = buildRows(doc, index, contextFor(doc, { filter: 'unplaced', mode: 'flat' }));
    expect(result.shown).toBe(45);
  });

  it('режим «Типы» — группы по убыванию численности', () => {
    const groups = buildRows(doc, index, contextFor(doc, { mode: 'types' })).rows.filter((r) => r.kind === 'group');
    expect((groups[0] as { name: string }).name).toBe('Компонент приложения');
    expect(groups[0]!.kind === 'group' && groups[0]!.count).toBe(242);
  });

  it('путь папки и предки объекта', () => {
    const element = Object.values(doc.elements).find((e) => doc.folders[e.folderId]?.parentId)!;
    const chain = ancestorsOf(index, element.id);
    expect(chain[chain.length - 1]).toBe(element.folderId);
    expect(folderPath(doc, element.folderId).split(' / ')).toHaveLength(chain.length);
  });
});

describe('UI-013: в ветви не больше 50 строк', () => {
  it('остальное — по «показать ещё»', () => {
    const application = Object.values(doc.folders).find((f) => f.folderType === 'APPLICATION')!;
    const big = [...index.children.entries()].find(([, items]) => items.filter((i) => i.kind !== 'folder').length > BRANCH_LIMIT);
    const expanded = new Set([application.id, ...(big ? [big[0], ...ancestorsOf(index, big[0])] : [])]);
    const rows = buildRows(doc, index, contextFor(doc, { expanded })).rows;
    if (big) {
      const more = rows.find((r) => r.kind === 'more' && r.id === big[0]);
      expect(more).toBeDefined();
      const withMore = buildRows(doc, index, contextFor(doc, { expanded, limits: new Map([[big[0], 1000]]) })).rows;
      expect(withMore.find((r) => r.kind === 'more' && r.id === big[0])).toBeUndefined();
    }
    const flat = buildRows(doc, index, contextFor(doc, { mode: 'flat' })).rows;
    expect(flat.filter((r) => r.kind === 'element')).toHaveLength(120);
  });
});
