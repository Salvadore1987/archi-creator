import { describe, expect, it } from 'vitest';
import { referenceTree, referenceViews } from '../tree/fixture';
import { commandsFor } from './commands';
import { applyChanges, fromTree, viewDocOf } from './doc';
import { invert } from './history';
import { createFolder, deleteObjects, isSystemFolder, moveItems, placeElement, removeFromView, rootOf } from './ops';

function docWithViews() {
  const doc = fromTree(referenceTree);
  for (const payload of Object.values(referenceViews)) {
    doc.loadedViews[payload.id] = viewDocOf(payload);
  }
  return doc;
}

const op = (changes: ReturnType<typeof removeFromView>['changes']) => ({ id: 'x', label: 'x', changes, affected: [], at: 0 });

describe('UI-001: узел — размещение, а не копия элемента', () => {
  it('убрать с представления — элемент остаётся, рёбра и вложенные уходят', () => {
    const doc = docWithViews();
    const view = Object.values(doc.loadedViews).find((v) => Object.values(v.edges).length > 0)!;
    const edge = Object.values(view.edges)[0]!;
    const node = view.nodes[edge.sourceId]!;
    const edit = removeFromView(view, [node.id]);
    const after = applyChanges(doc, edit.changes);
    expect(after.loadedViews[view.id]!.nodes[node.id]).toBeUndefined();
    expect(after.loadedViews[view.id]!.edges[edge.id]).toBeUndefined();
    expect(after.elements[node.elementId!]).toBeDefined();
  });

  it('серверу уходит одна команда, а отмена возвращает рёбра явно', () => {
    const doc = docWithViews();
    const view = Object.values(doc.loadedViews).find((v) => Object.values(v.edges).length > 0)!;
    const edge = Object.values(view.edges)[0]!;
    const edit = removeFromView(view, [edge.sourceId]);
    const forward = edit.changes.flatMap((c) => commandsFor(doc.model.id, c));
    expect(forward.map((c) => c.describe)).toEqual([`remove node ${edge.sourceId}`]);
    const back = invert(op(edit.changes)).changes.flatMap((c) => commandsFor(doc.model.id, c));
    expect(back[0]!.describe).toBe(`place node ${edge.sourceId}`);
    expect(back.some((c) => c.describe === `place edge ${edge.id}`)).toBe(true);
  });

  it('удаление элемента уносит связи и узлы со всех представлений', () => {
    const doc = docWithViews();
    const iabs = Object.values(doc.elements).find((e) => e.name === 'IABS')!;
    const edit = deleteObjects(doc, [iabs.id]);
    const after = applyChanges(doc, edit.changes);
    expect(after.elements[iabs.id]).toBeUndefined();
    expect(Object.values(after.relationships).some((r) => r.sourceId === iabs.id || r.targetId === iabs.id)).toBe(false);
    for (const view of Object.values(after.loadedViews)) {
      expect(Object.values(view.nodes).some((n) => n.elementId === iabs.id)).toBe(false);
    }
    const commands = edit.changes.flatMap((c) => commandsFor(doc.model.id, c)).map((c) => c.describe);
    expect(commands[commands.length - 1]).toBe(`delete element ${iabs.id}`);
    expect(commands.every((c) => c.startsWith('delete'))).toBe(true);
  });

  it('размещение — новый узел с идентификаторами от клиента', () => {
    const doc = docWithViews();
    const view = Object.values(doc.loadedViews)[0]!;
    const element = Object.values(doc.elements)[0]!;
    const edit = placeElement(view, element.id, 10.4, 20.6);
    const node = edit.changes[0]!.after as { archiId: string; x: number };
    expect(node.archiId).toMatch(/^id-[0-9a-f]{32}$/);
    expect(node.x).toBe(10);
  });
});

describe('UI-016: правка дерева', () => {
  it('перенос меняет папку, но не порядок соседей', () => {
    const doc = docWithViews();
    const element = Object.values(doc.elements).find((e) => doc.folders[e.folderId]?.parentId)!;
    const root = rootOf(doc, element.id)!;
    const others = Object.values(doc.elements).filter((e) => e.folderId === element.folderId && e.id !== element.id);
    const edit = moveItems(doc, [element.id], root)!;
    const after = applyChanges(doc, edit.changes);
    expect(after.elements[element.id]!.folderId).toBe(root);
    for (const sibling of others) expect(after.elements[sibling.id]!.sortOrder).toBe(sibling.sortOrder);
  });

  it('INV-MDL-009: элемент не переносится в чужой корень', () => {
    const doc = docWithViews();
    const element = Object.values(doc.elements).find((e) => e.layer === 'APPLICATION')!;
    const business = Object.values(doc.folders).find((f) => f.folderType === 'BUSINESS')!;
    expect(moveItems(doc, [element.id], business.id)).toBeNull();
  });

  it('INV-MDL-009: системную папку не перенести', () => {
    const doc = docWithViews();
    const roots = Object.values(doc.folders).filter((f) => !f.parentId);
    expect(isSystemFolder(doc, roots[0]!.id)).toBe(true);
    expect(moveItems(doc, [roots[0]!.id], roots[1]!.id)).toBeNull();
  });

  it('новая папка — в конце родителя, на сервер с идентификаторами клиента', () => {
    const doc = docWithViews();
    const root = Object.values(doc.folders).find((f) => f.folderType === 'APPLICATION')!;
    const edit = createFolder(doc, root.id, 'Новая');
    const [command] = edit.changes.flatMap((c) => commandsFor(doc.model.id, c));
    expect(command!.describe).toBe(`create folder ${edit.folderId}`);
  });
});
