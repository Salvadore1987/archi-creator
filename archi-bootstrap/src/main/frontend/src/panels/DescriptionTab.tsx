import { useMemo, useState } from 'react';
import type { Uuid } from '../api/types';
import { Icon, NotationIcon } from '../app/Icon';
import { shapeOf } from '../canvas/shapes';
import { layerToken } from '../canvas/style';
import { useOpenView } from '../canvas/useOpenView';
import { layerName, relationVerb, t, typeName } from '../i18n';
import { placementIndex } from '../model/doc';
import { useEditor } from '../model/store';
import { folderPath } from '../tree/buildTree';
import { impactOf, relationsOf } from './impact';

const FIRST_RELATIONS = 7;
const IMPACT_NAMES = 3;

/**
 * Карточка элемента для чтения: что это, с чем связано и что сломается при
 * отказе. Поля не правятся ни одной ролью — правка идёт через «Свойства».
 * Клик по связи переводит выбор на второй конец.
 */
export function DescriptionTab({ id }: { id: Uuid }) {
  const doc = useEditor((s) => s.doc!);
  const findings = useEditor((s) => s.findings);
  const activeViewId = useEditor((s) => s.activeViewId);
  const select = useEditor((s) => s.select);
  const openView = useOpenView();
  const [showAll, setShowAll] = useState(false);

  const object = doc.elements[id] ?? doc.relationships[id];
  const element = doc.elements[id];
  const relations = useMemo(() => relationsOf(doc, id), [doc, id]);
  const impact = useMemo(() => (element ? impactOf(doc, id) : null), [doc, id, element]);
  const views = placementIndex(doc).get(id) ?? [];
  const hasFinding = object ? findings.some((f) => f.targetId === object.archiId) : false;

  if (!object) {
    return <div className="inspector__empty">{t('props.noSelection')}</div>;
  }

  const visible = showAll ? relations : relations.slice(0, FIRST_RELATIONS);
  const endName = (otherId: Uuid) => doc.elements[otherId]?.name ?? doc.relationships[otherId]?.name ?? typeName(doc.relationships[otherId]?.archiType ?? '');

  return (
    <div className="read scroll" data-testid="description">
      <div>
        <h2>{object.name || typeName(object.archiType)}</h2>
        <div className="read__meta">
          <span className="chip">{typeName(object.archiType)}</span>
          {element && (
            <span className="chip" style={{ color: `var(--layer-${layerToken(element.layer)}-border)` }}>
              {layerName(element.layer)}
            </span>
          )}
          {hasFinding && <span className="chip chip--err">{t('description.finding')}</span>}
          <span className="chip">{t('description.viewsCount', { n: views.length })}</span>
        </div>
      </div>

      <p className="read__doc">
        {object.documentation?.trim()
          ? object.documentation
          : t('description.noDocumentation', { type: typeName(object.archiType), path: folderPath(doc, object.folderId) })}
      </p>

      {relations.length > 0 && (
        <section>
          <h3>{`${t('description.relations')} · ${relations.length}`}</h3>
          <div className="rel-list">
            {visible.map(({ relationship, outgoing, otherId }) => {
              const other = doc.elements[otherId];
              return (
                <button
                  key={`${relationship.id}-${outgoing}`}
                  type="button"
                  className={`rel${outgoing ? '' : ' rel--in'}`}
                  data-testid="relation-row"
                  onClick={() => select([otherId], 'external')}
                >
                  <span className="rel__dir">{outgoing ? '→' : '←'}</span>
                  {other && (
                    <span className="rel__dot" style={{ color: `var(--layer-${layerToken(other.layer)}-border)` }}>
                      <NotationIcon spriteId={shapeOf(other.archiType).corner_icon?.sprite_id ?? 'i-stub'} />
                    </span>
                  )}
                  <span className="rel__n">{endName(otherId)}</span>
                  <span className="rel__k">{relationVerb(relationship.archiType, outgoing)}</span>
                </button>
              );
            })}
          </div>
          {!showAll && relations.length > FIRST_RELATIONS && (
            <button type="button" className="read__more" onClick={() => setShowAll(true)}>
              {t('description.showAll', { n: relations.length })}
            </button>
          )}
        </section>
      )}

      {impact && (
        <section>
          <h3>{t('description.impact')}</h3>
          {impact.direct.length === 0 && impact.nested === 0 ? (
            <p className="read__muted">{t('description.impactNone')}</p>
          ) : (
            <p data-testid="impact">
              {impact.direct.length > 0 &&
                t('description.impactDirect', {
                  n: impact.direct.length,
                  names: impact.direct
                    .slice(0, IMPACT_NAMES)
                    .map((e) => `«${e.name}»`)
                    .join(', '),
                }) +
                  (impact.direct.length > IMPACT_NAMES
                    ? t('description.impactMore', { n: impact.direct.length - IMPACT_NAMES })
                    : '') +
                  '. '}
              {impact.secondLevel > 0 && `${t('description.impactSecond', { n: impact.secondLevel })} `}
              {impact.nested > 0 && t('description.impactNested', { n: impact.nested })}
            </p>
          )}
        </section>
      )}

      {object.properties.length > 0 && (
        <section>
          <h3>{t('description.properties')}</h3>
          <div className="kvs">
            {object.properties.map((p, i) => (
              <div className="kv" key={i}>
                <b>{p.key}</b>
                <span>{p.value}</span>
              </div>
            ))}
          </div>
        </section>
      )}

      <section>
        <h3>{t('description.views')}</h3>
        {views.length === 0 ? (
          <p className="read__muted">{t('description.notPlaced')}</p>
        ) : (
          <div className="vlist">
            {views.map((viewId) => (
              <button
                key={viewId}
                type="button"
                className={`vrow${viewId === activeViewId ? ' is-cur' : ''}`}
                onClick={() => void openView(viewId).then(() => select([id], 'external'))}
              >
                <Icon name="view" />
                <span>{doc.views[viewId]?.name}</span>
              </button>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
