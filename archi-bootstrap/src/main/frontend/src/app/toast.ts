import { create } from 'zustand';

export interface Toast {
  id: number;
  text: string;
  tone: 'info' | 'error';
  action?: { label: string; run: () => void };
}

interface ToastState {
  toast: Toast | null;
  show(text: string, options?: { tone?: Toast['tone']; action?: Toast['action'] }): void;
  dismiss(id: number): void;
}

let seq = 0;

/** Одно сообщение о действии за раз: новое вытесняет старое. */
export const useToast = create<ToastState>()((set, get) => ({
  toast: null,
  show(text, options) {
    const id = ++seq;
    set({ toast: { id, text, tone: options?.tone ?? 'info', action: options?.action } });
    window.setTimeout(() => get().dismiss(id), options?.tone === 'error' ? 8000 : 4500);
  },
  dismiss(id) {
    if (get().toast?.id === id) {
      set({ toast: null });
    }
  },
}));
