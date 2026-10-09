import { useEffect } from 'react';
import { useEditor } from '../model/store';
import { useCanEdit, useSave } from './session';

/** Поле ввода живёт своей отменой: Ctrl+Z в нём правит текст, а не модель. */
function typingTarget(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null;
  return !!element && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA' || element.isContentEditable);
}

export const FOCUS_TREE_SEARCH = 'archi:focus-tree-search';

/** Горячие клавиши как в Archi: отмена, повтор, сохранение, поиск по модели. */
export function useShortcuts(): void {
  const canEdit = useCanEdit();
  const save = useSave();

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const mod = event.metaKey || event.ctrlKey;
      if (!mod) return;
      const key = event.key.toLowerCase();
      if (key === 'f') {
        event.preventDefault();
        window.dispatchEvent(new Event(FOCUS_TREE_SEARCH));
        return;
      }
      if (!canEdit || typingTarget(event.target)) return;
      const editor = useEditor.getState();
      if (key === 'z' && !event.shiftKey) {
        event.preventDefault();
        editor.undo();
      } else if ((key === 'z' && event.shiftKey) || (key === 'y' && event.ctrlKey)) {
        event.preventDefault();
        editor.redo();
      } else if (key === 's') {
        event.preventDefault();
        void save();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [canEdit, save]);
}
