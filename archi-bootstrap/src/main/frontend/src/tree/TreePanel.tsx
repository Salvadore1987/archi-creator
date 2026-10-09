import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { Uuid } from '../api/types';
import { Icon } from '../app/Icon';
import { FOCUS_TREE_SEARCH } from '../app/useShortcuts';
import { useOpenView } from '../canvas/useOpenView';
import { t, typeName } from '../i18n';
import { placementIndex } from '../model/doc';
import { dirtyObjects } from '../model/history';
import { useEditor } from '../model/store';
import { ancestorsOf, buildIndex, buildRows, type TreeFilter, type TreeMode } from './buildTree';
import { nextSelection } from './selection';
import { TreeRowView } from './TreeRowView';
import { useTreeEditing } from './useTreeEditing';
import './tree.css';

export const DRAG_ELEMENT = 'application/x-archi-element';

const MODES: Array<[TreeMode, 'tree.modeFolders' | 'tree.modeTypes' | 'tree.modeFlat']> = [
  ['folders', 'tree.modeFolders'],
  ['types', 'tree.modeTypes'],
  ['flat', 'tree.modeFlat'],
];

export function TreePanel() {
  const doc = useEditor((s) => s.doc!);
  const history = useEditor((s) => s.history);
  const selection = useEditor((s) => s.selection);
  const findings = useEditor((s) => s.findings);
  const activeViewId = useEditor((s) => s.activeViewId);
  const select = useEditor((s) => s.select);
  const openView = useOpenView();
  const editing = useTreeEditing();

  const [query, setQuery] = useState('');
  const [mode, setMode] = useState<TreeMode>('folders');
  const [filter, setFilter] = useState<TreeFilter>('all');
  const [expanded, setExpanded] = useState<Set<string>>(
    () => new Set(Object.values(doc.folders).filter((f) => !f.parentId).map((f) => f.id)),
  );
  const [limits, setLimits] = useState<Map<string, number>>(new Map());
  const [anchor, setAnchor] = useState<Uuid | null>(null);
  const search = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLDivElement>(null);

  // Индекс зависит только от структуры, а не от позиции в истории или выделения.
  const index = useMemo(() => buildIndex(doc), [doc.folders, doc.elements, doc.relationships, doc.views]); // eslint-disable-line react-hooks/exhaustive-deps
  const placements = useMemo(() => placementIndex(doc), [doc]);
  const dirty = useMemo(() => dirtyObjects(history), [history]);
  const withFindings = useMemo(() => {
    const byArchiId = new Map(Object.values(doc.elements).map((e) => [e.archiId, e.id]));
    return new Set(findings.flatMap((f) => (f.targetId && byArchiId.has(f.targetId) ? [byArchiId.get(f.targetId)!] : [])));
  }, [findings, doc.elements]);
  const onView = useMemo(() => {
    const view = activeViewId ? doc.loadedViews[activeViewId] : undefined;
    return new Set(view ? Object.values(view.nodes).flatMap((n) => (n.elementId ? [n.elementId] : [])) : []);
  }, [doc.loadedViews, activeViewId]);

  const relationshipLabel = useCallback(
    (id: Uuid) => {
      const r = doc.relationships[id];
      if (!r) return '';
      const end = (endId: Uuid) => doc.elements[endId]?.name ?? doc.relationships[endId]?.name ?? '…';
      return `${typeName(r.archiType)}: ${end(r.sourceId)} → ${end(r.targetId)}`;
    },
    [doc.relationships, doc.elements],
  );

  const { rows, shown } = useMemo(
    () =>
      buildRows(doc, index, { mode, query, filter, expanded, limits, onView, placements, withFindings, relationshipLabel }),
    [doc, index, mode, query, filter, expanded, limits, onView, placements, withFindings, relationshipLabel],
  );

  const counts = useMemo(() => {
    const elements = Object.keys(doc.elements);
    return {
      all: elements.length,
      view: onView.size,
      unplaced: elements.filter((id) => (placements.get(id)?.length ?? 0) === 0).length,
      findings: withFindings.size,
    };
  }, [doc.elements, onView, placements, withFindings]);

  useEffect(() => {
    const focus = () => {
      search.current?.focus();
      search.current?.select();
    };
    window.addEventListener(FOCUS_TREE_SEARCH, focus);
    return () => window.removeEventListener(FOCUS_TREE_SEARCH, focus);
  }, []);

  // Прокрутка к строке — только когда выделение пришло извне. Клик в самом
  // дереве строку не двигает: иначе она уезжает из-под курсора и двойной
  // клик по имени не срабатывает.
  const lastScrolled = useRef(0);
  useEffect(() => {
    if (selection.origin === 'tree' || selection.seq === lastScrolled.current || selection.ids.length === 0) return;
    lastScrolled.current = selection.seq;
    const target = selection.ids[0]!;
    const chain = ancestorsOf(index, target);
    if (mode === 'folders' && chain.some((id) => !expanded.has(id))) {
      setExpanded((prev) => new Set([...prev, ...chain]));
    }
    requestAnimationFrame(() => {
      list.current?.querySelector(`[data-row="${CSS.escape(target)}"]`)?.scrollIntoView({ block: 'nearest' });
    });
  }, [selection, index, mode, expanded]);

  const visibleIds = useMemo(() => rows.filter((r) => r.kind !== 'more').map((r) => r.id), [rows]);
  const selected = useMemo(() => new Set(selection.ids), [selection.ids]);

  const toggle = (id: string) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const rowOf = (target: EventTarget) => (target as HTMLElement).closest('[data-row],[data-more]') as HTMLElement | null;

  const onClick = (event: React.MouseEvent) => {
    const el = rowOf(event.target);
    if (!el) return;
    if (el.dataset.more) {
      const id = el.dataset.more;
      setLimits((prev) => new Map(prev).set(id, (prev.get(id) ?? 0) + 200));
      return;
    }
    const id = el.dataset.row!;
    const kind = el.dataset.kind;
    if ((kind === 'folder' || kind === 'group') && (event.target as HTMLElement).closest('.tr__chev')) {
      toggle(kind === 'group' ? `type:${id}` : id);
      return;
    }
    if (kind === 'group') {
      toggle(`type:${id}`);
      return;
    }
    const next = nextSelection(selection.ids, anchor, id, visibleIds, {
      toggle: event.metaKey || event.ctrlKey,
      range: event.shiftKey,
    });
    if (!event.shiftKey) setAnchor(id);
    select(next, 'tree');
  };

  const onDoubleClick = (event: React.MouseEvent) => {
    const el = rowOf(event.target);
    if (!el?.dataset.row) return;
    const id = el.dataset.row;
    const kind = el.dataset.kind;
    if (kind === 'view') {
      void openView(id);
    } else if (kind === 'folder') {
      if ((event.target as HTMLElement).closest('.tr__n') && editing.canRename(id)) editing.startRename(id);
      else toggle(id);
    } else if ((event.target as HTMLElement).closest('.tr__n') && editing.canRename(id)) {
      editing.startRename(id);
    }
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    const lead = selection.ids[selection.ids.length - 1];
    if (event.key === 'Enter' && lead && doc.views[lead]) {
      void openView(lead);
    } else if (event.key === 'F2' && lead && editing.canRename(lead)) {
      event.preventDefault();
      editing.startRename(lead);
    } else if ((event.key === 'Delete' || event.key === 'Backspace') && selection.ids.length > 0) {
      event.preventDefault();
      editing.requestDelete(selection.ids);
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const at = lead ? visibleIds.indexOf(lead) : -1;
      const next = visibleIds[Math.max(0, Math.min(visibleIds.length - 1, at + (event.key === 'ArrowDown' ? 1 : -1)))];
      if (next) {
        setAnchor(next);
        select([next], 'tree');
        list.current?.querySelector(`[data-row="${CSS.escape(next)}"]`)?.scrollIntoView({ block: 'nearest' });
      }
    }
  };

  const onDragStart = (event: React.DragEvent) => {
    const el = rowOf(event.target);
    const id = el?.dataset.row;
    if (!id) return;
    const ids = selected.has(id) ? selection.ids : [id];
    if (!selected.has(id)) select([id], 'tree');
    event.dataTransfer.effectAllowed = 'copyMove';
    if (doc.elements[id]) event.dataTransfer.setData(DRAG_ELEMENT, id);
    editing.dragStart(ids);
  };

  const narrowed = query.trim() !== '' || filter !== 'all';

  return (
    <section className="tree-panel" data-testid="model-tree">
      <div className="panel-head">
        <Icon name="tree" />
        {t('tree.title')}
        <span className="spacer" />
        {editing.enabled && (
          <button type="button" className="btn btn--icon btn--small" title={t('tree.newFolder')} onClick={() => editing.createFolder()}>
            <Icon name="plus" />
          </button>
        )}
        <button
          type="button"
          className="btn btn--icon btn--small"
          title={t('tree.collapseAll')}
          onClick={() => setExpanded(new Set())}
        >
          <Icon name="minus" />
        </button>
      </div>

      <div className="tree-tools">
        <label className={`tsearch${query ? ' has-q' : ''}`}>
          <Icon name="search" />
          <input
            ref={search}
            value={query}
            placeholder={t('tree.searchPlaceholder')}
            autoComplete="off"
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Escape') setQuery('');
            }}
          />
          {query && (
            <button type="button" className="clr" title={t('tree.clear')} onClick={() => setQuery('')}>
              <Icon name="close" />
            </button>
          )}
        </label>
      </div>

      <div className="tmodes">
        {MODES.map(([value, label]) => (
          <button
            key={value}
            type="button"
            className={`btn btn--small${mode === value ? ' is-on' : ''}`}
            onClick={() => setMode(value)}
          >
            {t(label)}
          </button>
        ))}
      </div>

      <div className="tchips">
        <FilterChip value="all" current={filter} onSelect={setFilter} count={counts.all} label={t('tree.filterAll')} />
        <FilterChip value="view" current={filter} onSelect={setFilter} count={counts.view} label={t('tree.filterView')} />
        <FilterChip value="unplaced" current={filter} onSelect={setFilter} count={counts.unplaced} label={t('tree.filterUnplaced')} dot="un" />
        <FilterChip value="findings" current={filter} onSelect={setFilter} count={counts.findings} label={t('tree.filterFindings')} dot="warn" />
      </div>

      <div
        ref={list}
        className="tree scroll"
        role="tree"
        tabIndex={0}
        onClick={onClick}
        onDoubleClick={onDoubleClick}
        onKeyDown={onKeyDown}
        onDragStart={onDragStart}
        onDragEnd={editing.dragEnd}
        onDragOver={editing.dragOver}
        onDragLeave={editing.dragLeave}
        onDrop={editing.drop}
        onContextMenu={editing.contextMenu}
      >
        {rows.length === 0 && (
          <div className="tempty">
            <b>{t('tree.nothingFound')}</b>
            {t('tree.nothingFoundHint')}
          </div>
        )}
        {rows.map((row) => (
          <TreeRowView
            key={row.key}
            row={row}
            selected={selected.has(row.id)}
            lead={selection.ids.length > 1 && selection.ids[selection.ids.length - 1] === row.id}
            dropTarget={editing.dropTarget === row.id}
            editing={editing.renaming === row.id}
            onEditDone={(value) => editing.finishRename(row.id, value)}
            markers={
              row.kind === 'element' || row.kind === 'folder' || row.kind === 'relationship' || row.kind === 'view'
                ? {
                    views: placements.get(row.id)?.length ?? 0,
                    finding: withFindings.has(row.id),
                    dirty: dirty.has(row.id),
                  }
                : undefined
            }
          />
        ))}
      </div>

      <div className="tfoot">
        <span className="tfoot__txt">
          {t('tree.footer', {
            elements: Object.keys(doc.elements).length,
            relationships: Object.keys(doc.relationships).length,
            views: Object.keys(doc.views).length,
          })}
        </span>
        <span className="spacer" />
        {narrowed && <span className="tfoot__shown">{t('tree.shown', { n: shown })}</span>}
      </div>
      {editing.overlay}
    </section>
  );
}

function FilterChip(props: {
  value: TreeFilter;
  current: TreeFilter;
  label: string;
  count: number;
  dot?: 'un' | 'warn';
  onSelect(value: TreeFilter): void;
}) {
  return (
    <button
      type="button"
      className={`tchip${props.current === props.value ? ' is-on' : ''}`}
      aria-pressed={props.current === props.value}
      onClick={() => props.onSelect(props.value)}
    >
      {props.dot && <i className={`tr__dot tr__dot--${props.dot}`} />}
      {props.label}
      <b>{props.count}</b>
    </button>
  );
}
