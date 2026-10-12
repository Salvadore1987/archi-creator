import { requestStream } from '../api/http';
import type { AdvisorAnswer, AdvisorFinding, ModelPlan, SessionMeta } from '../api/types';

/** Кадры потока помощника — типы событий SSE сервера. */
export interface AdvisorHandlers {
  token(text: string): void;
  finding(finding: AdvisorFinding): void;
  plan(plan: ModelPlan): void;
  answer(answer: AdvisorAnswer): void;
  meta(meta: SessionMeta): void;
}

export type AdvisorFunction = 'review' | 'explain' | 'plan' | 'document';

/**
 * Обращение к помощнику и чтение потока до конца. `EventSource` не умеет `POST`
 * и заголовки, поэтому поток читается `fetch`'ем: кадры разделены пустой строкой,
 * в кадре — `event:` и `data:` с JSON. Отказ до потока — `ApiError`.
 */
export async function streamAdvisor(
  fn: AdvisorFunction,
  body: Record<string, unknown>,
  idempotencyKey: string,
  handlers: AdvisorHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const response = await requestStream(`/ai/${fn}`, { body, idempotencyKey, signal });
  const reader = response.body!.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += value;
    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      dispatch(buffer.slice(0, boundary), handlers);
      buffer = buffer.slice(boundary + 2);
      boundary = buffer.indexOf('\n\n');
    }
  }
  if (buffer.trim()) dispatch(buffer, handlers);
}

export function dispatch(frame: string, handlers: AdvisorHandlers): void {
  let event = 'message';
  const data: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
  }
  if (data.length === 0) return;
  const payload = JSON.parse(data.join('\n'));
  switch (event) {
    case 'token':
      handlers.token((payload as { text: string }).text);
      break;
    case 'finding':
      handlers.finding(payload as AdvisorFinding);
      break;
    case 'plan':
      handlers.plan(payload as ModelPlan);
      break;
    case 'answer':
      handlers.answer(payload as AdvisorAnswer);
      break;
    case 'meta':
      handlers.meta(payload as SessionMeta);
      break;
  }
}
