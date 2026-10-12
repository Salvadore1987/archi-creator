import { create } from 'zustand';
import { ErrorCodes } from '../api/codes';
import { ApiError } from '../api/http';
import type { AdvisorAnswer, AdvisorFinding, ModelPlan, SessionMeta } from '../api/types';
import { describeError } from '../app/errors';
import { t } from '../i18n';
import { streamAdvisor, type AdvisorFunction } from './stream';

export interface AdvisorState {
  fn: AdvisorFunction | null;
  running: boolean;
  /** Текст ответа по мере прихода. */
  text: string;
  findings: AdvisorFinding[];
  plan: ModelPlan | null;
  answer: AdvisorAnswer | null;
  meta: SessionMeta | null;
  error: string | null;
  /** Типы из последнего плана: палитра показывает только их, пока фильтр не сброшен. */
  paletteFilter: string[] | null;
  controller: AbortController | null;

  ask(fn: AdvisorFunction, body: Record<string, unknown>): Promise<void>;
  cancel(): void;
  clearPaletteFilter(): void;
  reset(): void;
}

const IDLE = {
  fn: null,
  running: false,
  text: '',
  findings: [],
  plan: null,
  answer: null,
  meta: null,
  error: null,
  controller: null,
};

/**
 * Помощник в интерфейсе: одно обращение за раз — новое отменяет поток предыдущего,
 * как и на сервере. Отказ и обрыв не трогают редактор — это только текст в панели.
 */
export const useAdvisor = create<AdvisorState>()((set, get) => ({
  ...IDLE,
  paletteFilter: null,

  async ask(fn, body) {
    get().controller?.abort();
    const controller = new AbortController();
    set({ ...IDLE, fn, running: true, controller });
    const mine = () => get().controller === controller;
    try {
      await streamAdvisor(
        fn,
        body,
        crypto.randomUUID(),
        {
          token: (text) => mine() && set({ text: get().text + text }),
          finding: (finding) => mine() && set({ findings: [...get().findings, finding] }),
          plan: (plan) =>
            mine() && set({ plan, paletteFilter: [...new Set(plan.elements.map((e) => e.archiType))] }),
          answer: (answer) => mine() && set({ answer }),
          meta: (meta) =>
            mine() && set({ meta, error: meta.outcome === 'FAILED' ? t('advisor.errors.broken') : get().error }),
        },
        controller.signal,
      );
    } catch (error) {
      if (!mine() || (error instanceof DOMException && error.name === 'AbortError')) return;
      set({ error: describeAdvisorError(error) });
    } finally {
      if (mine()) set({ running: false, controller: null });
    }
  },

  cancel() {
    get().controller?.abort();
    set({ running: false, controller: null });
  },

  clearPaletteFilter() {
    set({ paletteFilter: null });
  },

  reset() {
    get().controller?.abort();
    set({ ...IDLE, paletteFilter: null });
  },
}));

/** Отказ помощника своими словами: бюджет с датой сброса, частота с паузой. */
export function describeAdvisorError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case ErrorCodes.ADVISOR_DISABLED:
        return t('advisor.errors.disabled');
      case ErrorCodes.BUDGET_EXHAUSTED:
        return t('advisor.errors.budget', { date: error.problem.resetsAt ?? '' });
      case ErrorCodes.RATE_LIMITED:
        return t('advisor.errors.rate', { seconds: error.problem.retryAfterSeconds ?? 60 });
      case ErrorCodes.CONTEXT_TOO_LARGE:
        return t('advisor.errors.tooLarge');
      case ErrorCodes.ADVISOR_UNAVAILABLE:
        return t('advisor.errors.unavailable');
    }
    if (error.status === 503) return t('advisor.errors.unavailable');
  }
  if (error instanceof TypeError) return t('advisor.errors.unavailable');
  return describeError(error);
}
