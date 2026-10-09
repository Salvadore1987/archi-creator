import { inverse, type Change } from './doc';
import type { Uuid } from '../api/types';

/** Операция истории: подпись, изменения и затронутые объекты для пометок в дереве. */
export interface Operation {
  id: string;
  label: string;
  changes: Change[];
  affected: Uuid[];
  at: number;
}

/**
 * Линейная история. `pointer` — сколько операций применено к документу,
 * `saved` — сколько из них отражено на сервере. `detour` — обратные
 * изменения уже отправленного, ветку которого отбросила новая правка
 * после отмены: они уходят на сервер первыми.
 */
export interface History {
  ops: Operation[];
  pointer: number;
  saved: number;
  detour: Operation[];
}

export const EMPTY_HISTORY: History = { ops: [], pointer: 0, saved: 0, detour: [] };

export function invert(op: Operation): Operation {
  return { ...op, changes: [...op.changes].reverse().map(inverse) };
}

/** Новая операция обрезает ветку «вперёд»; отправленное из неё откатывается через detour. */
export function push(history: History, op: Operation): History {
  let { detour, saved } = history;
  if (saved > history.pointer) {
    const dropped = history.ops.slice(history.pointer, saved).reverse().map(invert);
    detour = [...detour, ...dropped];
    saved = history.pointer;
  }
  return { ops: [...history.ops.slice(0, history.pointer), op], pointer: history.pointer + 1, saved, detour };
}

export function canUndo(history: History): boolean {
  return history.pointer > 0;
}

export function canRedo(history: History): boolean {
  return history.pointer < history.ops.length;
}

/**
 * Переход к позиции: операции, которые нужно применить к документу,
 * чтобы из текущей позиции попасть в целевую, — вперёд как есть,
 * назад обращёнными.
 */
export function moveTo(history: History, target: number): { history: History; apply: Operation[] } {
  const to = Math.max(0, Math.min(target, history.ops.length));
  const apply =
    to >= history.pointer
      ? history.ops.slice(history.pointer, to)
      : history.ops.slice(to, history.pointer).reverse().map(invert);
  return { history: { ...history, pointer: to }, apply };
}

/** Что отправить на сервер, чтобы он догнал документ: сначала detour, потом путь от точки сохранения. */
export function syncPlan(history: History): Operation[] {
  const path =
    history.pointer >= history.saved
      ? history.ops.slice(history.saved, history.pointer)
      : history.ops.slice(history.pointer, history.saved).reverse().map(invert);
  return [...history.detour, ...path];
}

/** Сервер догнал документ целиком. */
export function synced(history: History): History {
  return { ...history, saved: history.pointer, detour: [] };
}

/**
 * Синхронизация прервалась: документ остаётся где был, а невыполненное
 * становится очередью, которая уйдёт первой при следующем сохранении.
 */
export function partiallySynced(history: History, remaining: Operation[]): History {
  return { ...history, saved: history.pointer, detour: remaining };
}

/** Несохранённые операции: расстояние до точки сохранения плюс очередь отката. */
export function unsavedCount(history: History): number {
  return Math.abs(history.pointer - history.saved) + history.detour.length;
}

/** Объекты, отличающиеся от сохранённой версии, — для точки в строке дерева. */
export function dirtyObjects(history: History): Set<Uuid> {
  const ids = new Set<Uuid>();
  const [from, to] = history.pointer >= history.saved ? [history.saved, history.pointer] : [history.pointer, history.saved];
  for (const op of history.ops.slice(from, to)) {
    op.affected.forEach((id) => ids.add(id));
  }
  for (const op of history.detour) {
    op.affected.forEach((id) => ids.add(id));
  }
  return ids;
}
