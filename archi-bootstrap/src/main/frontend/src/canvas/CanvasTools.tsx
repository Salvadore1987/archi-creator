import { useReactFlow } from '@xyflow/react';
import { useEffect, useRef, useState } from 'react';
import { api } from '../api/endpoints';
import type { LayoutStrategy, Uuid } from '../api/types';
import { describeError } from '../app/errors';
import { Icon } from '../app/Icon';
import { useToast } from '../app/toast';
import { useUi } from '../app/uiState';
import { t, type MessageKey } from '../i18n';
import type { IconName } from '../app/Icon';
import { alignNodes, applyLayoutPatch, layoutRequest, type AlignMode } from '../model/ops';
import { absolutePosition, nodeSelected } from './flowModel';
import { effectiveSize } from './shapes';
import { useEditor } from '../model/store';

const STRATEGIES: Array<{ id: LayoutStrategy; label: MessageKey }> = [
  { id: 'layered', label: 'canvas.layoutLayered' },
  { id: 'hierarchy', label: 'canvas.layoutHierarchy' },
  { id: 'compact', label: 'canvas.layoutCompact' },
];

const ALIGN: Array<{ mode: AlignMode; icon: IconName; label: MessageKey }> = [
  { mode: 'left', icon: 'align-left', label: 'canvas.alignLeft' },
  { mode: 'center', icon: 'align-center', label: 'canvas.alignCenter' },
  { mode: 'right', icon: 'align-right', label: 'canvas.alignRight' },
  { mode: 'top', icon: 'align-top', label: 'canvas.alignTop' },
  { mode: 'middle', icon: 'align-middle', label: 'canvas.alignMiddle' },
  { mode: 'bottom', icon: 'align-bottom', label: 'canvas.alignBottom' },
  { mode: 'width', icon: 'match-width', label: 'canvas.matchWidth' },
  { mode: 'height', icon: 'match-height', label: 'canvas.matchHeight' },
];

/** Узлы выделения на представлении — в порядке выделения: последний служит опорным. */
function selectedNodeIds(viewId: Uuid): Uuid[] {
  const state = useEditor.getState();
  const view = state.doc?.loadedViews[viewId];
  if (!view) return [];
  return state.selection.ids.flatMap((id) => {
    const node = Object.values(view.nodes).find((n) => nodeSelected(n, new Set([id])));
    return node ? [node.id] : [];
  });
}

/**
 * Инструменты холста над представлением. Авторазметку считает сервер по графу
 * из документа, а применяется она здесь — одной операцией истории.
 */
export function CanvasTools({ viewId, canEdit }: { viewId: Uuid; canEdit: boolean }) {
  const flow = useReactFlow();
  const perform = useEditor((s) => s.perform);
  const toast = useToast((s) => s.show);
  const [menu, setMenu] = useState(false);
  const navigator = useUi((s) => s.navigatorOpen);
  const toggleNavigator = useUi((s) => s.toggleNavigator);
  const [busy, setBusy] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const alignable = useEditor((s) => {
    const view = s.doc?.loadedViews[viewId];
    if (!view || s.selection.ids.length < 2) return false;
    const chosen = new Set(s.selection.ids);
    return Object.values(view.nodes).filter((n) => nodeSelected(n, chosen)).length >= 2;
  });

  const align = (mode: AlignMode, label: MessageKey) => {
    const view = useEditor.getState().doc!.loadedViews[viewId]!;
    const edit = alignNodes(
      view,
      selectedNodeIds(viewId),
      mode,
      (n) => effectiveSize(n.archiType, n.width, n.height),
      (id) => absolutePosition(view, id),
    );
    perform(t('tree_ops.alignOp', { how: t(label) }), edit.changes, edit.affected);
  };

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

  return (
    <div className="canvas-tools" ref={box} data-testid="canvas-tools">
      <button
        type="button"
        className={`btn btn--icon btn--small${navigator ? ' is-on' : ''}`}
        data-testid="navigator-toggle"
        aria-pressed={navigator}
        title={t('canvas.navigator')}
        onClick={toggleNavigator}
      >
        <Icon name="map" />
      </button>
      {canEdit && (
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
      )}
      {canEdit && alignable && (
        <div className="canvas-tools__group" data-testid="align-tools">
          {ALIGN.map((a) => (
            <button
              key={a.mode}
              type="button"
              className="btn btn--icon btn--small"
              data-align={a.mode}
              title={t(a.label)}
              onClick={() => align(a.mode, a.label)}
            >
              <Icon name={a.icon} />
            </button>
          ))}
        </div>
      )}
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
