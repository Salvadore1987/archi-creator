import { useCallback } from 'react';
import { api } from '../api/endpoints';
import type { Uuid } from '../api/types';
import { describeError } from '../app/errors';
import { useToast } from '../app/toast';
import { useEditor } from '../model/store';

/** Открыть представление вкладкой; содержимое подгружается один раз за сессию. */
export function useOpenView(): (viewId: Uuid) => Promise<void> {
  return useCallback(async (viewId: Uuid) => {
    const state = useEditor.getState();
    if (!state.doc) return;
    if (!state.doc.loadedViews[viewId]) {
      try {
        state.addView(await api.openView(viewId));
      } catch (error) {
        useToast.getState().show(describeError(error), { tone: 'error' });
        return;
      }
    }
    useEditor.getState().openView(viewId);
  }, []);
}

/** Для дерева и карточки: где объект на холсте и как к нему перейти. */
export function useRevealOnCanvas(): (elementId: Uuid) => Promise<boolean> {
  const openView = useOpenView();
  return useCallback(
    async (elementId: Uuid) => {
      const state = useEditor.getState();
      const doc = state.doc;
      if (!doc) return false;
      const active = state.activeViewId;
      const candidates = Object.keys(doc.views).filter((viewId) => {
        const loaded = doc.loadedViews[viewId];
        return loaded
          ? Object.values(loaded.nodes).some((n) => n.elementId === elementId)
          : doc.placements[viewId]?.elementIds.includes(elementId);
      });
      if (candidates.length === 0) return false;
      const target = active && candidates.includes(active) ? active : candidates[0]!;
      await openView(target);
      useEditor.getState().select([elementId], 'external');
      return true;
    },
    [openView],
  );
}
