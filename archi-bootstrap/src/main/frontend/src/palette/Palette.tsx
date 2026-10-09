import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/endpoints';
import { NotationIcon } from '../app/Icon';
import { useCanEdit } from '../app/session';
import { paletteDragData, DRAG_PALETTE_TYPE } from '../canvas/palette-drag';
import { shapeOf } from '../canvas/shapes';
import { layerToken } from '../canvas/style';
import { layerName, t, typeName } from '../i18n';
import { useEditor } from '../model/store';
import './palette.css';

const LAYERS = ['BUSINESS', 'APPLICATION', 'TECHNOLOGY'];

/**
 * Палитра типов фазы 1 из каталога сервера. Слой переключается сам на слой
 * выделенного элемента: чаще всего рядом с элементом ставят соседа его слоя.
 */
export function Palette() {
  const types = useQuery({ queryKey: ['metamodel', 'elements'], queryFn: api.elementTypes, staleTime: Infinity });
  const selectedLayer = useEditor((s) => {
    const id = s.selection.ids[s.selection.ids.length - 1];
    return id ? s.doc?.elements[id]?.layer : undefined;
  });
  const canEdit = useCanEdit();
  const [layer, setLayer] = useState('APPLICATION');
  useEffect(() => {
    if (selectedLayer && LAYERS.includes(selectedLayer)) setLayer(selectedLayer);
  }, [selectedLayer]);

  const items = useMemo(
    () => (types.data ?? []).filter((type) => type.supported && type.kind === 'ELEMENT' && type.layer === layer),
    [types.data, layer],
  );

  return (
    <section className="palette-panel" data-testid="palette">
      <div className="panel-head">
        {t('palette.title')}
        <span className="spacer" />
        <span className="palette__hint">{t('palette.hint')}</span>
      </div>
      <div className="layer-switch">
        {LAYERS.map((value) => (
          <button
            key={value}
            type="button"
            className={`btn btn--small${layer === value ? ' is-on' : ''}`}
            onClick={() => setLayer(value)}
          >
            {layerName(value)}
          </button>
        ))}
      </div>
      <div className="palette scroll">
        {items.map((type) => (
          <div
            key={type.archiType}
            className={`pal${canEdit ? '' : ' pal--off'}`}
            draggable={canEdit}
            data-archi-type={type.archiType}
            onDragStart={(event) => {
              event.dataTransfer.effectAllowed = 'copy';
              event.dataTransfer.setData(
                DRAG_PALETTE_TYPE,
                paletteDragData({ archiType: type.archiType, layer: type.layer, defaultName: typeName(type.archiType) }),
              );
            }}
          >
            <span className="pal__dot" style={{ '--bg': `var(--layer-${layerToken(type.layer)})`, '--bd': `var(--layer-${layerToken(type.layer)}-border)` } as React.CSSProperties}>
              <NotationIcon spriteId={shapeOf(type.archiType).corner_icon?.sprite_id ?? 'i-stub'} />
            </span>
            {typeName(type.archiType)}
          </div>
        ))}
      </div>
    </section>
  );
}
