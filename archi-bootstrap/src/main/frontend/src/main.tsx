import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './index.css';

const root = document.getElementById('root');
if (!root) {
  throw new Error('В index.html нет элемента #root');
}

/**
 * Серверные данные держит TanStack Query, документ модели — Zustand (§3.2).
 * Граница проведена здесь, на входе, чтобы её не пришлось проводить потом
 * по живому состоянию.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Модель читается редко и меняется по действию пользователя,
      // а не сама по себе: перезапрос при возврате фокуса даёт шум,
      // а не свежесть.
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
  },
});

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
);
