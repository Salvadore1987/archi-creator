import { describe, expect, it } from 'vitest';
import { referenceTree, referenceViews } from '../tree/fixture';
import { commandsFor } from './commands';
import { applyChanges, fromTree, viewDocOf } from './doc';
import { invert } from './history';
import {
  applyLayoutPatch,
  createFolder,
  deleteObjects,
  drawRelationship,
  impliedRelationship,
  isSystemFolder,
  layoutRequest,
  moveItems,
  nestNodes,
  placeElement,
  removeFromView,
  rootOf,
} from './ops';

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

describe('UI-003: нарисованная связь', () => {
  it('новая связь и её ребро — одна правка, на сервер две команды', () => {
    const doc = docWithViews();
    const view = Object.values(doc.loadedViews).find((v) => Object.values(v.nodes).filter((n) => n.elementId).length > 1)!;
    const [a, b] = Object.values(view.nodes).filter((n) => n.elementId && !n.parentId);
    const edit = drawRelationship(doc, view, a!.id, b!.id, 'archimate:AssociationRelationship')!;
    expect(edit.created).toBe(true);
    const after = applyChanges(doc, edit.changes);
    const relationship = after.relationships[edit.relationshipId]!;
    expect([relationship.sourceId, relationship.targetId]).toEqual([a!.elementId, b!.elementId]);
    expect(doc.folders[relationship.folderId]!.folderType).toBe('RELATIONS');
    const commands = edit.changes.flatMap((c) => commandsFor(doc.model.id, c)).map((c) => c.describe);
    expect(commands).toEqual([`create relationship ${edit.relationshipId}`, expect.stringMatching(/^place edge /)]);
  });

  it('связь того же типа между той же парой не дублируется — рисуется её ребро', () => {
    const doc = docWithViews();
    const view = Object.values(doc.loadedViews).find((v) => Object.values(v.edges).length > 0)!;
    const edge = Object.values(view.edges).find((e) => view.nodes[e.sourceId] && view.nodes[e.targetId])!;
    const relationship = doc.relationships[edge.relationshipId!]!;
    expect(drawRelationship(doc, view, edge.sourceId, edge.targetId, relationship.archiType)!.changes).toEqual([]);
    const without = applyChanges(doc, [{ entity: 'edge', id: edge.id, viewId: view.id, before: edge, after: null }]);
    const again = drawRelationship(without, without.loadedViews[view.id]!, edge.sourceId, edge.targetId, relationship.archiType)!;
    expect(again.created).toBe(false);
    expect(again.changes.map((c) => c.entity)).toEqual(['edge']);
  });
});

describe('UI-004: вложенность', () => {
  it('перенос в контейнер — в конец его содержимого, на сервер команда переноса', () => {
    const doc = docWithViews();
    const view = Object.values(doc.loadedViews).find((v) => Object.values(v.nodes).filter((n) => !n.parentId && n.elementId).length > 1)!;
    const [parent, child] = Object.values(view.nodes).filter((n) => !n.parentId && n.elementId);
    const edit = nestNodes(view, [{ id: child!.id, parentId: parent!.id, x: 10, y: 20 }]);
    const moved = edit.changes[0]!.after as typeof child;
    expect(moved!.parentId).toBe(parent!.id);
    const siblings = Object.values(view.nodes).filter((n) => n.parentId === parent!.id).map((n) => n.sortOrder);
    expect(moved!.sortOrder).toBeGreaterThan(Math.max(0, ...siblings));
    expect(commandsFor(doc.model.id, edit.changes[0]!).map((c) => c.describe)).toEqual([`reparent node ${child!.id}`]);
    const back = invert(op(edit.changes)).changes.flatMap((c) => commandsFor(doc.model.id, c)).map((c) => c.describe);
    expect(back).toEqual([`reparent node ${child!.id}`]);
  });

  it('подразумеваемая связь не дублирует уже выражающую вложенность', () => {
    const doc = docWithViews();
    const composition = Object.values(doc.relationships).find((r) => r.archiType === 'archimate:CompositionRelationship')!;
    expect(impliedRelationship(doc, composition.sourceId, composition.targetId, 'archimate:CompositionRelationship').changes).toEqual([]);
    const [a, b] = Object.values(doc.elements).filter(
      (e) => !Object.values(doc.relationships).some((r) => r.sourceId === e.id || r.targetId === e.id),
    );
    const implied = impliedRelationship(doc, a!.id, b!.id, 'archimate:AssignmentRelationship');
    expect(implied.changes.map((c) => c.entity)).toEqual(['relationship']);
  });
});

describe('UI-005: авторазметка', () => {
  it('граф — все узлы с типами элементов, предложение — одна правка, на сервер PUT layout', () => {
    const doc = docWithViews();
    const view = Object.values(doc.loadedViews).find((v) => Object.values(v.edges).length > 0)!;
    const request = layoutRequest(doc, view, 'layered');
    expect(request.nodes).toHaveLength(Object.keys(view.nodes).length);
    const withElement = request.nodes.find((n) => view.nodes[n.id]!.elementId)!;
    expect(withElement.conceptType).toBe(doc.elements[view.nodes[withElement.id]!.elementId!]!.archiType);
    const root = request.nodes.find((n) => !n.parentId)!;
    const edge = Object.values(view.edges).find((e) => e.bendpoints.length > 0) ?? Object.values(view.edges)[0]!;
    const edit = applyLayoutPatch(view, {
      nodes: [{ id: root.id, x: root.x + 100, y: root.y, width: root.width, height: root.height }],
      edges: [{ id: edge.id, bendpoints: [] }],
    });
    const commands = edit.changes.flatMap((c) => commandsFor(doc.model.id, c)).map((c) => c.describe);
    expect(commands[0]).toBe(`layout node ${root.id}`);
    expect(applyLayoutPatch(view, { nodes: [], edges: [] }).changes).toEqual([]);
  });
});
