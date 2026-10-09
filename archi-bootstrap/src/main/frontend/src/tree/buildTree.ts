import type { Uuid } from '../api/types';
import type { ModelDoc } from '../model/doc';
import { typeName } from '../i18n';

export type TreeMode = 'folders' | 'types' | 'flat';
export type TreeFilter = 'all' | 'view' | 'unplaced' | 'findings';
export type ItemKind = 'folder' | 'element' | 'relationship' | 'view';

/** Строка дерева, готовая к отрисовке: дерево разворачивается в плоский список видимых строк. */
export type TreeRow =
  | {
      kind: 'folder' | 'group';
      key: string;
      id: Uuid;
      depth: number;
      name: string;
      count: number;
      open: boolean;
      root: boolean;
      match?: [number, number];
    }
  | {
      kind: Exclude<ItemKind, 'folder'>;
      key: string;
      id: Uuid;
      depth: number;
      name: string;
      archiType: string;
      layer?: string;
      match?: [number, number];
    }
  | { kind: 'more'; key: string; id: string; depth: number; hidden: number };

interface Item {
  kind: ItemKind;
  id: Uuid;
  name: string;
  sortOrder: number;
  archiType: string;
  layer?: string;
}

/** Индекс содержимого папок — строится раз на документ, а не на каждое нажатие клавиши. */
export interface TreeIndex {
  roots: Item[];
  children: Map<Uuid, Item[]>;
  parent: Map<Uuid, Uuid>;
}

export const BRANCH_LIMIT = 50;
export const FLAT_LIMIT = 120;

export function buildIndex(doc: ModelDoc): TreeIndex {
  const children = new Map<Uuid, Item[]>();
  const parent = new Map<Uuid, Uuid>();
  const roots: Item[] = [];
  const add = (folderId: Uuid | undefined, item: Item) => {
    if (!folderId) {
      roots.push(item);
      return;
    }
    parent.set(item.id, folderId);
    const list = children.get(folderId);
    if (list) list.push(item);
    else children.set(folderId, [item]);
  };
  for (const f of Object.values(doc.folders)) {
    add(f.parentId, { kind: 'folder', id: f.id, name: f.name, sortOrder: f.sortOrder, archiType: f.folderType ?? '' });
  }
  for (const e of Object.values(doc.elements)) {
    add(e.folderId, { kind: 'element', id: e.id, name: e.name, sortOrder: e.sortOrder, archiType: e.archiType, layer: e.layer });
  }
  for (const r of Object.values(doc.relationships)) {
    add(r.folderId, {
      kind: 'relationship',
      id: r.id,
      name: r.name ?? '',
      sortOrder: r.sortOrder,
      archiType: r.archiType,
    });
  }
  for (const v of Object.values(doc.views)) {
    add(v.folderId, { kind: 'view', id: v.id, name: v.name, sortOrder: v.sortOrder, archiType: v.archiType });
  }
  const bySortOrder = (a: Item, b: Item) => a.sortOrder - b.sortOrder;
  roots.sort(bySortOrder);
  children.forEach((list) => list.sort(bySortOrder));
  return { roots, children, parent };
}

export interface TreeContext {
  mode: TreeMode;
  query: string;
  filter: TreeFilter;
  expanded: ReadonlySet<string>;
  /** Сколько строк показано в ветви сверх лимита — по «показать ещё». */
  limits: ReadonlyMap<string, number>;
  /** Элементы открытого представления. */
  onView: ReadonlySet<Uuid>;
  /** Сколько представлений у каждого объекта. */
  placements: ReadonlyMap<Uuid, Uuid[]>;
  /** Элементы с замечаниями валидации. */
  withFindings: ReadonlySet<Uuid>;
  /** Имена связей без собственного имени — по типу и концам. */
  relationshipLabel: (id: Uuid) => string;
}

export interface TreeResult {
  rows: TreeRow[];
  /** Элементов под фильтром и запросом — для подвала «показано N». */
  shown: number;
}

function matchOf(name: string, query: string): [number, number] | undefined {
  if (!query) return undefined;
  const at = name.toLowerCase().indexOf(query);
  return at >= 0 ? [at, at + query.length] : undefined;
}

/**
 * Видимые строки дерева. При поиске и фильтре скрываются ветви без
 * совпадений, а ветви с ними раскрываются сами; счётчики папок
 * пересчитываются под то, что осталось.
 */
export function buildRows(doc: ModelDoc, index: TreeIndex, ctx: TreeContext): TreeResult {
  const query = ctx.query.trim().toLowerCase();
  const narrowed = query !== '' || ctx.filter !== 'all';

  const labelOf = (item: Item) => (item.kind === 'relationship' && !item.name ? ctx.relationshipLabel(item.id) : item.name);

  const passes = (item: Item): boolean => {
    if (item.kind === 'folder') return false;
    if (ctx.filter !== 'all') {
      if (item.kind !== 'element') return false;
      if (ctx.filter === 'view' && !ctx.onView.has(item.id)) return false;
      if (ctx.filter === 'unplaced' && (ctx.placements.get(item.id)?.length ?? 0) > 0) return false;
      if (ctx.filter === 'findings' && !ctx.withFindings.has(item.id)) return false;
    }
    if (!query) return true;
    return labelOf(item).toLowerCase().includes(query) || typeName(item.archiType).toLowerCase().includes(query);
  };

  const rows: TreeRow[] = [];
  let shown = 0;
  const itemRow = (item: Item, depth: number): TreeRow => ({
    kind: item.kind as Exclude<ItemKind, 'folder'>,
    key: item.id,
    id: item.id,
    depth,
    name: labelOf(item),
    archiType: item.archiType,
    layer: item.layer,
    match: matchOf(labelOf(item), query),
  });

  if (ctx.mode === 'flat') {
    const items = [...Object.values(doc.elements)]
      .map((e) => ({ kind: 'element' as const, id: e.id, name: e.name, sortOrder: e.sortOrder, archiType: e.archiType, layer: e.layer }))
      .filter(passes)
      .sort((a, b) => a.name.localeCompare(b.name, 'ru'));
    shown = items.length;
    const limit = FLAT_LIMIT + (ctx.limits.get('flat') ?? 0);
    items.slice(0, limit).forEach((item) => rows.push(itemRow(item, 0)));
    if (items.length > limit) rows.push({ kind: 'more', key: 'more:flat', id: 'flat', depth: 0, hidden: items.length - limit });
    return { rows, shown };
  }

  if (ctx.mode === 'types') {
    const groups = new Map<string, Item[]>();
    for (const e of Object.values(doc.elements)) {
      const item: Item = { kind: 'element', id: e.id, name: e.name, sortOrder: e.sortOrder, archiType: e.archiType, layer: e.layer };
      if (!passes(item)) continue;
      const list = groups.get(e.archiType);
      if (list) list.push(item);
      else groups.set(e.archiType, [item]);
    }
    const ordered = [...groups.entries()].sort((a, b) => b[1].length - a[1].length || a[0].localeCompare(b[0]));
    for (const [type, items] of ordered) {
      shown += items.length;
      const key = `type:${type}`;
      const open = narrowed || ctx.expanded.has(key);
      rows.push({ kind: 'group', key, id: type, depth: 0, name: typeName(type), count: items.length, open, root: true });
      if (!open) continue;
      items.sort((a, b) => a.name.localeCompare(b.name, 'ru'));
      pushLimited(items, 1, key);
    }
    return { rows, shown };
  }

  // Папки файла: счётчик ветви — число подходящих объектов в ней, рекурсивно.
  const counts = new Map<Uuid, number>();
  const countOf = (folderId: Uuid): number => {
    const cached = counts.get(folderId);
    if (cached !== undefined) return cached;
    let total = 0;
    for (const child of index.children.get(folderId) ?? []) {
      total += child.kind === 'folder' ? countOf(child.id) : passes(child) ? 1 : 0;
    }
    counts.set(folderId, total);
    return total;
  };

  function pushLimited(items: Item[], depth: number, branchKey: string) {
    const limit = BRANCH_LIMIT + (ctx.limits.get(branchKey) ?? 0);
    items.slice(0, limit).forEach((item) => rows.push(itemRow(item, depth)));
    if (items.length > limit) {
      rows.push({ kind: 'more', key: `more:${branchKey}`, id: branchKey, depth, hidden: items.length - limit });
    }
  }

  const walk = (folder: Item, depth: number) => {
    const count = countOf(folder.id);
    // Пустую папку видно, пока ничего не ищут: её создали, чтобы что-то туда положить.
    if (narrowed && count === 0) return;
    const root = depth === 0;
    const open = (narrowed && count > 0) || ctx.expanded.has(folder.id);
    rows.push({
      kind: 'folder',
      key: folder.id,
      id: folder.id,
      depth,
      name: folder.name,
      count,
      open,
      root,
      match: matchOf(folder.name, query),
    });
    if (!open) return;
    const children = index.children.get(folder.id) ?? [];
    for (const child of children) {
      if (child.kind === 'folder') walk(child, depth + 1);
    }
    const items = children.filter((child) => child.kind !== 'folder' && passes(child));
    shown += items.length;
    pushLimited(items, depth + 1, folder.id);
  };

  for (const root of index.roots) {
    if (root.kind === 'folder') walk(root, 0);
  }
  return { rows, shown };
}

/** Папки от корня до объекта — чтобы раскрыть их, когда выделение пришло извне. */
export function ancestorsOf(index: TreeIndex, id: Uuid): Uuid[] {
  const chain: Uuid[] = [];
  let current = index.parent.get(id);
  while (current) {
    chain.unshift(current);
    current = index.parent.get(current);
  }
  return chain;
}

/** Полный путь папки: «Application / Интеграционный слой». */
export function folderPath(doc: ModelDoc, folderId: Uuid): string {
  const names: string[] = [];
  let current: Uuid | undefined = folderId;
  while (current) {
    const folder: ModelDoc['folders'][string] | undefined = doc.folders[current];
    if (!folder) break;
    names.unshift(folder.name);
    current = folder.parentId;
  }
  return names.join(' / ');
}
