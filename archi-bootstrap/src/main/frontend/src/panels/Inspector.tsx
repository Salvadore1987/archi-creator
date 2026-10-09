import { useState } from 'react';
import { t } from '../i18n';

export type InspectorTab = 'properties' | 'description';

export function Inspector({ defaultTab }: { defaultTab: InspectorTab }) {
  const [tab, setTab] = useState<InspectorTab>(defaultTab);
  return (
    <div className="panel-head">
      <button type="button" className={`btn btn--small${tab === 'properties' ? ' is-on' : ''}`} onClick={() => setTab('properties')}>
        {t('props.tabProperties')}
      </button>
      <button type="button" className={`btn btn--small${tab === 'description' ? ' is-on' : ''}`} onClick={() => setTab('description')}>
        {t('props.tabDescription')}
      </button>
    </div>
  );
}
