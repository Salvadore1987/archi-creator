import type { Me } from '../api/types';
import { api } from '../api/endpoints';
import { signOut, type AuthMode } from '../auth/session';
import { t } from '../i18n';
import { canRedo, canUndo, unsavedCount } from '../model/history';
import { useEditor } from '../model/store';
import { describeError } from './errors';
import { Icon } from './Icon';
import { navigateToModel } from './route';
import { isEditorRole, useCanEdit, useSave } from './session';
import { useToast } from './toast';
import { useUi } from './uiState';
import { FOCUS_TREE_SEARCH } from './useShortcuts';
import { UserBadge } from './UserBadge';

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

  const exportModel = async () => {
    try {
      const response = await api.exportModel(model.id);
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${model.name}.archimate`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      useToast.getState().show(describeError(error), { tone: 'error' });
    }
  };

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
      <button type="button" className="btn btn--ghost" onClick={() => void exportModel()}>
        <Icon name="download" />
        {t('topbar.export')}
      </button>
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
