import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { api } from '../api/endpoints';
import type { Me } from '../api/types';
import { signOut, type AuthMode } from '../auth/session';
import { t } from '../i18n';
import { uuidv7 } from '../model/ids';
import { describeError } from './errors';
import { Icon } from './Icon';
import { navigateToModel } from './route';
import { isEditorRole } from './session';
import { useToast } from './toast';
import { UserBadge } from './UserBadge';

export function ModelList({ me, auth }: { me?: Me; auth: AuthMode }) {
  const client = useQueryClient();
  const models = useQuery({ queryKey: ['models'], queryFn: api.listModels });
  const file = useRef<HTMLInputElement>(null);
  const [name, setName] = useState('');
  const toast = useToast((s) => s.show);
  const canEdit = isEditorRole(me?.roles ?? []);

  const importing = useMutation({
    mutationFn: (selected: File) => api.importModel(selected, uuidv7()),
    onSuccess: (report) => {
      toast(t('models.importDone'));
      void client.invalidateQueries({ queryKey: ['models'] });
      if (report.modelId) navigateToModel(report.modelId);
    },
    onError: (error) => toast(describeError(error), { tone: 'error' }),
  });

  const creating = useMutation({
    mutationFn: (modelName: string) => api.createModel(modelName, uuidv7()),
    onSuccess: (model) => navigateToModel(model.id),
    onError: (error) => toast(describeError(error), { tone: 'error' }),
  });

  return (
    <div className="screen">
      <header className="topbar">
        <div className="brand">
          {t('app.brand')}
          <em>{t('app.brandAccent')}</em>
          {t('app.brandTail')}
        </div>
        <div className="grow" />
        <UserBadge me={me} onSignOut={auth.kind === 'oidc' ? () => void signOut(auth) : undefined} />
      </header>
      <main className="screen__body">
        <div className="row">
          <h1>{t('models.title')}</h1>
          <div className="grow" />
          {canEdit && (
            <>
              <input
                ref={file}
                type="file"
                accept=".archimate,.xml"
                hidden
                onChange={(event) => {
                  const selected = event.target.files?.[0];
                  if (selected) importing.mutate(selected);
                  event.target.value = '';
                }}
              />
              <button type="button" className="btn btn--ghost" disabled={importing.isPending} onClick={() => file.current?.click()}>
                <Icon name="upload" />
                {importing.isPending ? t('models.importing') : t('models.import')}
              </button>
            </>
          )}
        </div>
        {canEdit && (
          <form
            className="row"
            onSubmit={(event) => {
              event.preventDefault();
              if (name.trim()) creating.mutate(name.trim());
            }}
          >
            <input
              className="field__input"
              value={name}
              placeholder={t('models.createPlaceholder')}
              onChange={(event) => setName(event.target.value)}
            />
            <button type="submit" className="btn btn--primary" disabled={!name.trim() || creating.isPending}>
              {t('models.create')}
            </button>
          </form>
        )}
        {models.isLoading && <div className="notice">{t('app.loading')}</div>}
        {models.isError && (
          <div className="notice notice--error">{t('app.backendDown', { reason: describeError(models.error) })}</div>
        )}
        {models.data?.length === 0 && <div className="notice">{t('models.empty')}</div>}
        <div className="model-list">
          {models.data?.map((model) => (
            <button key={model.id} type="button" className="model-row" onClick={() => navigateToModel(model.id)}>
              <div className="grow">
                <b>{model.name}</b>
                <div>
                  <small>
                    {model.updatedAt ? t('models.updated', { when: new Date(model.updatedAt).toLocaleString('ru-RU') }) : ''}
                    {model.createdBy ? ` · ${t('models.author', { who: model.createdBy })}` : ''}
                  </small>
                </div>
              </div>
              <Icon name="chevron" />
            </button>
          ))}
        </div>
      </main>
    </div>
  );
}
