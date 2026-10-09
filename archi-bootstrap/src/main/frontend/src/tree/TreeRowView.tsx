import { memo } from 'react';
import { Icon, NotationIcon } from '../app/Icon';
import { t } from '../i18n';
import { shapeOf } from '../canvas/shapes';
import { layerToken } from '../canvas/style';
import type { TreeRow } from './buildTree';

export interface RowMarkers {
  views: number;
  finding: boolean;
  dirty: boolean;
}

interface Props {
  row: TreeRow;
  selected: boolean;
  lead: boolean;
  dropTarget: boolean;
  markers?: RowMarkers;
  editing: boolean;
  onEditDone(value: string | null): void;
}

function Highlighted({ text, match }: { text: string; match?: [number, number] }) {
  if (!match) return <span className="tr__n">{text}</span>;
  return (
    <span className="tr__n">
      {text.slice(0, match[0])}
      <mark>{text.slice(match[0], match[1])}</mark>
      {text.slice(match[1])}
    </span>
  );
}

/** Строка дерева. Отступ — направляющими уровней, маркеры — справа. */
export const TreeRowView = memo(function TreeRowView({ row, selected, lead, dropTarget, markers, editing, onEditDone }: Props) {
  if (row.kind === 'more') {
    return (
      <div className="tmore" style={{ '--d': row.depth } as React.CSSProperties} data-more={row.id}>
        {t('tree.showMore', { n: row.hidden })}
      </div>
    );
  }
  const folderLike = row.kind === 'folder' || row.kind === 'group';
  const classes = [
    'tr',
    folderLike ? 'tr--folder' : '',
    folderLike && row.root ? 'tr--root' : '',
    folderLike && row.open ? 'is-open' : '',
    selected ? 'is-sel' : '',
    lead ? 'is-lead' : '',
    dropTarget ? 'is-drop' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div
      className={classes}
      style={{ '--d': row.depth } as React.CSSProperties}
      data-row={row.id}
      data-kind={row.kind}
      draggable={!editing}
      role="treeitem"
      aria-selected={selected}
      aria-expanded={folderLike ? row.open : undefined}
    >
      <span className="tr__ind" />
      {folderLike ? <Icon name="chevron" className="tr__chev" /> : <span className="tr__sp" />}
      {row.kind === 'folder' && !row.root && <Icon name="folder" className="tr__ic" />}
      {row.kind === 'element' && (
        <span className="tr__icw" style={{ color: `var(--layer-${layerToken(row.layer)}-border)` }}>
          <NotationIcon spriteId={shapeOf(row.archiType).corner_icon?.sprite_id ?? 'i-stub'} className="tr__ic" />
        </span>
      )}
      {row.kind === 'relationship' && <Icon name="relation" className="tr__ic" />}
      {row.kind === 'view' && <Icon name="view" className="tr__ic" />}
      {editing ? (
        <input
          className="tr__edit"
          defaultValue={row.name}
          autoFocus
          onFocus={(event) => event.target.select()}
          onKeyDown={(event) => {
            event.stopPropagation();
            if (event.key === 'Enter') onEditDone(event.currentTarget.value);
            if (event.key === 'Escape') onEditDone(null);
          }}
          onBlur={(event) => onEditDone(event.currentTarget.value)}
          onClick={(event) => event.stopPropagation()}
          onDoubleClick={(event) => event.stopPropagation()}
        />
      ) : (
        <Highlighted text={row.name} match={row.match} />
      )}
      <span className="tr__b">
        {markers?.dirty && <i className="tr__dirty" title={t('tree.dirtyMarker')} />}
        {markers?.finding && <i className="tr__dot tr__dot--warn" title={t('tree.findingMarker')} />}
        {row.kind === 'element' && markers && markers.views === 0 && (
          <i className="tr__dot tr__dot--un" title={t('tree.unplacedMarker')} />
        )}
        {row.kind === 'element' && markers && markers.views > 1 && (
          <span className="tr__v" title={t('tree.viewsMarker', { n: markers.views })}>
            {`×${markers.views}`}
          </span>
        )}
        {folderLike && <span className="tr__cnt">{row.count}</span>}
      </span>
    </div>
  );
});
