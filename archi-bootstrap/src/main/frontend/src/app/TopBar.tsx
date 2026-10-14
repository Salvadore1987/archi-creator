import { useState } from 'react';
import type { Me } from '../api/types';
import { api } from '../api/endpoints';
import { signOut, type AuthMode } from '../auth/session';
import { t } from '../i18n';
import { canRedo, canUndo, unsavedCount } from '../model/history';
import { useMerge } from '../merge/store';
import { useEditor } from '../model/store';
import { describeError } from './errors';
import { Icon } from './Icon';
import { navigateToModel } from './route';
import { isEditorRole, useCanEdit, useSave } from './session';
import { useToast } from './toast';
import { useUi } from './uiState';
import { FOCUS_TREE_SEARCH } from './useShortcuts';
import { UserBadge } from './UserBadge';
import { ContextMenu, type MenuItem } from '../tree/TreeDialogs';

/** Имя файла из `Content-Disposition`: сначала UTF-8 (`filename*`), потом простое. */
function fileNameOf(response: Response): string | undefined {
  const header = response.headers.get('Content-Disposition') ?? '';
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (encoded) return decodeURIComponent(encoded[1]!);
  return /filename="([^"]+)"/i.exec(header)?.[1];
}

export function TopBar({ me, auth }: { me?: Me; auth: AuthMode }) {
  const model = useEditor((s) => s.doc!.model);
  const history = useEditor((s) => s.history);
  const lock = useEditor((s) => s.lock);
  const saving = useEditor((s) => s.save.kind === 'saving');
  const versionPending = useEditor((s) => s.versionPending);
  const undo = useEditor((s) => s.undo);
  const redo = useEditor((s) => s.redo);
  const canEdit = useCanEdit();
  const save = useSave();
  const snap = useUi((s) => s.snapToGrid);
  const toggleSnap = useUi((s) => s.toggleSnap);
  const editorRole = isEditorRole(me?.roles ?? []);
  const unsaved = unsavedCount(history);
  const undoOp = canUndo(history) ? history.ops[history.pointer - 1] : undefined;
  const redoOp = canRedo(history) ? history.ops[history.pointer] : undefined;

  const leave = () => {
    if (unsaved > 0 && !window.confirm(t('app.unsavedLeave'))) return;
    navigateToModel(null);
  };

  const activeViewId = useEditor((s) => s.activeViewId);
  const [exportMenu, setExportMenu] = useState<{ x: number; y: number } | null>(null);

  /**
   * Файл берёт имя из ответа сервера: там версия и слаг представления.
   * `lossNotice` — текст о потерях выгрузки, если сервер их насчитал.
   */
  const download = async (fetchFile: () => Promise<Response>, fallbackName: string,
    lossNotice?: (count: number) => string) => {
    try {
      const response = await fetchFile();
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = fileNameOf(response) ?? fallbackName;
      link.click();
      URL.revokeObjectURL(url);
      const losses = Number(response.headers.get('X-Archi-Loss-Count') ?? 0);
      if (lossNotice && losses > 0) useToast.getState().show(lossNotice(losses));
    } catch (error) {
      useToast.getState().show(describeError(error), { tone: 'error' });
    }
  };

  const exportItems: MenuItem[] = [
    { label: t('topbar.exportModel'), icon: 'download', run: () => void download(() => api.exportModel(model.id), `${model.name}.archimate`) },
    {
      label: t('topbar.exportOef'),
      icon: 'download',
      run: () => void download(() => api.exportModel(model.id, 'oef'), `${model.name}-oef.zip`, (n) => t('topbar.exportOefLosses', { n })),
    },
    ...(activeViewId
      ? ([
          { label: t('topbar.exportViewSvg'), icon: 'view', run: () => void download(() => api.exportViewImage(activeViewId, 'svg'), `${model.name}.svg`) },
          { label: t('topbar.exportViewPng'), icon: 'view', run: () => void download(() => api.exportViewImage(activeViewId, 'png'), `${model.name}.png`) },
          { label: t('topbar.exportViewPng2x'), icon: 'view', run: () => void download(() => api.exportViewImage(activeViewId, 'png', 2), `${model.name}.png`) },
        ] satisfies MenuItem[])
      : []),
    { label: t('topbar.exportAllSvg'), icon: 'folder', run: () => void download(() => api.exportAllViewImages(model.id, 'svg'), `${model.name}-svg.zip`) },
    { label: t('topbar.exportAllPng'), icon: 'folder', run: () => void download(() => api.exportAllViewImages(model.id, 'png'), `${model.name}-png.zip`) },
  ];

  return (
    <header className="topbar">
      <button type="button" className="brand" title={t('topbar.toModels')} onClick={leave}>
        {t('app.brand')}
        <em>{t('app.brandAccent')}</em>
        {t('app.brandTail')}
      </button>
      <div className="sep" />
      <div className="doc-title">
        <b>{model.name}</b>
        {lock.kind === 'mine' && (
          <span className="chip chip--lock" data-testid="lock-chip">
            <Icon name="lock" />
            {t('topbar.lockMine', { owner: me?.displayName ?? '' })}
          </span>
        )}
        {lock.kind === 'theirs' && (
          <span className="chip chip--warn" data-testid="lock-chip">
            <Icon name="lock" />
            {t('topbar.lockTheirs', { owner: lock.owner })}
          </span>
        )}
        {lock.kind === 'readonly' && (
          <span className="chip chip--ok" data-testid="lock-chip">
            <Icon name="eye" />
            {t('topbar.readOnly')}
          </span>
        )}
      </div>
      {editorRole && (
        <>
          <div className="sep" />
          <div className="toolbar" data-testid="edit-toolbar">
            <button
              type="button"
              className={`btn btn--icon${snap ? ' is-on' : ''}`}
              title={t('topbar.grid')}
              aria-pressed={snap}
              onClick={toggleSnap}
            >
              <Icon name="grid" />
            </button>
            <div className="sep" />
            <button
              type="button"
              className="btn btn--icon"
              data-testid="undo"
              disabled={!canEdit || !undoOp}
              title={undoOp ? t('topbar.undoOp', { label: undoOp.label }) : t('topbar.nothingToUndo')}
              onClick={undo}
            >
              <Icon name="undo" />
            </button>
            <button
              type="button"
              className="btn btn--icon"
              data-testid="redo"
              disabled={!canEdit || !redoOp}
              title={redoOp ? t('topbar.redoOp', { label: redoOp.label }) : t('topbar.nothingToRedo')}
              onClick={redo}
            >
              <Icon name="redo" />
            </button>
          </div>
        </>
      )}
      <div className="grow" />
      <button type="button" className="btn btn--ghost" onClick={() => window.dispatchEvent(new Event(FOCUS_TREE_SEARCH))}>
        <Icon name="search" />
        {t('topbar.search')}
        <span className="kbd">{t('topbar.searchKey')}</span>
      </button>
      {editorRole && (
        <button
          type="button"
          className="btn btn--ghost"
          data-testid="merge-open"
          title={t('topbar.mergeTitle')}
          onClick={() => useMerge.getState().show()}
        >
          <Icon name="relation" />
          {t('topbar.merge')}
        </button>
      )}
      <button
        type="button"
        className="btn btn--ghost"
        data-testid="export-menu"
        title={unsaved > 0 ? t('topbar.exportUnsaved') : undefined}
        onClick={(event) => {
          const box = event.currentTarget.getBoundingClientRect();
          setExportMenu({ x: box.left, y: box.bottom + 4 });
        }}
      >
        <Icon name="download" />
        {t('topbar.export')}
      </button>
      {exportMenu && (
        <ContextMenu
          x={exportMenu.x}
          y={exportMenu.y}
          title={unsaved > 0 ? t('topbar.exportUnsaved') : t('topbar.exportTitle')}
          items={exportItems}
          onClose={() => setExportMenu(null)}
        />
      )}
      {/* Кнопка остаётся на месте и во время отправки — «Сохранение…», а не исчезновение. */}
      {editorRole && lock.kind === 'mine' && (unsaved > 0 || versionPending || saving) && (
        <button type="button" className="btn btn--primary" data-testid="save" disabled={saving} onClick={() => void save()}>
          {saving ? t('topbar.saving') : t('topbar.save')}
        </button>
      )}
      <UserBadge me={me} onSignOut={auth.kind === 'oidc' ? () => void signOut(auth) : undefined} />
    </header>
  );
}
