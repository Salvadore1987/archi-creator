import type { Uuid } from '../api/types';

type TreeCommand = 'move' | 'delete' | 'rename';
type Listener = (command: TreeCommand, ids: Uuid[]) => void;

const listeners = new Set<Listener>();

/** Команды правки дерева из других панелей: диалоги переноса и удаления живут в дереве. */
export const treeCommandsBus = {
  emit(command: TreeCommand, ids: Uuid[]) {
    listeners.forEach((listener) => listener(command, ids));
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
