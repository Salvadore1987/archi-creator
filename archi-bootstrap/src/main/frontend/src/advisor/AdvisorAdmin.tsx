import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../api/endpoints';
import type { UsageRow } from '../api/types';
import { describeError } from '../app/errors';
import { useToast } from '../app/toast';
import { t, type MessageKey } from '../i18n';
import './advisor.css';

/**
 * Настройки помощника для администратора: выключатель, месячный лимит и расход
 * за месяц по пользователям и функциям. Повышение лимита — единственный способ
 * вернуть помощника до конца месяца: расход не списывается.
 */
export function AdvisorAdmin() {
  const client = useQueryClient();
  const toast = useToast((s) => s.show);
  const settings = useQuery({ queryKey: ['advisor-settings'], queryFn: api.advisorSettings });
  const usage = useQuery({ queryKey: ['advisor-usage'], queryFn: api.advisorUsage });
  const [enabled, setEnabled] = useState(false);
  const [limit, setLimit] = useState('');
  useEffect(() => {
    if (settings.data) {
      setEnabled(settings.data.enabled);
      setLimit(String(settings.data.monthlyTokenLimit));
    }
  }, [settings.data]);

  const save = useMutation({
    mutationFn: () => api.changeAdvisorSettings({ enabled, monthlyTokenLimit: Number(limit) }),
    onSuccess: () => {
      toast(t('advisor.admin.saved'));
      void client.invalidateQueries({ queryKey: ['advisor-usage'] });
      void client.invalidateQueries({ queryKey: ['advisor-status'] });
    },
    onError: (error) => toast(describeError(error), { tone: 'error' }),
  });

  if (settings.isError) return null;

  return (
    <section className="ai-admin" data-testid="advisor-admin">
      <h2>{t('advisor.admin.title')}</h2>
      <form
        className="ai-admin__form"
        onSubmit={(event) => {
          event.preventDefault();
          save.mutate();
        }}
      >
        <label className="ai-admin__check">
          <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />
          {t('advisor.admin.enabled')}
        </label>
        <p className="ai__muted">{t('advisor.admin.enabledHint')}</p>
        <label className="ai-admin__field">
          {t('advisor.admin.limit')}
          <input
            className="field__input"
            type="number"
            min={1}
            value={limit}
            onChange={(event) => setLimit(event.target.value)}
          />
        </label>
        <button type="submit" className="btn btn--primary" disabled={save.isPending || !(Number(limit) > 0)}>
          {t('advisor.admin.save')}
        </button>
      </form>
      {usage.data && (
        <>
          <p data-testid="advisor-usage">
            {t('advisor.admin.usage', {
              period: usage.data.period,
              consumed: usage.data.consumedTokens.toLocaleString('ru-RU'),
              limit: usage.data.limitTokens.toLocaleString('ru-RU'),
            })}
            {' · '}
            {t(`advisor.admin.status.${usage.data.budgetStatus}` as MessageKey)}
          </p>
          <UsageTable title={t('advisor.admin.byUser')} rows={usage.data.byUser} />
          <UsageTable title={t('advisor.admin.byFunction')} rows={usage.data.byFunction} functions />
        </>
      )}
    </section>
  );
}

function UsageTable({ title, rows, functions = false }: { title: string; rows: UsageRow[]; functions?: boolean }) {
  if (rows.length === 0) return null;
  return (
    <table className="ai-admin__table">
      <caption>{title}</caption>
      <thead>
        <tr>
          <th>{t('advisor.admin.columnKey')}</th>
          <th>{t('advisor.admin.columnRequests')}</th>
          <th>{t('advisor.admin.columnRejected')}</th>
          <th>{t('advisor.admin.columnTokens')}</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.key}>
            <td>{functions ? t(`advisor.fn.${row.key.toLowerCase()}` as MessageKey) : row.key}</td>
            <td>{row.requests}</td>
            <td>{row.rejected}</td>
            <td>{(row.promptTokens + row.completionTokens).toLocaleString('ru-RU')}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
