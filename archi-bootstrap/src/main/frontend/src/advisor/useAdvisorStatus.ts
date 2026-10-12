import { useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { AdvisorStatus, Uuid } from '../api/types';

/**
 * Состояние помощника в пространстве модели. Выключен или запрос не удался — `undefined`:
 * панели и кнопок помощника нет, редактор от помощника не зависит.
 */
export function useAdvisorStatus(workspaceId: Uuid | undefined): AdvisorStatus | undefined {
  const status = useQuery({
    queryKey: ['advisor-status', workspaceId],
    queryFn: () => api.advisorStatus(workspaceId!),
    enabled: workspaceId !== undefined,
    retry: false,
    staleTime: 60_000,
  });
  return status.data?.enabled ? status.data : undefined;
}
