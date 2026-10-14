import { create } from 'zustand';
import { api } from '../api/endpoints';
import type { MergeAddedNode, MergeMarkKind, MergePreview, MergeSide, Uuid } from '../api/types';
import { describeError } from '../app/errors';

/**
 * Режим слияния ветки предложения. Отчёт строит сервер; выбор по конфликту
 * переспрашивает отчёт, чтобы холст показывал итог именно этого выбора.
 * Отметки холста — по `archiId`: так их находят и узлы, и элементы, и связи.
 */
export interface MergeState {
  open: boolean;
  ref: string;
  preview: MergePreview | null;
  resolutions: Record<string, MergeSide>;
  marks: Record<string, MergeMarkKind>;
  /** Добавляемые узлы по `archiId` представления. */
  ghosts: Record<string, MergeAddedNode[]>;
  loading: boolean;
  error: string | null;

  show(): void;
  hide(): void;
  setRef(ref: string): void;
  build(modelId: Uuid): Promise<void>;
  choose(modelId: Uuid, conflictId: string, side: MergeSide): Promise<void>;
}

const EMPTY = { preview: null, resolutions: {}, marks: {}, ghosts: {}, error: null };

export const useMerge = create<MergeState>()((set, get) => {
  const request = async (modelId: Uuid, resolutions: Record<string, MergeSide>) => {
    set({ loading: true, error: null });
    try {
      const preview = await api.mergePreview(modelId, get().ref.trim(), resolutions);
      set({
        preview,
        resolutions,
        marks: Object.fromEntries(preview.marks.map((m) => [m.archiId, m.mark])),
        ghosts: Object.fromEntries(preview.views.map((v) => [v.archiId, v.addedNodes])),
      });
    } catch (error) {
      set({ ...EMPTY, error: describeError(error) });
    } finally {
      set({ loading: false });
    }
  };

  return {
    open: false,
    ref: '',
    loading: false,
    ...EMPTY,

    show() {
      set({ open: true });
    },
    hide() {
      set({ open: false, loading: false, ...EMPTY });
    },
    setRef(ref) {
      set({ ref });
    },
    build(modelId) {
      return request(modelId, {});
    },
    choose(modelId, conflictId, side) {
      return request(modelId, { ...get().resolutions, [conflictId]: side });
    },
  };
});

/** Отметка объекта холста: своя у узла, иначе у элемента или связи, которую он показывает. */
export function useMergeMark(...archiIds: Array<string | undefined>): MergeMarkKind | undefined {
  return useMerge((s) => {
    for (const id of archiIds) {
      const mark = id ? s.marks[id] : undefined;
      if (mark) return mark;
    }
    return undefined;
  });
}
