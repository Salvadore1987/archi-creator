import { useEffect, useState } from 'react';
import { useCanEdit } from '../app/session';
import { useUi } from '../app/uiState';
import { layerToken } from '../canvas/style';
import { layerName, t } from '../i18n';
import { useEditor } from '../model/store';
import { BulkPanel } from './BulkPanel';
import { DescriptionTab } from './DescriptionTab';
import { PropertiesTab } from './PropertiesTab';
import './panels.css';

export type InspectorTab = 'properties' | 'description';

/**
 * Правая панель: «Свойства» правят объект модели, «Описание» читается.
 * Вкладка по умолчанию зависит от роли: читателю нужна карточка, а не форма.
 */
export function Inspector({ defaultTab }: { defaultTab: InspectorTab }) {
  const [tab, setTab] = useState<InspectorTab>(defaultTab);
  const ids = useEditor((s) => s.selection.ids);
  const doc = useEditor((s) => s.doc!);
  const canEdit = useCanEdit();
  useEffect(() => setTab(defaultTab), [defaultTab]);
  const request = useUi((s) => s.inspectorRequest);
  useEffect(() => {
    if (request) setTab(request.tab);
  }, [request]);

  const single = ids.length === 1 ? ids[0]! : undefined;
  const element = single ? doc.elements[single] : undefined;

  return (
    <div className="inspector" data-testid="inspector">
      <div className="panel-head">
        <div className="rtabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'properties'}
            className={`btn btn--small${tab === 'properties' ? ' is-on' : ''}`}
            onClick={() => setTab('properties')}
          >
            {t('props.tabProperties')}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'description'}
            className={`btn btn--small${tab === 'description' ? ' is-on' : ''}`}
            onClick={() => setTab('description')}
          >
            {t('props.tabDescription')}
          </button>
        </div>
        <span className="spacer" />
        {element && (
          <span className="chip layer-chip" style={{ color: `var(--layer-${layerToken(element.layer)}-border)` }}>
            {layerName(element.layer)}
          </span>
        )}
      </div>
      {ids.length > 1 ? (
        <BulkPanel ids={ids} />
      ) : !single ? (
        <div className="inspector__empty">{t('props.noSelection')}</div>
      ) : tab === 'properties' ? (
        <PropertiesTab key={single} id={single} editable={canEdit} />
      ) : (
        <DescriptionTab key={single} id={single} />
      )}
    </div>
  );
}
