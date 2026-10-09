import { useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { Me } from '../api/types';
import type { AuthMode } from '../auth/session';
import { IconSprite } from '../canvas/IconSprite';
import { Editor } from './Editor';
import { UiIconSprite } from './Icon';
import { ModelList } from './ModelList';
import { useModelRoute } from './route';
import { Toaster } from './Toaster';

/** Заглушка входа профиля dev: сервер подставляет архитектора и без `/me`. */
const STUB_ME: Me = { subject: 'dev-architect', displayName: 'dev-architect', roles: ['ARCHITECT'] };

export function App({ auth }: { auth: AuthMode }) {
  const modelId = useModelRoute();
  const me = useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      try {
        return await api.me();
      } catch (error) {
        if (auth.kind === 'stub') return STUB_ME;
        throw error;
      }
    },
    staleTime: Infinity,
  });

  return (
    <>
      <UiIconSprite />
      <IconSprite />
      {modelId ? (
        <Editor key={modelId} modelId={modelId} me={me.data} auth={auth} />
      ) : (
        <ModelList me={me.data} auth={auth} />
      )}
      <Toaster />
    </>
  );
}
