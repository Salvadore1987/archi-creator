import { create } from 'zustand';

/** Состояние корпуса, не относящееся к модели: открытые панели, сетка. */
interface UiState {
  historyOpen: boolean;
  /** Вкладка правой панели, которую попросили открыть извне: «Где используется» ведёт на «Описание». */
  inspectorRequest: { tab: 'properties' | 'description'; seq: number } | null;
  requestInspector(tab: 'properties' | 'description'): void;
  snapToGrid: boolean;
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
  toggleHistory(open) {
    set({ historyOpen: open ?? !get().historyOpen });
  },
  toggleSnap() {
    set({ snapToGrid: !get().snapToGrid });
  },
}));
