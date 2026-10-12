import { create } from 'zustand';
import type { Clip } from '../model/ops';

/** Состояние корпуса, не относящееся к модели: открытые панели, сетка. */
interface UiState {
  historyOpen: boolean;
  /** Вкладка правой панели, которую попросили открыть извне: «Где используется» ведёт на «Описание». */
  inspectorRequest: { tab: 'properties' | 'description'; seq: number } | null;
  requestInspector(tab: 'properties' | 'description'): void;
  snapToGrid: boolean;
  /** Навигатор — мини-карта представления, как одноимённое окно Archi. */
  navigatorOpen: boolean;
  toggleNavigator(): void;
  /** Скопированное с холста; вставок из него — чтобы каждая следующая ложилась со сдвигом. */
  clipboard: { clip: Clip; pastes: number } | null;
  setClipboard(clip: Clip | null): void;
  notePaste(): number;
  toggleHistory(open?: boolean): void;
  toggleSnap(): void;
}

export const useUi = create<UiState>()((set, get) => ({
  historyOpen: false,
  inspectorRequest: null,
  requestInspector(tab) {
    set({ inspectorRequest: { tab, seq: (get().inspectorRequest?.seq ?? 0) + 1 } });
  },
  snapToGrid: true,
  navigatorOpen: false,
  toggleNavigator() {
    set({ navigatorOpen: !get().navigatorOpen });
  },
  clipboard: null,
  setClipboard(clip) {
    set({ clipboard: clip ? { clip, pastes: 0 } : null });
  },
  notePaste() {
    const current = get().clipboard;
    if (!current) return 0;
    const pastes = current.pastes + 1;
    set({ clipboard: { ...current, pastes } });
    return pastes;
  },
  toggleHistory(open) {
    set({ historyOpen: open ?? !get().historyOpen });
  },
  toggleSnap() {
    set({ snapToGrid: !get().snapToGrid });
  },
}));
