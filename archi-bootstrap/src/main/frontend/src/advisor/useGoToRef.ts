import { useCallback } from 'react';
import { useToast } from '../app/toast';
import { useOpenView, useRevealOnCanvas } from '../canvas/useOpenView';
import { t } from '../i18n';
import { useEditor } from '../model/store';
import type { ElementRef } from '../api/types';

/**
 * Переход к объекту из ответа помощника: элемент выделяется на холсте, если он где-то
 * размещён, и в дереве — всегда; представление открывается вкладкой.
 */
export function useGoToRef(): (ref: ElementRef) => Promise<void> {
  const openView = useOpenView();
  const reveal = useRevealOnCanvas();
  return useCallback(
    async (ref: ElementRef) => {
      const doc = useEditor.getState().doc;
      if (!doc) return;
      const view = Object.values(doc.views).find((v) => v.archiId === ref.archiId);
      if (view) {
        await openView(view.id);
        return;
      }
      const object =
        Object.values(doc.elements).find((e) => e.archiId === ref.archiId) ??
        Object.values(doc.relationships).find((r) => r.archiId === ref.archiId);
      if (!object) {
        useToast.getState().show(t('advisor.errors.notInModel', { name: ref.name }), { tone: 'error' });
        return;
      }
      if (!(await reveal(object.id))) {
        useEditor.getState().select([object.id], 'external');
      }
    },
    [openView, reveal],
  );
}
