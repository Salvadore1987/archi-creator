import { useQuery } from '@tanstack/react-query';
import { fetchHealth } from './health';
import { useModelStore } from './store';

/**
 * Экран этапа 0. Модели ещё нет, редактора тоже — единственное, что каркас
 * обязан показать, это что фронтенд собран, отдан приложением и достаёт
 * до него по сети. Реальный экран (дерево, канва, панель свойств) приходит
 * на этапе 3, docs/frontend.md §6.
 */
export function App() {
  const modelId = useModelStore((state) => state.modelId);
  const health = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: false });

  const status = health.isError ? 'DOWN' : (health.data?.status ?? 'UNKNOWN');
  const statusClass =
    status === 'UP' ? 'status--up' : status === 'UNKNOWN' ? 'status--unknown' : 'status--down';

  return (
    <div className="shell">
      <main className="panel">
        <h1>Archi Creator</h1>
        <p>Каркас этапа 0. Редактор появится на этапе 3.</p>

        <dl>
          <div className="row">
            <dt>Бэкенд</dt>
            <dd className={statusClass}>{health.isFetching ? '…' : status}</dd>
          </div>
          <div className="row">
            <dt>Открытая модель</dt>
            <dd>{modelId ?? 'нет'}</dd>
          </div>
        </dl>

        <button type="button" onClick={() => void health.refetch()} disabled={health.isFetching}>
          Проверить снова
        </button>
      </main>
    </div>
  );
}
