import { t } from '../i18n';
import { unsavedCount } from '../model/history';
import { useEditor } from '../model/store';
import { describeError } from './errors';
import { useUi } from './uiState';

export function StatusBar() {
  const doc = useEditor((s) => s.doc!);
  const activeViewId = useEditor((s) => s.activeViewId);
  const selection = useEditor((s) => s.selection.ids);
  const history = useEditor((s) => s.history);
  const findings = useEditor((s) => s.findings);
  const lastVersion = useEditor((s) => s.lastVersion);
  const save = useEditor((s) => s.save);
  const toggleHistory = useUi((s) => s.toggleHistory);
  const unsaved = unsavedCount(history);

  const view = activeViewId ? doc.views[activeViewId] : undefined;
  const loaded = activeViewId ? doc.loadedViews[activeViewId] : undefined;
  const selectedName =
    selection.length === 1
      ? (doc.elements[selection[0]!]?.name ?? doc.relationships[selection[0]!]?.name ?? doc.folders[selection[0]!]?.name)
      : undefined;
  const errors = findings.filter((f) => f.severity === 'ERROR').length;
  const warnings = findings.length - errors;

  return (
    <footer className="status">
      {view && <span>{view.name}</span>}
      {loaded && (
        <>
          <span>{t('status.objects', { n: Object.keys(loaded.nodes).length })}</span>
          <span>{t('status.edges', { n: Object.keys(loaded.edges).length })}</span>
        </>
      )}
      {selection.length === 1 && selectedName !== undefined && <span>{t('status.selected', { name: selectedName })}</span>}
      {selection.length > 1 && <span>{t('status.selectedMany', { n: selection.length })}</span>}
      <button type="button" className="status__dirty" data-testid="unsaved" onClick={() => toggleHistory()}>
        {unsaved > 0 ? t('status.unsaved', { n: unsaved }) : t('history.title')}
      </button>
      {save.kind === 'error' && <span className="status__error">{describeError(save.error)}</span>}
      <span className="grow" />
      <span className={errors > 0 ? 'chip chip--err' : warnings > 0 ? 'chip chip--warn' : 'chip chip--ok'}>
        {findings.length > 0 ? t('status.findings', { errors, warnings }) : t('status.noFindings')}
      </span>
      {lastVersion && (
        <span>
          {t('status.savedAt', { time: new Date(lastVersion.createdAt).toLocaleTimeString('ru-RU') })} ·{' '}
          <b>{t('status.version', { n: lastVersion.versionNo })}</b>
        </span>
      )}
    </footer>
  );
}
