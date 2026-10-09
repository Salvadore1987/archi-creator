import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { api } from '../api/endpoints';
import type { Uuid } from '../api/types';
import { describeError } from '../app/errors';
import { useCanEdit } from '../app/session';
import { useToast } from '../app/toast';
import { useUi } from '../app/uiState';
import { useOpenView, useRevealOnCanvas } from '../canvas/useOpenView';
import { t } from '../i18n';
import { attachedRelationships } from '../model/doc';
import {
  createFolder as createFolderEdit,
  deleteFolders,
  deleteObjects,
  folderIsEmpty,
  isSystemFolder,
  moveItems,
  renameItem,
  type Edit,
} from '../model/ops';
import { useEditor } from '../model/store';
import { treeCommandsBus } from './bus';
import { ContextMenu, DeleteDialog, MoveDialog, type MenuItem } from './TreeDialogs';

/** Правка дерева на месте: переименование, перенос, папки, удаление. */
export interface TreeEditing {
  enabled: boolean;
  renaming: Uuid | null;
  dropTarget: Uuid | null;
  overlay: ReactNode;
  canRename(id: Uuid): boolean;
  startRename(id: Uuid): void;
  finishRename(id: Uuid, value: string | null): void;
  createFolder(): void;
  requestDelete(ids: Uuid[]): void;
  dragStart(ids: Uuid[]): void;
  dragEnd(): void;
  dragOver(event: React.DragEvent): void;
  dragLeave(event: React.DragEvent): void;
  drop(event: React.DragEvent): void;
  contextMenu(event: React.MouseEvent): void;
}

type Overlay =
  | { kind: 'menu'; x: number; y: number; id: Uuid }
  | { kind: 'move'; ids: Uuid[] }
  | { kind: 'delete'; ids: Uuid[]; relationships: number; onView: number };

function rowId(target: EventTarget): { id: Uuid; kind: string } | null {
  const row = (target as HTMLElement).closest('[data-row]') as HTMLElement | null;
  return row?.dataset.row ? { id: row.dataset.row, kind: row.dataset.kind ?? '' } : null;
}

/**
 * Каждое действие — операция истории с подписью, и сообщение о нём даёт
 * отменить сразу. Перетаскивание меняет папку, но не порядок внутри неё:
 * порядок значим для выгрузки, и случайный жест не должен его менять.
 */
export function useTreeEditing(): TreeEditing {
  const enabled = useCanEdit();
  const perform = useEditor((s) => s.perform);
  const select = useEditor((s) => s.select);
  const toast = useToast((s) => s.show);
  const openView = useOpenView();
  const reveal = useRevealOnCanvas();
  const requestInspector = useUi((s) => s.requestInspector);
  const [renaming, setRenaming] = useState<Uuid | null>(null);
  const [dropTarget, setDropTarget] = useState<Uuid | null>(null);
  const [overlay, setOverlay] = useState<Overlay | null>(null);
  const dragged = useRef<Uuid[]>([]);

  const doc = () => useEditor.getState().doc!;

  const apply = useCallback(
    (label: string, edit: Edit | null, explain?: string) => {
      if (!edit) {
        if (explain) toast(explain, { tone: 'error' });
        return;
      }
      perform(label, edit.changes, edit.affected);
      toast(label, { action: { label: t('tree_ops.undoToast'), run: () => useEditor.getState().undo() } });
    },
    [perform, toast],
  );

  const canRename = useCallback(
    (id: Uuid) => {
      const d = doc();
      if (!enabled || isSystemFolder(d, id) || d.relationships[id]) return false;
      const element = d.elements[id];
      return !element || element.supported;
    },
    [enabled],
  );

  const finishRename = useCallback(
    (id: Uuid, value: string | null) => {
      setRenaming(null);
      const d = doc();
      const before = d.elements[id]?.name ?? d.folders[id]?.name ?? d.views[id]?.name;
      if (value === null || before === undefined || value.trim() === '' || value === before) return;
      apply(t('tree_ops.renameOp', { from: before, to: value.trim() }), renameItem(d, id, value.trim()));
    },
    [apply],
  );

  const createFolder = useCallback(
    (parent?: Uuid) => {
      const d = doc();
      const lead = useEditor.getState().selection.ids.at(-1);
      const target =
        parent ??
        (lead ? (d.folders[lead] ? lead : (d.elements[lead]?.folderId ?? d.relationships[lead]?.folderId ?? d.views[lead]?.folderId)) : undefined) ??
        Object.values(d.folders).find((f) => !f.parentId && f.folderType === 'OTHER')?.id;
      if (!target) return;
      const name = t('tree.newFolderName');
      const edit = createFolderEdit(d, target, name);
      perform(t('tree_ops.createFolderOp', { name }), edit.changes, edit.affected);
      select([edit.folderId], 'external');
      setRenaming(edit.folderId);
    },
    [perform, select],
  );

  const move = useCallback(
    (ids: Uuid[], folderId: Uuid) => {
      const d = doc();
      const folder = d.folders[folderId];
      if (!folder) return;
      apply(t('tree_ops.moveOp', { n: ids.length, folder: folder.name }), moveItems(d, ids, folderId), t('tree_ops.moveNotAllowed'));
    },
    [apply],
  );

  /**
   * Удаление начинается с показа последствий. Перед ним подгружаются
   * представления, где лежат удаляемые элементы: без их узлов отмена
   * не смогла бы вернуть размещения.
   */
  const requestDelete = useCallback(
    async (ids: Uuid[]) => {
      if (!enabled || ids.length === 0) return;
      const d = doc();
      if (ids.some((id) => isSystemFolder(d, id))) {
        toast(t('tree_ops.systemFolder'), { tone: 'error' });
        return;
      }
      if (ids.some((id) => d.views[id])) {
        toast(t('tree_ops.viewDelete'), { tone: 'error' });
        return;
      }
      if (ids.some((id) => d.folders[id] && !folderIsEmpty(d, id))) {
        toast(t('tree_ops.deleteFolderNotEmpty'), { tone: 'error' });
        return;
      }
      const placements = Object.entries(d.placements)
        .filter(([viewId, p]) => !d.loadedViews[viewId] && p.elementIds.some((id) => ids.includes(id)))
        .map(([viewId]) => viewId);
      try {
        for (const viewId of placements) {
          useEditor.getState().addView(await api.openView(viewId));
        }
      } catch (error) {
        toast(describeError(error), { tone: 'error' });
        return;
      }
      const current = doc();
      const activeView = useEditor.getState().activeViewId;
      const onView = activeView
        ? Object.values(current.loadedViews[activeView]?.nodes ?? {}).filter((n) => n.elementId && ids.includes(n.elementId)).length
        : 0;
      const ownRelationships = ids.filter((id) => current.relationships[id]).length;
      setOverlay({
        kind: 'delete',
        ids,
        relationships: attachedRelationships(current, ids).length + ownRelationships,
        onView,
      });
    },
    [enabled, toast],
  );

  const confirmDelete = (ids: Uuid[]) => {
    setOverlay(null);
    const d = doc();
    const folders = ids.filter((id) => d.folders[id]);
    const objects = ids.filter((id) => !d.folders[id]);
    const edits = [deleteObjects(d, objects), deleteFolders(d, folders)];
    const edit: Edit = { changes: edits.flatMap((e) => e.changes), affected: edits.flatMap((e) => e.affected) };
    select([], 'tree');
    apply(t('tree_ops.deleteOp', { n: ids.length }), edit);
  };

  useEffect(
    () =>
      treeCommandsBus.subscribe((command, ids) => {
        if (command === 'move') setOverlay({ kind: 'move', ids });
        if (command === 'delete') void requestDelete(ids);
        if (command === 'rename' && ids[0]) setRenaming(ids[0]);
      }),
    [requestDelete],
  );

  const menuItems = (id: Uuid): MenuItem[] => {
    const d = doc();
    const selection = useEditor.getState().selection.ids;
    const targets = selection.includes(id) ? selection : [id];
    const element = d.elements[id];
    const folder = d.folders[id];
    const view = d.views[id];
    const name = element?.name ?? folder?.name ?? view?.name ?? d.relationships[id]?.name ?? '';
    const items: MenuItem[] = [];
    if (view) items.push({ label: t('tree_ops.openView'), icon: 'view', run: () => void openView(id) });
    if (element) {
      items.push({
        label: t('tree_ops.showOnView'),
        icon: 'target',
        run: () => void reveal(id).then((found) => found || toast(t('tree_ops.noPlacement'))),
      });
      items.push({
        label: t('tree_ops.whereUsed'),
        icon: 'eye',
        run: () => {
          select([id], 'tree');
          requestInspector('description');
        },
      });
    }
    items.push({
      label: t('tree_ops.copyName'),
      icon: 'copy',
      run: () => void navigator.clipboard?.writeText(name).then(() => toast(t('tree_ops.copied'))),
    });
    if (!enabled) return items;
    if (canRename(id)) items.push({ label: t('tree_ops.rename'), icon: 'pencil', hint: 'F2', run: () => setRenaming(id) });
    if (!isSystemFolder(d, id)) {
      items.push({ label: t('tree_ops.move'), icon: 'folder', run: () => setOverlay({ kind: 'move', ids: targets }) });
    }
    if (folder) {
      items.push({ label: t('tree_ops.newSubfolder'), icon: 'plus', run: () => createFolder(id) });
      items.push({
        label: t('tree_ops.selectAll'),
        icon: 'tree',
        run: () => select(Object.values(d.elements).filter((e) => e.folderId === id).map((e) => e.id), 'tree'),
      });
    }
    // Системной папке пункт удаления не показывается вовсе.
    if (!isSystemFolder(d, id)) {
      items.push({ label: t('tree_ops.delete'), icon: 'trash', danger: true, run: () => void requestDelete(targets) });
    }
    return items;
  };

  const overlayNode = (() => {
    if (!overlay) return null;
    const d = doc();
    if (overlay.kind === 'menu') {
      const name = d.elements[overlay.id]?.name ?? d.folders[overlay.id]?.name ?? d.views[overlay.id]?.name ?? '';
      return (
        <ContextMenu x={overlay.x} y={overlay.y} title={name} items={menuItems(overlay.id)} onClose={() => setOverlay(null)} />
      );
    }
    if (overlay.kind === 'move') {
      return (
        <MoveDialog
          doc={d}
          ids={overlay.ids}
          onClose={() => setOverlay(null)}
          onMove={(folderId) => {
            setOverlay(null);
            move(overlay.ids, folderId);
          }}
        />
      );
    }
    return (
      <DeleteDialog
        count={overlay.ids.length}
        relationships={overlay.relationships}
        onView={overlay.onView}
        onClose={() => setOverlay(null)}
        onConfirm={() => confirmDelete(overlay.ids)}
      />
    );
  })();

  return {
    enabled,
    renaming,
    dropTarget,
    overlay: overlayNode,
    canRename,
    startRename: (id) => canRename(id) && setRenaming(id),
    finishRename,
    createFolder: () => createFolder(),
    requestDelete: (ids) => void requestDelete(ids),
    dragStart: (ids) => {
      dragged.current = ids;
    },
    dragEnd: () => {
      dragged.current = [];
      setDropTarget(null);
    },
    dragOver: (event) => {
      if (!enabled || dragged.current.length === 0) return;
      const row = rowId(event.target);
      if (row?.kind === 'folder' && !dragged.current.includes(row.id)) {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
        setDropTarget(row.id);
      } else {
        setDropTarget(null);
      }
    },
    dragLeave: (event) => {
      if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node)) setDropTarget(null);
    },
    drop: (event) => {
      const row = rowId(event.target);
      setDropTarget(null);
      if (!row || row.kind !== 'folder' || dragged.current.length === 0) return;
      event.preventDefault();
      move(dragged.current, row.id);
      dragged.current = [];
    },
    contextMenu: (event) => {
      const row = rowId(event.target);
      if (!row || row.kind === 'group') return;
      event.preventDefault();
      if (!useEditor.getState().selection.ids.includes(row.id)) select([row.id], 'tree');
      setOverlay({ kind: 'menu', x: event.clientX, y: event.clientY, id: row.id });
    },
  };
}
