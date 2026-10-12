import { useReactFlow } from '@xyflow/react';
import { useEffect, useRef, useState } from 'react';
import { api } from '../api/endpoints';
import type { LayoutStrategy, Uuid } from '../api/types';
import { describeError } from '../app/errors';
import { Icon } from '../app/Icon';
import { useToast } from '../app/toast';
import { t, type MessageKey } from '../i18n';
import { applyLayoutPatch, layoutRequest } from '../model/ops';
import { useEditor } from '../model/store';

const STRATEGIES: Array<{ id: LayoutStrategy; label: MessageKey }> = [
  { id: 'layered', label: 'canvas.layoutLayered' },
  { id: 'hierarchy', label: 'canvas.layoutHierarchy' },
  { id: 'compact', label: 'canvas.layoutCompact' },
];

/**
 * Инструменты холста над представлением. Авторазметку считает сервер по графу
 * из документа, а применяется она здесь — одной операцией истории.
 */
export function CanvasTools({ viewId, canEdit }: { viewId: Uuid; canEdit: boolean }) {
  const flow = useReactFlow();
  const perform = useEditor((s) => s.perform);
  const toast = useToast((s) => s.show);
  const [menu, setMenu] = useState(false);
  const [busy, setBusy] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menu) return;
    const away = (event: PointerEvent) => {
      if (!box.current?.contains(event.target as Node)) setMenu(false);
    };
    window.addEventListener('pointerdown', away, true);
    return () => window.removeEventListener('pointerdown', away, true);
  }, [menu]);

  const autoLayout = async (strategy: (typeof STRATEGIES)[number]) => {
    setMenu(false);
    const doc = useEditor.getState().doc!;
    const view = doc.loadedViews[viewId]!;
    setBusy(true);
    try {
      const patch = await api.autoLayout(viewId, layoutRequest(doc, view, strategy.id));
      const current = useEditor.getState().doc!.loadedViews[viewId]!;
      const edit = applyLayoutPatch(current, patch);
      perform(t('tree_ops.autoLayoutOp', { strategy: t(strategy.label) }), edit.changes, edit.affected);
      requestAnimationFrame(() => void flow.fitView({ padding: 0.1, maxZoom: 1, duration: 200 }));
    } catch (error) {
      toast(describeError(error), { tone: 'error' });
    } finally {
      setBusy(false);
    }
  };

  if (!canEdit) return null;
  return (
    <div className="canvas-tools" ref={box} data-testid="canvas-tools">
      <button
        type="button"
        className={`btn btn--small${menu ? ' is-on' : ''}`}
        data-testid="auto-layout"
        disabled={busy}
        aria-expanded={menu}
        title={t('canvas.autoLayoutHint')}
        onClick={() => setMenu((open) => !open)}
      >
        <Icon name="layout" />
        {t('canvas.autoLayout')}
      </button>
      {menu && (
        <div className="canvas-tools__menu" role="menu">
          {STRATEGIES.map((strategy) => (
            <button
              key={strategy.id}
              type="button"
              role="menuitem"
              className="canvas-tools__item"
              data-strategy={strategy.id}
              onClick={() => void autoLayout(strategy)}
            >
              {t(strategy.label)}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
