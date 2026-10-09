import { api } from '../api/endpoints';
import { ApiError } from '../api/http';
import type { Element, Folder, Property, Relationship, Uuid, ViewEdge, ViewNode, ViewSummary } from '../api/types';
import type { Change } from './doc';
import type { Operation } from './history';

/** Серверная команда: описание для журнала и сам вызов. */
export interface Command {
  describe: string;
  run: () => Promise<unknown>;
}

/** Изменение, которое сервер выполнить не умеет: такой правки интерфейс не должен предлагать. */
export class UnsupportedChange extends Error {}

/** Команды одного изменения. Пусто — изменение сервер сделает сам, следствием соседней команды. */
export function commandsFor(modelId: Uuid, change: Change): Command[] {
  if (change.implicit) {
    return [];
  }
  switch (change.entity) {
    case 'folder':
      return folderCommands(modelId, change.before, change.after);
    case 'element':
      return elementCommands(modelId, change.before, change.after);
    case 'relationship':
      return relationshipCommands(modelId, change.before, change.after);
    case 'view':
      return viewCommands(modelId, change.before, change.after);
    case 'node':
      return nodeCommands(change.viewId!, change.before, change.after);
    case 'edge':
      return edgeCommands(change.viewId!, change.before, change.after);
  }
}

function command(describe: string, run: () => Promise<unknown>): Command {
  return { describe, run };
}

/** Перенос в другую папку: родитель папки — `parentId`, остальных — `folderId`. */
function treeCommands(modelId: Uuid, before: TreeItem, after: TreeItem): Command[] {
  const target = parentOf(after);
  if (parentOf(before) !== target && target) {
    return [command(`move ${after.id}`, () => api.moveItems(modelId, target, [after.id]))];
  }
  return [];
}

type TreeItem = { id: Uuid; folderId?: Uuid; parentId?: Uuid };

function parentOf(item: TreeItem): Uuid | undefined {
  return item.folderId ?? item.parentId;
}

function folderCommands(modelId: Uuid, before: Folder | null, after: Folder | null): Command[] {
  if (!before && after) {
    return [
      command(`create folder ${after.id}`, () =>
        api.createFolder(modelId, { id: after.id, archiId: after.archiId, parentId: after.parentId!, name: after.name }),
      ),
    ];
  }
  if (before && !after) {
    return [command(`delete folder ${before.id}`, () => api.deleteItems(modelId, [before.id]))];
  }
  const result = treeCommands(modelId, before!, after!);
  if (before!.name !== after!.name) {
    result.push(command(`rename folder ${after!.id}`, () => api.renameItem(modelId, after!.id, after!.name)));
  }
  return result;
}

/** Что из имени, документации и свойств отличается — только это и уходит в PATCH. */
function contentPatch(
  before: { name?: string; documentation?: string; properties: Property[] } | null,
  after: { name?: string; documentation?: string; properties: Property[] },
): { name?: string; documentation?: string; properties?: Property[] } | null {
  const patch: { name?: string; documentation?: string; properties?: Property[] } = {};
  if (before && before.name !== after.name) patch.name = after.name ?? '';
  if ((before?.documentation ?? '') !== (after.documentation ?? '')) patch.documentation = after.documentation ?? '';
  if (JSON.stringify(before?.properties ?? []) !== JSON.stringify(after.properties)) patch.properties = after.properties;
  return Object.keys(patch).length > 0 ? patch : null;
}

function elementCommands(modelId: Uuid, before: Element | null, after: Element | null): Command[] {
  if (!before && after) {
    const result = [
      command(`create element ${after.id}`, () =>
        api.createElement(modelId, {
          id: after.id,
          archiId: after.archiId,
          archiType: after.archiType,
          name: after.name,
          folderId: after.folderId,
        }),
      ),
    ];
    const patch = contentPatch({ name: after.name, properties: [] }, after);
    if (patch) {
      result.push(command(`patch element ${after.id}`, () => api.updateElement(after.id, patch)));
    }
    return result;
  }
  if (before && !after) {
    return [command(`delete element ${before.id}`, () => api.deleteElement(before.id))];
  }
  const result = treeCommands(modelId, before!, after!);
  const patch = contentPatch(before, after!);
  if (patch) {
    result.push(command(`patch element ${after!.id}`, () => api.updateElement(after!.id, patch)));
  }
  return result;
}

function relationshipCommands(modelId: Uuid, before: Relationship | null, after: Relationship | null): Command[] {
  if (!before && after) {
    const result = [
      command(`create relationship ${after.id}`, () =>
        api.createRelationship(modelId, {
          id: after.id,
          archiId: after.archiId,
          archiType: after.archiType,
          sourceId: after.sourceId,
          targetId: after.targetId,
          name: after.name,
          folderId: after.folderId,
          accessType: after.accessType,
          directed: after.directed,
        }),
      ),
    ];
    const patch = contentPatch({ name: after.name, properties: [] }, after);
    if (patch) {
      result.push(command(`patch relationship ${after.id}`, () => api.updateRelationship(after.id, patch)));
    }
    return result;
  }
  if (before && !after) {
    return [command(`delete relationship ${before.id}`, () => api.deleteRelationship(before.id))];
  }
  const result = treeCommands(modelId, before!, after!);
  const patch = contentPatch(before, after!);
  if (patch) {
    result.push(command(`patch relationship ${after!.id}`, () => api.updateRelationship(after!.id, patch)));
  }
  return result;
}

function viewCommands(modelId: Uuid, before: ViewSummary | null, after: ViewSummary | null): Command[] {
  if (!before || !after) {
    throw new UnsupportedChange('создание и удаление представлений не поддержано');
  }
  const result = treeCommands(modelId, before, after);
  if (before.name !== after.name) {
    result.push(command(`rename view ${after.id}`, () => api.renameItem(modelId, after.id, after.name)));
  }
  return result;
}

function nodeCommands(viewId: Uuid, before: ViewNode | null, after: ViewNode | null): Command[] {
  if (!before && after) {
    if (!after.elementId) {
      throw new UnsupportedChange('размещение группы и заметки не поддержано');
    }
    return [
      command(`place node ${after.id}`, () =>
        api.placeNode(viewId, {
          id: after.id,
          archiId: after.archiId,
          elementId: after.elementId!,
          x: after.x,
          y: after.y,
          width: after.width,
          height: after.height,
          parentId: after.parentId,
        }),
      ),
    ];
  }
  if (before && !after) {
    return [command(`remove node ${before.id}`, () => api.removeNode(before.id))];
  }
  if ((before!.parentId ?? null) !== (after!.parentId ?? null)) {
    throw new UnsupportedChange('перенос узла в другой контейнер не поддержан');
  }
  const moved =
    before!.x !== after!.x || before!.y !== after!.y || before!.width !== after!.width || before!.height !== after!.height;
  return moved
    ? [
        command(`layout node ${after!.id}`, () =>
          api.saveLayout(viewId, {
            nodes: [{ id: after!.id, x: after!.x, y: after!.y, width: after!.width, height: after!.height }],
            edges: [],
          }),
        ),
      ]
    : [];
}

function edgeCommands(viewId: Uuid, before: ViewEdge | null, after: ViewEdge | null): Command[] {
  if (!before && after) {
    if (!after.relationshipId) {
      throw new UnsupportedChange('соединение без связи модели не поддержано');
    }
    return [
      command(`place edge ${after.id}`, () =>
        api.placeEdge(viewId, {
          id: after.id,
          archiId: after.archiId,
          relationshipId: after.relationshipId!,
          sourceId: after.sourceId,
          targetId: after.targetId,
          bendpoints: after.bendpoints,
        }),
      ),
    ];
  }
  if (before && !after) {
    throw new UnsupportedChange('удаление ребра без его связи или узла не поддержано');
  }
  return JSON.stringify(before!.bendpoints) !== JSON.stringify(after!.bendpoints)
    ? [
        command(`layout edge ${after!.id}`, () =>
          api.saveLayout(viewId, { nodes: [], edges: [{ id: after!.id, bendpoints: after!.bendpoints }] }),
        ),
      ]
    : [];
}

/** Ход синхронизации: сколько выполнено и что осталось, если прервалась. */
export interface SyncOutcome {
  done: number;
  remaining: Operation[];
  error?: unknown;
}

/**
 * Отправка плана по одной команде, строго по порядку. Сбой останавливает
 * отправку: невыполненный хвост операции и следующие операции возвращаются,
 * чтобы уйти первыми при следующем сохранении.
 */
export async function runPlan(modelId: Uuid, plan: Operation[]): Promise<SyncOutcome> {
  let done = 0;
  for (let i = 0; i < plan.length; i++) {
    const op = plan[i]!;
    for (let c = 0; c < op.changes.length; c++) {
      const change = op.changes[c]!;
      try {
        for (const cmd of commandsFor(modelId, change)) {
          await runTolerant(cmd);
        }
      } catch (error) {
        const rest = { ...op, changes: op.changes.slice(c) };
        return { done, remaining: [rest, ...plan.slice(i + 1)], error };
      }
    }
    done++;
  }
  return { done, remaining: [] };
}

/**
 * Создание с занятым идентификатором при повторе — это наше же создание из
 * прерванной попытки: идентификаторы задаёт клиент, совпасть с чужими
 * 128 случайными битами они не могут.
 */
async function runTolerant(cmd: Command): Promise<void> {
  try {
    await cmd.run();
  } catch (error) {
    const replayedCreate =
      error instanceof ApiError && error.code === 'MDL_ID_TAKEN' && /^(create|place) /.test(cmd.describe);
    if (!replayedCreate) {
      throw error;
    }
  }
}
