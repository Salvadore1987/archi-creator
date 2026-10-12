import type { AccessType } from '../api/types';

export type MarkerId = 'diamond-filled' | 'diamond-open' | 'dot' | 'arrow-filled' | 'arrow-open' | 'triangle-open';

/** Оформление связи — семантика нотации, а не стиль: пунктир и наконечники по типу. */
export interface EdgeNotation {
  dash?: string;
  start?: MarkerId;
  end?: MarkerId;
}

export function notationOf(archiType: string, accessType?: AccessType, directed?: boolean): EdgeNotation {
  switch (archiType) {
    case 'archimate:CompositionRelationship':
      return { start: 'diamond-filled' };
    case 'archimate:AggregationRelationship':
      return { start: 'diamond-open' };
    case 'archimate:AssignmentRelationship':
      return { start: 'dot', end: 'arrow-filled' };
    case 'archimate:RealizationRelationship':
      return { dash: '6 4', end: 'triangle-open' };
    case 'archimate:ServingRelationship':
      return { end: 'arrow-open' };
    case 'archimate:AccessRelationship':
      // Без атрибута Archi считает доступ записью.
      switch (accessType ?? 'WRITE') {
        case 'READ':
          return { dash: '2 3', start: 'arrow-open' };
        case 'READ_WRITE':
          return { dash: '2 3', start: 'arrow-open', end: 'arrow-open' };
        case 'ACCESS':
          return { dash: '2 3' };
        default:
          return { dash: '2 3', end: 'arrow-open' };
      }
    case 'archimate:InfluenceRelationship':
      return { dash: '6 4', end: 'arrow-open' };
    case 'archimate:TriggeringRelationship':
      return { end: 'arrow-filled' };
    case 'archimate:FlowRelationship':
      return { dash: '8 4', end: 'arrow-filled' };
    case 'archimate:SpecializationRelationship':
      return { end: 'triangle-open' };
    case 'archimate:AssociationRelationship':
      return directed ? { end: 'arrow-open' } : {};
    default:
      return {};
  }
}
