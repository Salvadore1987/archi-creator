import type { Node, NodeProps } from '@xyflow/react';
import { memo } from 'react';
import { typeName } from '../i18n';

export const MERGE_GHOST = 'mergeGhost';
const GHOST_PREFIX = 'merge:';

/** Идентификатор контура на холсте: не пересекается с идентификаторами узлов документа. */
export function ghostId(archiId: string): string {
  return GHOST_PREFIX + archiId;
}

export function isGhost(id: string): boolean {
  return id.startsWith(GHOST_PREFIX);
}

export type MergeGhostData = { name: string; type?: string };
export type MergeGhostFlowNode = Node<MergeGhostData, typeof MERGE_GHOST>;

/** Узел, который слияние добавит на представление: контур без поведения. */
export const MergeGhostNode = memo(function MergeGhostNode({ id, data }: NodeProps<MergeGhostFlowNode>) {
  return (
    <div className="merge-ghost" data-merge-ghost={id}>
      <span>{data.name}</span>
      {data.type && <span className="merge-ghost__type">{typeName(data.type)}</span>}
    </div>
  );
});
