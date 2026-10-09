import { useMemo, useState } from 'react';
import type { Uuid } from '../api/types';
import { Icon } from '../app/Icon';
import { t } from '../i18n';
import type { ModelDoc } from '../model/doc';
import { rootOf } from '../model/ops';
import { folderPath } from './buildTree';
import './dialogs.css';

export interface MenuItem {
  label: string;
  icon: Parameters<typeof Icon>[0]['name'];
  danger?: boolean;
  hint?: string;
  run(): void;
}

export function ContextMenu({ x, y, title, items, onClose }: { x: number; y: number; title: string; items: MenuItem[]; onClose(): void }) {
  return (
    <div className="ctx-backdrop" onClick={onClose} onContextMenu={(event) => (event.preventDefault(), onClose())}>
      <div className="ctx" role="menu" style={{ left: x, top: y }} onClick={(event) => event.stopPropagation()}>
        <div className="ctx__head">{title}</div>
        {items.map((item) => (
          <button
            key={item.label}
            type="button"
            role="menuitem"
            className={`ctx__i${item.danger ? ' ctx__i--danger' : ''}`}
            title={item.hint}
            onClick={() => {
              onClose();
              item.run();
            }}
          >
            <Icon name={item.icon} />
            {item.label}
          </button>
        ))}
      </div>
    </div>
  );
}

/** Выбор папки по полному пути: на тридцати папках быстрее, чем раскрывать дерево. */
export function MoveDialog({ doc, ids, onMove, onClose }: { doc: ModelDoc; ids: Uuid[]; onMove(folderId: Uuid): void; onClose(): void }) {
  const [query, setQuery] = useState('');
  const root = rootOf(doc, ids[0]!);
  const folders = useMemo(
    () =>
      Object.values(doc.folders)
        .filter((f) => rootOf(doc, f.id) === root && !ids.includes(f.id))
        .map((f) => ({ id: f.id, path: folderPath(doc, f.id) }))
        .sort((a, b) => a.path.localeCompare(b.path, 'ru')),
    [doc, root, ids],
  );
  const shown = folders.filter((f) => f.path.toLowerCase().includes(query.trim().toLowerCase()));
  return (
    <div className="pop-backdrop" onClick={onClose}>
      <div className="pop" role="dialog" aria-label={t('tree_ops.moveTitle')} onClick={(event) => event.stopPropagation()}>
        <h4>{t('tree_ops.moveTitle')}</h4>
        <input
          className="field__input"
          autoFocus
          value={query}
          placeholder={t('tree_ops.moveSearch')}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Escape') onClose();
            if (event.key === 'Enter' && shown[0]) onMove(shown[0].id);
          }}
        />
        <div className="pop__list scroll">
          {shown.map((folder) => (
            <button key={folder.id} type="button" className="pop__fi" onClick={() => onMove(folder.id)}>
              <Icon name="folder" />
              <span>{folder.path}</span>
            </button>
          ))}
        </div>
        <p className="pop__note">{t('tree_ops.moveNotAllowed')}</p>
        <div className="pop__acts">
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            {t('tree_ops.cancel')}
          </button>
        </div>
      </div>
    </div>
  );
}

/** Подтверждение удаления: число затронутых связей видно до нажатия, а не после отказа сервера. */
export function DeleteDialog(props: {
  count: number;
  relationships: number;
  onView: number;
  onConfirm(): void;
  onClose(): void;
}) {
  return (
    <div className="pop-backdrop" onClick={props.onClose}>
      <div className="pop" role="alertdialog" data-testid="delete-dialog" onClick={(event) => event.stopPropagation()}>
        <h4>{t('tree_ops.deleteTitle', { n: props.count })}</h4>
        {props.relationships > 0 && <p>{t('tree_ops.deleteRelations', { n: props.relationships })}</p>}
        {props.onView > 0 && <p>{t('tree_ops.deleteOnView', { n: props.onView })}</p>}
        <div className="pop__acts">
          <button type="button" className="btn btn--ghost" onClick={props.onClose}>
            {t('tree_ops.cancel')}
          </button>
          <button type="button" className="btn btn--danger" autoFocus onClick={props.onConfirm}>
            {t('tree_ops.deleteConfirm')}
          </button>
        </div>
      </div>
    </div>
  );
}
