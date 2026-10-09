import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { api } from '../api/endpoints';
import type { Me } from '../api/types';
import type { AuthMode } from '../auth/session';
import { CanvasArea } from '../canvas/CanvasArea';
import { t } from '../i18n';
import { useEditor } from '../model/store';
import { Palette } from '../palette/Palette';
import { Inspector } from '../panels/Inspector';
import { TreePanel } from '../tree/TreePanel';
import { describeError } from './errors';
import { isEditorRole, useModelLock, useUnloadGuard } from './session';
import { StatusBar } from './StatusBar';
import { TopBar } from './TopBar';
import { useShortcuts } from './useShortcuts';

/**
 * Главный экран — четыре панели: слева дерево и палитра, в центре вкладки
 * представлений и холст, справа свойства и описание, снизу статус-строка.
 */
export function Editor({ modelId, me, auth }: { modelId: string; me?: Me; auth: AuthMode }) {
  const tree = useQuery({ queryKey: ['model', modelId], queryFn: () => api.openModel(modelId), staleTime: Infinity });
  const findings = useQuery({ queryKey: ['findings', modelId], queryFn: () => api.validate(modelId) });
  const versions = useQuery({ queryKey: ['versions', modelId], queryFn: () => api.versions(modelId) });
  const loaded = useEditor((s) => s.doc?.model.id === modelId);
  const load = useEditor((s) => s.load);
  const close = useEditor((s) => s.close);
  const setRoles = useEditor((s) => s.setRoles);
  const roles = me?.roles ?? [];
  const editor = isEditorRole(roles);

  useEffect(() => {
    if (tree.data) load(tree.data);
  }, [tree.data, load]);
  useEffect(() => close, [close]);
  useEffect(() => setRoles(roles), [roles, setRoles]);
  useEffect(() => {
    if (findings.data) useEditor.getState().setFindings(findings.data);
  }, [findings.data]);
  useEffect(() => {
    if (versions.data) useEditor.getState().setLastVersion(versions.data[0] ?? null);
  }, [versions.data]);

  useModelLock(modelId, roles, loaded && me !== undefined);
  useUnloadGuard();
  useShortcuts();

  if (tree.isError) {
    return <div className="notice notice--error">{describeError(tree.error)}</div>;
  }
  if (!loaded) {
    return <div className="notice">{t('app.loading')}</div>;
  }

  return (
    <div className={`shell${editor ? '' : ' shell--viewer'}`}>
      <TopBar me={me} auth={auth} />
      <div className="main">
        <aside className="side side--l">
          <TreePanel />
          {editor && (
            <>
              <div className="split" />
              <Palette />
            </>
          )}
        </aside>
        <main className="center">
          <CanvasArea />
        </main>
        <aside className="side side--r">
          <Inspector defaultTab={editor ? 'properties' : 'description'} />
        </aside>
      </div>
      <StatusBar />
    </div>
  );
}
