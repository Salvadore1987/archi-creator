import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/endpoints';
import type { MergeConflict, MergeSide } from '../api/types';
import { describeError } from '../app/errors';
import { Icon } from '../app/Icon';
import { useToast } from '../app/toast';
import { t, tOptional, typeName } from '../i18n';
import { unsavedCount } from '../model/history';
import { useEditor } from '../model/store';
import { useMerge } from './store';
import './merge.css';

const SHORT_SHA = 7;

/** Значение конфликта для человека: удаление, перечень полей, число объектов или текст. */
function valueOf(conflict: MergeConflict, value: string | undefined): string {
  if (value === undefined) return t('merge.removed');
  if (conflict.field === '*') return t('merge.changedFields', { fields: value });
  if (conflict.field === 'diagram') return t('merge.diagramObjects', { n: value });
  return value === '' ? t('merge.empty') : value;
}

function ConflictCard({ conflict, onChoose, disabled }: {
  conflict: MergeConflict;
  onChoose(side: MergeSide): void;
  disabled: boolean;
}) {
  const field = conflict.field === '*' ? t('merge.fieldRemoval') : conflict.field;
  const option = (side: MergeSide, label: string, value: string | undefined) => (
    <label className={`merge-choice${conflict.resolution === side ? ' is-on' : ''}`}>
      <input
        type="radio"
        name={conflict.id}
        checked={conflict.resolution === side}
        disabled={disabled}
        data-testid={`choose-${side.toLowerCase()}`}
        onChange={() => onChoose(side)}
      />
      <span className="merge-choice__side">{label}</span>
      <span className="merge-choice__value">{valueOf(conflict, value)}</span>
    </label>
  );
  return (
    <li className="merge-conflict" data-testid="merge-conflict" data-conflict={conflict.id}>
      <div className="merge-conflict__head">
        <b>{conflict.name}</b>
        {conflict.type && <span className="merge-muted">{typeName(conflict.type)}</span>}
        <span className="chip chip--warn">{field}</span>
      </div>
      {conflict.base !== undefined && conflict.field !== '*' && (
        <div className="merge-muted">
          {t('merge.base')}: {valueOf(conflict, conflict.base)}
        </div>
      )}
      {option('OURS', t('merge.ours'), conflict.ours)}
      {option('THEIRS', t('merge.theirs'), conflict.theirs)}
    </li>
  );
}

/**
 * Правая панель слияния ветки предложения: ссылка, сводка, конфликты с выбором
 * по полю, каскад и применение. Холст в это время показывает итог слияния
 * отметками на узлах и контурами добавляемых узлов.
 */
export function MergePanel() {
  const modelId = useEditor((s) => s.doc!.model.id);
  const lock = useEditor((s) => s.lock);
  const unsaved = useEditor((s) => unsavedCount(s.history));
  const merge = useMerge();
  const queries = useQueryClient();
  const [applying, setApplying] = useState(false);
  const preview = merge.preview;

  const blocked = !preview
    ? undefined
    : lock.kind !== 'mine'
      ? t('merge.needLock')
      : unsaved > 0
        ? t('merge.needSave')
        : preview.conflicts.some((c) => !c.resolution)
          ? t('merge.needChoice')
          : !preview.applicable
            ? t('merge.notApplicable')
            : undefined;

  const apply = async () => {
    if (!preview) return;
    setApplying(true);
    try {
      const result = await api.merge(modelId, preview, merge.resolutions);
      useToast.getState().show(result.created ? t('merge.applied', { n: result.versionNo }) : t('merge.appliedUnchanged'));
      merge.hide();
      await queries.invalidateQueries({ queryKey: ['model', modelId] });
      await queries.invalidateQueries({ queryKey: ['versions', modelId] });
    } catch (error) {
      useToast.getState().show(describeError(error), { tone: 'error' });
    } finally {
      setApplying(false);
    }
  };

  const totals = preview?.summary.reduce(
    (sum, row) => ({ added: sum.added + row.added, removed: sum.removed + row.removed, changed: sum.changed + row.changed }),
    { added: 0, removed: 0, changed: 0 },
  );

  return (
    <div className="merge-panel" data-testid="merge-panel">
      <div className="panel-head">
        <b>{t('merge.title')}</b>
        <span className="spacer" />
        <button type="button" className="btn btn--icon" title={t('merge.close')} onClick={merge.hide}>
          <Icon name="close" />
        </button>
      </div>
      <form
        className="merge-ref"
        onSubmit={(event) => {
          event.preventDefault();
          if (merge.ref.trim()) void merge.build(modelId);
        }}
      >
        <label className="field__label" htmlFor="merge-ref">
          {t('merge.ref')}
        </label>
        <input
          id="merge-ref"
          className="field__input"
          data-testid="merge-ref"
          value={merge.ref}
          placeholder={t('merge.refPlaceholder')}
          onChange={(event) => merge.setRef(event.target.value)}
        />
        <button type="submit" className="btn" data-testid="merge-build" disabled={!merge.ref.trim() || merge.loading}>
          {merge.loading ? t('merge.building') : t('merge.build')}
        </button>
      </form>
      {merge.error && <div className="merge-error" role="alert">{merge.error}</div>}
      {preview && totals && (
        <div className="merge-body">
          <div className="merge-muted" data-testid="merge-commits">
            {t('merge.commits', {
              commit: preview.commitSha.slice(0, SHORT_SHA),
              base: preview.mergeBaseSha.slice(0, SHORT_SHA),
            })}
          </div>
          <div data-testid="merge-summary">
            {preview.unchanged ? t('merge.unchanged') : t('merge.summary', totals)}
          </div>
          <div className="merge-legend">
            <span className="merge-legend__item merge-legend__item--added">{t('merge.legendAdded')}</span>
            <span className="merge-legend__item merge-legend__item--removed">{t('merge.legendRemoved')}</span>
            <span className="merge-legend__item merge-legend__item--changed">{t('merge.legendChanged')}</span>
          </div>

          {preview.conflicts.length > 0 && (
            <section>
              <h4>{t('merge.conflicts', { n: preview.conflicts.length })}</h4>
              <p className="merge-muted">{t('merge.conflictsHint')}</p>
              <ul className="merge-list">
                {preview.conflicts.map((conflict) => (
                  <ConflictCard
                    key={conflict.id}
                    conflict={conflict}
                    disabled={merge.loading}
                    onChoose={(side) => void merge.choose(modelId, conflict.id, side)}
                  />
                ))}
              </ul>
            </section>
          )}

          {preview.cascade.length > 0 && (
            <section data-testid="merge-cascade">
              <h4>{t('merge.cascade', { n: preview.cascade.length })}</h4>
              <ul className="merge-list">
                {preview.cascade.map((item) => (
                  <li key={`${item.viewArchiId ?? ''}${item.archiId}`} className="merge-row merge-row--removed">
                    <b>{item.name || item.archiId}</b>
                    <span className="merge-muted">{tOptional(`merge.reason.${item.reason}`) ?? item.reason}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {preview.changes.length > 0 && (
            <section>
              <h4>{t('merge.changes')}</h4>
              <ul className="merge-list" data-testid="merge-changes">
                {preview.changes.map((change) => (
                  <li key={change.archiId} className={`merge-row merge-row--${change.change.toLowerCase()}`}>
                    <b>{change.name || change.archiId}</b>
                    <span className="merge-muted">
                      {tOptional(`merge.change.${change.change}`) ?? change.change}
                      {change.fields.length > 0 ? ` · ${change.fields.join(', ')}` : ''}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {preview.findings.length > 0 && (
            <div className="merge-muted">{t('merge.findings', { n: preview.findings.length })}</div>
          )}
        </div>
      )}
      {preview && (
        <div className="merge-foot">
          {blocked && <div className="merge-muted" data-testid="merge-blocked">{blocked}</div>}
          <button
            type="button"
            className="btn btn--primary"
            data-testid="merge-apply"
            disabled={!!blocked || applying || merge.loading}
            onClick={() => void apply()}
          >
            {applying ? t('merge.applying') : t('merge.apply')}
          </button>
        </div>
      )}
    </div>
  );
}
