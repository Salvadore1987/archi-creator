import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/endpoints';
import { NotationIcon } from '../app/Icon';
import { useCanEdit } from '../app/session';
import { paletteDragData, DRAG_PALETTE_TYPE } from '../canvas/palette-drag';
import { shapeOf } from '../canvas/shapes';
import { layerToken } from '../canvas/style';
import { layerName, t, typeName } from '../i18n';
import { useAdvisor } from '../advisor/store';
import { useEditor } from '../model/store';
import './palette.css';

const LAYERS = ['BUSINESS', 'APPLICATION', 'TECHNOLOGY', 'OTHER'];

/** Объекты представления без элемента модели — в «Прочем», рядом с соединителем. */
const ANNOTATIONS = [
  { archiType: 'archimate:Group', kind: 'GROUP' as const },
  { archiType: 'archimate:Note', kind: 'NOTE' as const },
];

interface PaletteItem {
  archiType: string;
  layer: string;
  kind?: 'GROUP' | 'NOTE';
  defaultName: string;
}

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
  const planFilter = useAdvisor((s) => s.paletteFilter);
  const clearPlanFilter = useAdvisor((s) => s.clearPaletteFilter);
  const [layer, setLayer] = useState('APPLICATION');
  useEffect(() => {
    if (selectedLayer && LAYERS.includes(selectedLayer)) setLayer(selectedLayer);
  }, [selectedLayer]);

  const items = useMemo<PaletteItem[]>(() => {
    // План помощника сужает палитру до своих типов, во всех слоях сразу: схему собирает архитектор.
    if (planFilter) {
      return (types.data ?? [])
        .filter((type) => type.supported && planFilter.includes(type.archiType))
        .map((type) => ({ archiType: type.archiType, layer: type.layer, defaultName: typeName(type.archiType) }));
    }
    const elements = (types.data ?? [])
      .filter((type) => type.supported && type.layer === layer)
      // Соединитель в Archi безымянный.
      .map((type) => ({ archiType: type.archiType, layer: type.layer, defaultName: type.kind === 'JUNCTION' ? '' : typeName(type.archiType) }));
    if (layer !== 'OTHER') return elements;
    return [
      ...ANNOTATIONS.map((a) => ({ ...a, layer: 'OTHER', defaultName: a.kind === 'GROUP' ? typeName(a.archiType) : '' })),
      ...elements,
    ];
  }, [types.data, layer, planFilter]);

  return (
    <section className="palette-panel" data-testid="palette">
      <div className="panel-head">
        {t('palette.title')}
        <span className="spacer" />
        <span className="palette__hint">{t('palette.hint')}</span>
      </div>
      {planFilter ? (
        <div className="layer-switch" data-testid="palette-plan-filter">
          <span className="chip">{t('palette.planFilter')}</span>
          <button type="button" className="btn btn--small" onClick={clearPlanFilter}>
            {t('palette.planFilterReset')}
          </button>
        </div>
      ) : (
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
      )}
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
                paletteDragData({ archiType: type.archiType, layer: type.layer, defaultName: type.defaultName, kind: type.kind }),
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
