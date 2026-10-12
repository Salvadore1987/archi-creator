import type { Uuid } from '../api/types';
import { NotationIcon } from '../app/Icon';
import { useCanEdit } from '../app/session';
import { shapeOf } from '../canvas/shapes';
import { layerToken } from '../canvas/style';
import { t } from '../i18n';
import { useEditor } from '../model/store';
import { treeCommandsBus } from '../tree/bus';

/** Сводка группового выделения: сколько, каких слоёв, из каких папок, и общие действия. */
export function BulkPanel({ ids }: { ids: Uuid[] }) {
  const doc = useEditor((s) => s.doc!);
  const canEdit = useCanEdit();
  const elements = ids.map((id) => doc.elements[id]).filter((e) => !!e);
  const layers = new Set(elements.map((e) => e.layer));
  const folders = new Set(ids.map((id) => doc.elements[id]?.folderId ?? doc.relationships[id]?.folderId ?? doc.folders[id]?.parentId));

  return (
    <div className="bulk scroll" data-testid="bulk">
      <h4>{t('bulk.title', { n: ids.length })}</h4>
      <div className="read__meta">
        <span className="chip">{t('bulk.layers', { n: layers.size })}</span>
        <span className="chip">{t('bulk.folders', { n: folders.size })}</span>
      </div>
      <div className="bulk__list">
        {ids.map((id) => {
          const element = doc.elements[id];
          const name = element?.name ?? doc.relationships[id]?.name ?? doc.folders[id]?.name ?? doc.views[id]?.name ?? id;
          return (
            <div className="bulk__li" key={id}>
              {element && (
                <span style={{ color: `var(--layer-${layerToken(element.layer)}-border)` }}>
                  <NotationIcon spriteId={shapeOf(element.archiType).corner_icon?.sprite_id ?? 'i-stub'} />
                </span>
              )}
              <span>{name}</span>
            </div>
          );
        })}
      </div>
      {canEdit && (
        <div className="row">
          <button type="button" className="btn btn--ghost" onClick={() => treeCommandsBus.emit('move', ids)}>
            {t('bulk.move')}
          </button>
          <button type="button" className="btn btn--danger" onClick={() => treeCommandsBus.emit('delete', ids)}>
            {t('bulk.delete')}
          </button>
        </div>
      )}
    </div>
  );
}
