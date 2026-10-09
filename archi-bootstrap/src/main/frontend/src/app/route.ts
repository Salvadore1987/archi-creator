import { useSyncExternalStore } from 'react';

/**
 * Адрес открытой модели — параметр `?model=`, а не путь: сервер отдаёт
 * SPA с корня, и глубокая ссылка не требует от него перенаправлений.
 */
function read(): string | null {
  return new URLSearchParams(window.location.search).get('model');
}

function subscribe(listener: () => void): () => void {
  window.addEventListener('popstate', listener);
  return () => window.removeEventListener('popstate', listener);
}

export function useModelRoute(): string | null {
  return useSyncExternalStore(subscribe, read);
}

export function navigateToModel(modelId: string | null): void {
  const url = modelId ? `?model=${encodeURIComponent(modelId)}` : window.location.pathname;
  window.history.pushState(null, '', url);
  window.dispatchEvent(new PopStateEvent('popstate'));
}
