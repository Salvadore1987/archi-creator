import type { ReactNode } from 'react';
import type { Uuid } from '../api/types';

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

export function useTreeEditing(): TreeEditing {
  return {
    enabled: false,
    renaming: null,
    dropTarget: null,
    overlay: null,
    canRename: () => false,
    startRename: () => {},
    finishRename: () => {},
    createFolder: () => {},
    requestDelete: () => {},
    dragStart: () => {},
    dragEnd: () => {},
    dragOver: () => {},
    dragLeave: () => {},
    drop: () => {},
    contextMenu: () => {},
  };
}
