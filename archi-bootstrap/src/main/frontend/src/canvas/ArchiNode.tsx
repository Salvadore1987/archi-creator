import { Handle, NodeResizer, Position, type NodeProps } from '@xyflow/react';
import { memo } from 'react';
import { t, typeName } from '../i18n';
import { useEditor } from '../model/store';
import { isOpaque, type ArchiFlowNode } from './flowModel';
import { NodeShape } from './NodeShape';

export type NodeEditing = { resizable: boolean; onResizeEnd(id: string, bounds: { x: number; y: number; width: number; height: number }): void };

let editing: NodeEditing = { resizable: false, onResizeEnd: () => {} };

/** Канва сообщает узлам, можно ли менять размер, — без пересоздания всех узлов на смену прав. */
export function setNodeEditing(next: NodeEditing): void {
  editing = next;
}

/**
 * Узел представления. Имя берётся из элемента модели, а не хранится в узле:
 * переименование видно на всех представлениях сразу.
 */
export const ArchiNode = memo(function ArchiNode({ id, data, selected, width, height }: NodeProps<ArchiFlowNode>) {
  const node = useEditor((s) => s.doc?.loadedViews[data.viewId]?.nodes[id]);
  const element = useEditor((s) => (node?.elementId ? s.doc?.elements[node.elementId] : undefined));
  const opaque = useEditor((s) => (node && s.doc ? isOpaque(s.doc, node) : false));
  if (!node) return null;

  const archiType = element?.archiType ?? node.archiType;
  const label = element?.name ?? node.label ?? node.content ?? '';
  const subtitle = opaque ? `${typeName(archiType)} · ${t('canvas.opaque')}` : undefined;
  const w = width ?? node.width;
  const h = height ?? node.height;

  return (
    <div className={`archi-node${opaque ? ' archi-node--opaque' : ''}`} data-node={id} data-element={node.elementId}>
      {selected && editing.resizable && !opaque && (
        <NodeResizer
          minWidth={20}
          minHeight={15}
          color="var(--accent)"
          handleClassName="archi-node__handle"
          lineClassName="archi-node__line"
          onResizeEnd={(_, params) =>
            editing.onResizeEnd(id, {
              x: Math.round(params.x),
              y: Math.round(params.y),
              width: Math.round(params.width),
              height: Math.round(params.height),
            })
          }
        />
      )}
      <NodeShape
        archiType={archiType}
        layer={element?.layer}
        width={w}
        height={h}
        label={node.kind === 'NOTE' ? (node.content ?? '') : label}
        style={node.style}
        selected={selected}
        opaque={opaque}
        subtitle={subtitle}
      />
      <Handle type="target" position={Position.Top} className="archi-node__anchor" isConnectable={false} />
      <Handle type="source" position={Position.Bottom} className="archi-node__anchor" isConnectable={false} />
    </div>
  );
});
