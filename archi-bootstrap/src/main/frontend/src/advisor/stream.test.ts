import { describe, expect, it, vi } from 'vitest';
import { dispatch, type AdvisorHandlers } from './stream';

describe('кадры потока помощника', () => {
  it('тип кадра — по event, тело — JSON из data', () => {
    const handlers: AdvisorHandlers = { token: vi.fn(), finding: vi.fn(), plan: vi.fn(), answer: vi.fn(), meta: vi.fn() };
    dispatch('event: token\ndata: {"text":"При"}', handlers);
    dispatch('event: meta\ndata: {"outcome":"COMPLETED","contextTruncated":true}', handlers);
    dispatch(': комментарий без данных', handlers);

    expect(handlers.token).toHaveBeenCalledWith('При');
    expect(handlers.meta).toHaveBeenCalledWith({ outcome: 'COMPLETED', contextTruncated: true });
    expect(handlers.finding).not.toHaveBeenCalled();
  });
});
