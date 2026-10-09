import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import 'virtual:design-tokens.css';
import '@xyflow/react/dist/base.css';
import './app/app.css';
import './canvas/canvas.css';
import { App } from './app/App';
import { initAuth } from './auth/session';
import { t } from './i18n';
import { useEditor } from './model/store';

const root = document.getElementById('root');
if (!root) {
  throw new Error('В index.html нет элемента #root');
}

/**
 * Серверные данные держит TanStack Query, документ модели — Zustand.
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
      retry: 1,
    },
  },
});

// В разработке документ сессии виден из консоли и из E2E — состояние
// проверяется напрямую, а не по пикселям.
if (import.meta.env.DEV) {
  (window as unknown as { __archiEditor: typeof useEditor }).__archiEditor = useEditor;
}

const reactRoot = createRoot(root);

initAuth()
  .then((mode) =>
    reactRoot.render(
      <StrictMode>
        <QueryClientProvider client={queryClient}>
          <App auth={mode} />
        </QueryClientProvider>
      </StrictMode>,
    ),
  )
  .catch((error: unknown) => {
    const reason = error instanceof Error ? error.message : String(error);
    reactRoot.render(<div className="notice notice--error">{t('app.signInFailed', { reason })}</div>);
  });
