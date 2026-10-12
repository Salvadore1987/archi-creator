import { api } from '../api/endpoints';
import { ErrorCodes } from '../api/codes';
import { ApiError } from '../api/http';
import type { MessageKey } from '../i18n';
import type { Element, Folder, Property, Relationship, Style, Uuid, ViewEdge, ViewNode, ViewSummary } from '../api/types';
import type { Change } from './doc';
import type { Operation } from './history';

/** Серверная команда: описание для журнала и сам вызов. */
export interface Command {
  describe: string;
  run: () => Promise<unknown>;
}

/** Изменение, которое сервер выполнить не умеет: такой правки интерфейс не должен предлагать. */
export class UnsupportedChange extends Error {
  constructor(readonly reason: MessageKey) {
    super(reason);
  }
}

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
    throw new UnsupportedChange('unsupported.viewCreateDelete');
  }
  const result = treeCommands(modelId, before, after);
  if (before.name !== after.name) {
    result.push(command(`rename view ${after.id}`, () => api.renameItem(modelId, after.id, after.name)));
  }
  return result;
}

/** Стиль как его хранит сервер: пустой и отсутствующий — одно и то же. */
function sameStyle(a?: Style, b?: Style): boolean {
  const norm = (style?: Style) => JSON.stringify(Object.fromEntries(Object.entries(style ?? {}).filter(([, v]) => v !== undefined && v !== '').sort()));
  return norm(a) === norm(b);
}

function hasStyle(style?: Style): boolean {
  return !sameStyle(style, undefined);
}

function nodeCommands(viewId: Uuid, before: ViewNode | null, after: ViewNode | null): Command[] {
  if (!before && after) {
    const result: Command[] = [];
    if (after.elementId) {
      result.push(
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
      );
    } else if (after.kind === 'GROUP' || after.kind === 'NOTE') {
      result.push(
        command(`place node ${after.id}`, () =>
          api.placeNode(viewId, {
            id: after.id,
            archiId: after.archiId,
            kind: after.kind as 'GROUP' | 'NOTE',
            label: after.kind === 'GROUP' ? after.label : undefined,
            content: after.kind === 'NOTE' ? after.content : undefined,
            x: after.x,
            y: after.y,
            width: after.width,
            height: after.height,
            parentId: after.parentId,
          }),
        ),
      );
    } else {
      throw new UnsupportedChange('unsupported.nodeKindPlacement');
    }
    if (hasStyle(after.style)) {
      result.push(command(`style node ${after.id}`, () => api.editNode(after.id, { style: after.style! })));
    }
    return result;
  }
  if (before && !after) {
    return [command(`remove node ${before.id}`, () => api.removeNode(before.id))];
  }
  const was = before!;
  const now = after!;
  const result: Command[] = [];
  const reparented = (was.parentId ?? null) !== (now.parentId ?? null);
  if (reparented) {
    result.push(
      command(`reparent node ${now.id}`, () => api.moveNodeToParent(now.id, { parentId: now.parentId, x: now.x, y: now.y })),
    );
  }
  const moved = !reparented && (was.x !== now.x || was.y !== now.y);
  const resized = was.width !== now.width || was.height !== now.height;
  if (moved || resized) {
    result.push(
      command(`layout node ${now.id}`, () =>
        api.saveLayout(viewId, {
          nodes: [{ id: now.id, x: now.x, y: now.y, width: now.width, height: now.height }],
          edges: [],
        }),
      ),
    );
  }
  const text: { label?: string; content?: string } = {};
  if ((was.label ?? '') !== (now.label ?? '')) text.label = now.label ?? '';
  if ((was.content ?? '') !== (now.content ?? '')) text.content = now.content ?? '';
  const restyled = !sameStyle(was.style, now.style);
  if (Object.keys(text).length > 0 || restyled) {
    const body = restyled ? { ...text, style: now.style ?? {} } : text;
    result.push(command(`edit node ${now.id}`, () => api.editNode(now.id, body)));
  }
  return result;
}

function edgeCommands(viewId: Uuid, before: ViewEdge | null, after: ViewEdge | null): Command[] {
  if (!before && after) {
    if (!after.relationshipId) {
      throw new UnsupportedChange('unsupported.edgeWithoutRelationship');
    }
    const result = [
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
    if (hasStyle(after.style)) {
      result.push(command(`style edge ${after.id}`, () => api.editEdge(after.id, { style: after.style! })));
    }
    return result;
  }
  if (before && !after) {
    return [command(`remove edge ${before.id}`, () => api.removeEdge(before.id))];
  }
  const result: Command[] = [];
  if (JSON.stringify(before!.bendpoints) !== JSON.stringify(after!.bendpoints)) {
    result.push(
      command(`layout edge ${after!.id}`, () =>
        api.saveLayout(viewId, { nodes: [], edges: [{ id: after!.id, bendpoints: after!.bendpoints }] }),
      ),
    );
  }
  if (!sameStyle(before!.style, after!.style)) {
    result.push(command(`style edge ${after!.id}`, () => api.editEdge(after!.id, { style: after!.style ?? {} })));
  }
  return result;
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
      error instanceof ApiError && error.code === ErrorCodes.ID_TAKEN && /^(create|place) /.test(cmd.describe);
    if (!replayedCreate) {
      throw error;
    }
  }
}
