import { create } from 'zustand';
import type { Finding, Role, Uuid, VersionInfo, ViewPayload } from '../api/types';
import { applyChanges, fromTree, viewDocOf, type Change, type ModelDoc } from './doc';
import {
  EMPTY_HISTORY,
  canRedo,
  canUndo,
  moveTo,
  partiallySynced,
  push,
  syncPlan,
  synced,
  type History,
  type Operation,
} from './history';
import { runPlan } from './commands';
import type { ModelTree } from '../api/types';

export interface Viewport {
  x: number;
  y: number;
  zoom: number;
}

/** Откуда пришло выделение: прокрутка дерева к строке нужна, только если не из самого дерева. */
export type SelectionOrigin = 'tree' | 'canvas' | 'panel' | 'external';

export interface Selection {
  ids: Uuid[];
  origin: SelectionOrigin;
  /** Счётчик смены выделения: дерево прокручивает строку один раз на каждое внешнее выделение. */
  seq: number;
}

export type LockState =
  | { kind: 'none' }
  | { kind: 'mine'; expiresAt: string }
  | { kind: 'theirs'; owner: string; expiresAt?: string }
  | { kind: 'readonly' };

export type SaveState = { kind: 'idle' } | { kind: 'saving' } | { kind: 'error'; error: unknown };

export interface EditorState {
  doc: ModelDoc | null;
  history: History;
  selection: Selection;
  openViews: Uuid[];
  activeViewId: Uuid | null;
  viewports: Record<Uuid, Viewport>;
  findings: Finding[];
  roles: Role[];
  lock: LockState;
  save: SaveState;
  lastVersion: VersionInfo | null;
  versionPending: boolean;

  load(tree: ModelTree): void;
  close(): void;
  addView(payload: ViewPayload): void;
  openView(viewId: Uuid): void;
  closeView(viewId: Uuid): void;
  setViewport(viewId: Uuid, viewport: Viewport): void;
  select(ids: Uuid[], origin: SelectionOrigin): void;
  setFindings(findings: Finding[]): void;
  setRoles(roles: Role[]): void;
  setLock(lock: LockState): void;
  setLastVersion(version: VersionInfo | null): void;

  /** Правка: изменения применяются к документу и кладутся в историю одной операцией. */
  perform(label: string, changes: Change[], affected: Uuid[]): void;
  undo(): void;
  redo(): void;
  goTo(position: number): void;
  /** Синхронизация с сервером; версию фиксирует вызывающий — ему нужен ключ идемпотентности. */
  sync(): Promise<boolean>;
  setSaveState(state: SaveState): void;
  markVersionSaved(version: VersionInfo): void;
}

let operationSeq = 0;

export const useEditor = create<EditorState>()((set, get) => ({
  doc: null,
  history: EMPTY_HISTORY,
  selection: { ids: [], origin: 'external', seq: 0 },
  openViews: [],
  activeViewId: null,
  viewports: {},
  findings: [],
  roles: [],
  lock: { kind: 'none' },
  save: { kind: 'idle' },
  lastVersion: null,
  versionPending: false,

  load(tree) {
    set({
      doc: fromTree(tree),
      history: EMPTY_HISTORY,
      selection: { ids: [], origin: 'external', seq: 0 },
      openViews: [],
      activeViewId: null,
      viewports: {},
      save: { kind: 'idle' },
      versionPending: false,
    });
  },

  close() {
    set({ doc: null, history: EMPTY_HISTORY, openViews: [], activeViewId: null, findings: [], lock: { kind: 'none' } });
  },

  addView(payload) {
    const doc = get().doc;
    if (!doc || doc.loadedViews[payload.id]) {
      return;
    }
    set({ doc: { ...doc, loadedViews: { ...doc.loadedViews, [payload.id]: viewDocOf(payload) } } });
  },

  openView(viewId) {
    const { openViews } = get();
    set({ openViews: openViews.includes(viewId) ? openViews : [...openViews, viewId], activeViewId: viewId });
  },

  closeView(viewId) {
    const { openViews, activeViewId } = get();
    const index = openViews.indexOf(viewId);
    const rest = openViews.filter((id) => id !== viewId);
    set({
      openViews: rest,
      activeViewId: activeViewId === viewId ? (rest[Math.min(index, rest.length - 1)] ?? null) : activeViewId,
    });
  },

  setViewport(viewId, viewport) {
    set({ viewports: { ...get().viewports, [viewId]: viewport } });
  },

  select(ids, origin) {
    set({ selection: { ids, origin, seq: get().selection.seq + 1 } });
  },

  setFindings(findings) {
    set({ findings });
  },
  setRoles(roles) {
    set({ roles });
  },
  setLock(lock) {
    set({ lock });
  },
  setLastVersion(version) {
    set({ lastVersion: version });
  },

  perform(label, changes, affected) {
    const { doc, history, save } = get();
    // Во время отправки документ не меняется: план составлен от его текущего состояния.
    if (!doc || changes.length === 0 || save.kind === 'saving') {
      return;
    }
    const op: Operation = { id: `op-${++operationSeq}`, label, changes, affected, at: Date.now() };
    set({ doc: applyChanges(doc, changes), history: push(history, op) });
  },

  undo() {
    const { history } = get();
    if (canUndo(history)) get().goTo(history.pointer - 1);
  },

  redo() {
    const { history } = get();
    if (canRedo(history)) get().goTo(history.pointer + 1);
  },

  goTo(position) {
    const { doc, history, save } = get();
    if (!doc || save.kind === 'saving') {
      return;
    }
    const move = moveTo(history, position);
    const next = move.apply.reduce((current, op) => applyChanges(current, op.changes), doc);
    set({ doc: next, history: move.history });
  },

  async sync() {
    const { doc, history } = get();
    if (!doc) {
      return false;
    }
    const plan = syncPlan(history);
    set({ save: { kind: 'saving' } });
    const outcome = await runPlan(doc.model.id, plan);
    if (outcome.remaining.length > 0) {
      set({ history: partiallySynced(history, outcome.remaining), save: { kind: 'error', error: outcome.error } });
      return false;
    }
    set({ history: synced(history), versionPending: true });
    return true;
  },

  setSaveState(state) {
    set({ save: state });
  },

  markVersionSaved(version) {
    set({ lastVersion: version, versionPending: false, save: { kind: 'idle' } });
  },
}));
