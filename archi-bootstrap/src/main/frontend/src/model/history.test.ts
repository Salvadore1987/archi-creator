import { describe, expect, it } from 'vitest';
import type { Element } from '../api/types';
import { applyChanges, type Change, type ModelDoc } from './doc';
import {
  EMPTY_HISTORY,
  dirtyObjects,
  invert,
  moveTo,
  partiallySynced,
  push,
  syncPlan,
  synced,
  unsavedCount,
  type History,
  type Operation,
} from './history';

const element = (id: string, name: string): Element => ({
  id,
  folderId: 'f',
  archiId: `id-${id}`,
  archiType: 'archimate:ApplicationComponent',
  layer: 'APPLICATION',
  name,
  properties: [],
  supported: true,
  sortOrder: 0,
});

const rename = (id: string, from: string, to: string): Operation => ({
  id: `${id}:${to}`,
  label: `rename ${id}`,
  changes: [{ entity: 'element', id, before: element(id, from), after: element(id, to) }],
  affected: [id],
  at: 0,
});

const names = (ops: Operation[]) =>
  ops.flatMap((op) => op.changes.map((c) => `${(c.before as Element).name}→${(c.after as Element).name}`));

function pushAll(history: History, ops: Operation[]): History {
  return ops.reduce(push, history);
}

describe('UI-017: история на любую глубину сессии', () => {
  it('30 операций, возврат к пятой, повтор до конца', () => {
    const ops = Array.from({ length: 30 }, (_, i) => rename('a', `v${i}`, `v${i + 1}`));
    const doc: ModelDoc = {
      model: { id: 'm', workspaceId: 'w', archiId: 'id-m', name: 'm', status: 'ACTIVE' },
      folders: {},
      elements: { a: element('a', 'v0') },
      relationships: {},
      views: {},
      placements: {},
      loadedViews: {},
    };
    let history = EMPTY_HISTORY;
    let current = doc;
    for (const op of ops) {
      history = push(history, op);
      current = applyChanges(current, op.changes);
    }
    expect(current.elements.a!.name).toBe('v30');

    const back = moveTo(history, 5);
    current = back.apply.reduce((d, op) => applyChanges(d, op.changes), current);
    expect(current.elements.a!.name).toBe('v5');
    expect(back.history.pointer).toBe(5);

    const forward = moveTo(back.history, 30);
    current = forward.apply.reduce((d, op) => applyChanges(d, op.changes), current);
    expect(current.elements.a!.name).toBe('v30');
  });

  it('новая операция после отмены обрезает ветку «вперёд»', () => {
    let history = pushAll(EMPTY_HISTORY, [rename('a', '0', '1'), rename('a', '1', '2')]);
    history = moveTo(history, 1).history;
    history = push(history, rename('a', '1', 'x'));
    expect(history.ops).toHaveLength(2);
    expect(history.pointer).toBe(2);
  });

  it('обратная операция переставляет изменения в обратном порядке', () => {
    const op: Operation = {
      id: 'o',
      label: 'two',
      affected: [],
      at: 0,
      changes: [
        { entity: 'element', id: 'a', before: null, after: element('a', 'A') },
        { entity: 'element', id: 'b', before: null, after: element('b', 'B') },
      ],
    };
    const reverted = invert(op).changes as Change[];
    expect(reverted.map((c) => c.id)).toEqual(['b', 'a']);
    expect(reverted.every((c) => c.after === null)).toBe(true);
  });
});

describe('ADR-0018: сохранение переводит сервер от точки сохранения к позиции', () => {
  it('вперёд — операции после точки сохранения как есть', () => {
    let history = pushAll(EMPTY_HISTORY, [rename('a', '0', '1')]);
    history = synced(history);
    history = pushAll(history, [rename('a', '1', '2'), rename('a', '2', '3')]);
    expect(names(syncPlan(history))).toEqual(['1→2', '2→3']);
    expect(unsavedCount(history)).toBe(2);
  });

  it('отмена за точку сохранения — тоже несохранённое изменение, назад обращёнными', () => {
    let history = pushAll(EMPTY_HISTORY, [rename('a', '0', '1'), rename('a', '1', '2')]);
    history = synced(history);
    history = moveTo(history, 0).history;
    expect(unsavedCount(history)).toBe(2);
    expect(names(syncPlan(history))).toEqual(['2→1', '1→0']);
  });

  it('новая ветка после отмены за точку сохранения сначала откатывает отправленное', () => {
    let history = pushAll(EMPTY_HISTORY, [rename('a', '0', '1'), rename('a', '1', '2')]);
    history = synced(history);
    history = moveTo(history, 1).history;
    history = push(history, rename('a', '1', 'x'));
    expect(names(syncPlan(history))).toEqual(['2→1', '1→x']);
    expect(unsavedCount(history)).toBe(2);
  });

  it('отмена до точки сохранения снимает пометки сама', () => {
    let history = pushAll(EMPTY_HISTORY, [rename('a', '0', '1')]);
    history = synced(history);
    history = push(history, rename('b', '0', '1'));
    expect(dirtyObjects(history)).toEqual(new Set(['b']));
    history = moveTo(history, 1).history;
    expect(dirtyObjects(history).size).toBe(0);
    expect(unsavedCount(history)).toBe(0);
  });

  it('прерванная синхронизация оставляет хвост первым в очереди', () => {
    let history = pushAll(EMPTY_HISTORY, [rename('a', '0', '1'), rename('a', '1', '2')]);
    const plan = syncPlan(history);
    history = partiallySynced(history, plan.slice(1));
    expect(names(syncPlan(history))).toEqual(['1→2']);
    expect(unsavedCount(history)).toBe(1);
  });
});
