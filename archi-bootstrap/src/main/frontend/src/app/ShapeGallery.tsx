import { knownShapeTypes } from '../canvas/shapes';
import { NodeShape } from '../canvas/NodeShape';
import { t, typeName } from '../i18n';

const LAYER_OF: Array<[RegExp, string]> = [
  [/Business|Contract|Representation|Product/, 'BUSINESS'],
  [/Application|DataObject/, 'APPLICATION'],
  [/Technology|Node|Device|SystemSoftware|Path|CommunicationNetwork|Artifact/, 'TECHNOLOGY'],
];

/** Типы вне фазы 1 — рисуются заглушкой. */
const STUBS: Array<[string, string]> = [
  ['archimate:Capability', 'STRATEGY'],
  ['archimate:Goal', 'MOTIVATION'],
  ['archimate:WorkPackage', 'IMPLEMENTATION'],
];

/** Образец собственного стиля объекта из файла Archi: заливка и шрифт сильнее токена слоя. */
const OWN_STYLE = { fillColor: '#c0c0c0', font: '1|Segoe UI|10.0|1|WINDOWS|1|-13|0|0|0|700|0|0|0|0|3|2|1|34|Segoe UI' };

/**
 * Галерея силуэтов: каждый тип, выделенный и обычный, заглушки и собственный
 * стиль. Опорная страница визуальных проверок и сравнения с Archi глазами.
 */
export function ShapeGallery() {
  const types = knownShapeTypes().filter((type) => !/Group|Note|Junction|DiagramModelReference/.test(type));
  const layer = (type: string) => LAYER_OF.find(([pattern]) => pattern.test(type))?.[1] ?? 'OTHER';
  return (
    <div className="gallery" data-testid="gallery">
      {types.map((type) => (
        <figure key={type} data-type={type}>
          <NodeShape archiType={type} layer={layer(type)} width={150} height={55} label={typeName(type)} />
        </figure>
      ))}
      {STUBS.map(([type, stubLayer]) => (
        <figure key={type} data-type={type}>
          <NodeShape archiType={type} layer={stubLayer} width={150} height={55} label={typeName(type)} opaque subtitle={t('canvas.opaque')} />
        </figure>
      ))}
      <figure data-type="own-style">
        <NodeShape archiType="archimate:ApplicationComponent" layer="APPLICATION" width={150} height={55} label={t('props.style')} style={OWN_STYLE} />
      </figure>
      <figure data-type="own-style-selected">
        <NodeShape archiType="archimate:ApplicationComponent" layer="APPLICATION" width={150} height={55} label={t('props.style')} style={OWN_STYLE} selected />
      </figure>
      <figure data-type="selected">
        <NodeShape archiType="archimate:ApplicationComponent" layer="APPLICATION" width={150} height={55} label={typeName('archimate:ApplicationComponent')} selected />
      </figure>
      <figure data-type="group">
        <NodeShape archiType="archimate:Group" width={310} height={90} label={typeName('archimate:Group')} />
      </figure>
      <figure data-type="note">
        <NodeShape archiType="archimate:Note" width={150} height={70} label={typeName('archimate:Note')} />
      </figure>
      <figure data-type="junction">
        <NodeShape archiType="archimate:Junction" width={15} height={15} label="" />
      </figure>
    </div>
  );
}
