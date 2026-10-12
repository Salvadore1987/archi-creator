import { memo } from 'react';
import { effectiveSize, outlinePath, resolveBox, shapeOf } from './shapes';
import { paintOf, textAlign, type ObjectStyle } from './style';

export interface NodeShapeProps {
  archiType: string;
  layer?: string | null;
  width: number;
  height: number;
  label: string;
  style?: ObjectStyle | null;
  selected?: boolean;
  /** Неподдержанный объект: виден и двигается, но не правится. */
  opaque?: boolean;
  /** Модельный тип у заглушки — подсказка, что именно не поддержано. */
  subtitle?: string;
}

/** Расстояние второй линии выделения от контура: двойная обводка, а не заливка. */
const SELECTION_GAP = 3;

/**
 * Фигура узла инлайн-SVG по общей геометрии. Цвет — из токенов слоя либо
 * собственного стиля объекта; выделение рисуется второй обводкой акцентом,
 * заливка не меняется, иначе пропал бы цвет слоя.
 */
export const NodeShape = memo(function NodeShape(props: NodeShapeProps) {
  const entry = shapeOf(props.archiType);
  const size = effectiveSize(props.archiType, props.width, props.height);
  const paint = paintOf(entry, props.layer, props.style);
  const icon = entry.corner_icon;
  const iconBox = icon ? resolveBox({ x: icon.x, y: icon.y, width: icon.size, height: icon.size }, size) : null;
  const labelBox = entry.label_box ? resolveBox(entry.label_box, size) : null;
  const align = props.style?.textAlignment != null ? textAlign(props.style.textAlignment) : entry.outline === 'tab' ? 'left' : 'center';

  return (
    <svg
      className="shape"
      width={size.width}
      height={size.height}
      viewBox={`0 0 ${size.width} ${size.height}`}
      overflow="visible"
      data-archi-type={props.archiType}
    >
      {props.selected && (
        <path
          className="shape__selection"
          d={outlinePath(entry, size, -SELECTION_GAP)}
          fill="none"
          stroke="var(--accent)"
          strokeWidth={1.5}
        />
      )}
      <path
        className="shape__outline"
        d={outlinePath(entry, size)}
        fill={paint.fill}
        stroke={props.selected ? 'var(--accent)' : paint.stroke}
        strokeWidth={1}
        strokeDasharray={paint.dash}
      />
      {icon && iconBox && (
        <svg
          className="shape__icon"
          x={iconBox.x}
          y={iconBox.y}
          width={iconBox.width}
          height={iconBox.height}
          color={paint.stroke}
          style={{ '--icon-cutout': paint.fill } as React.CSSProperties}
        >
          <use href={`#${icon.sprite_id}`} />
        </svg>
      )}
      {labelBox && labelBox.width > 0 && labelBox.height > 0 && (
        <foreignObject x={labelBox.x} y={labelBox.y} width={labelBox.width} height={labelBox.height}>
          <div
            className={`shape__label shape__label--${entry.outline}`}
            style={{
              color: paint.text,
              textAlign: align,
              justifyContent: align === 'left' ? 'flex-start' : align === 'right' ? 'flex-end' : 'center',
              // Шрифта из файла Archi может не быть в системе — запасное семейство то же, что у токена.
              fontFamily: paint.font ? `"${paint.font.family}", system-ui, sans-serif` : undefined,
              fontSize: paint.font ? `${paint.font.sizePt}pt` : undefined,
              fontWeight: paint.font?.bold ? 700 : undefined,
              fontStyle: paint.font?.italic ? 'italic' : undefined,
            }}
          >
            <span className="shape__name">{props.label}</span>
            {props.subtitle && <span className="shape__subtitle">{props.subtitle}</span>}
          </div>
        </foreignObject>
      )}
    </svg>
  );
});
