import { create } from 'zustand';

/**
 * Состояние документа модели (§3.2: Zustand — документ, TanStack Query —
 * серверные данные). На этапе 0 документа ещё нет, и в хранилище лежит
 * единственное, что уже существует, — какая модель выбрана.
 *
 * Хранилище заведено пустым намеренно: разделение «документ против кэша
 * сервера» дешевле установить сразу, чем расселять состояние потом.
 */
interface ModelState {
  /** Внутренний идентификатор модели (UUIDv7, ADR-0002) или null. */
  modelId: string | null;
  select: (modelId: string | null) => void;
}

export const useModelStore = create<ModelState>((set) => ({
  modelId: null,
  select: (modelId) => set({ modelId }),
}));
