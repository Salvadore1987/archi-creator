import { Icon } from '../app/Icon';
import { useCanEdit } from '../app/session';
import { useUi } from '../app/uiState';
import { t } from '../i18n';
import { useEditor } from '../model/store';
import './history.css';

/**
 * Панель истории: операции сессии, текущая позиция и точка сохранения.
 * Клик по строке возвращает модель в состояние после этой операции.
 */
export function HistoryPanel() {
  const open = useUi((s) => s.historyOpen);
  const toggle = useUi((s) => s.toggleHistory);
  const history = useEditor((s) => s.history);
  const goTo = useEditor((s) => s.goTo);
  const canEdit = useCanEdit();
  if (!open) return null;

  // Строка 0 — открытие модели; строка i — состояние после операции i.
  const rows = [
    { label: t('history.start'), at: undefined as number | undefined },
    ...history.ops.map((op) => ({ label: op.label, at: op.at as number | undefined })),
  ];

  return (
    <div className="history-pop" data-testid="history-panel" role="dialog" aria-label={t('history.title')}>
      <div className="panel-head">
        <Icon name="history" />
        {t('history.title')}
        <span className="spacer" />
        <button type="button" className="btn btn--icon btn--small" title={t('history.close')} onClick={() => toggle(false)}>
          <Icon name="close" />
        </button>
      </div>
      {history.detour.length > 0 && (
        <div className="history-pop__detour">
          {history.detour.map((op) => (
            <div key={op.id}>{t('history.pendingRevert', { label: op.label })}</div>
          ))}
        </div>
      )}
      <ol className="history-pop__list scroll">
        {history.ops.length === 0 && <li className="history-pop__empty">{t('history.empty')}</li>}
        {rows.map((row, index) => {
          const current = index === history.pointer;
          const undone = index > history.pointer;
          return (
            <li key={index}>
              <button
                type="button"
                className={`hrow${current ? ' is-cur' : ''}${undone ? ' is-undone' : ''}`}
                disabled={!canEdit}
                data-position={index}
                onClick={() => goTo(index)}
              >
                <span className="hrow__n">{index}</span>
                <span className="hrow__label">{row.label}</span>
                {index === history.saved && <span className="chip chip--ok">{t('history.saved')}</span>}
                {current && <span className="chip chip--lock">{t('history.current')}</span>}
                {row.at !== undefined && <span className="hrow__time">{new Date(row.at).toLocaleTimeString('ru-RU')}</span>}
              </button>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
