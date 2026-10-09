import { create } from 'zustand';

/** Состояние корпуса, не относящееся к модели: открытые панели, сетка. */
interface UiState {
  historyOpen: boolean;
  snapToGrid: boolean;
  toggleHistory(open?: boolean): void;
  toggleSnap(): void;
}

export const useUi = create<UiState>()((set, get) => ({
  historyOpen: false,
  snapToGrid: true,
  toggleHistory(open) {
    set({ historyOpen: open ?? !get().historyOpen });
  },
  toggleSnap() {
    set({ snapToGrid: !get().snapToGrid });
  },
}));
