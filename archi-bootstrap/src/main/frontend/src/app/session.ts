import { useCallback, useEffect } from 'react';
import { api } from '../api/endpoints';
import { ApiError } from '../api/http';
import type { Role, Uuid } from '../api/types';
import { unsavedCount } from '../model/history';
import { uuidv7 } from '../model/ids';
import { useEditor } from '../model/store';
import { describeError } from './errors';
import { useToast } from './toast';
import { t } from '../i18n';

const EDITOR_ROLES: Role[] = ['ARCHITECT', 'ADMIN'];
/** Блокировка живёт полчаса; продлеваем с запасом, чтобы пауза в работе её не теряла. */
const LOCK_RENEW_MS = 5 * 60_000;

export function isEditorRole(roles: Role[]): boolean {
  return roles.some((role) => EDITOR_ROLES.includes(role));
}

/** Править можно архитектору с собственной блокировкой и не во время отправки. */
export function useCanEdit(): boolean {
  return useEditor((s) => isEditorRole(s.roles) && s.lock.kind === 'mine' && s.save.kind !== 'saving');
}

/**
 * Блокировка на время открытой модели: архитектор берёт её при открытии,
 * продлевает, отпускает при закрытии. Чужая блокировка — модель только
 * для чтения с именем владельца; запись сервер всё равно отклонит.
 */
export function useModelLock(modelId: Uuid, roles: Role[], ready: boolean): void {
  const setLock = useEditor((s) => s.setLock);

  useEffect(() => {
    if (!ready) {
      return;
    }
    if (!isEditorRole(roles)) {
      setLock({ kind: 'readonly' });
      return;
    }
    let cancelled = false;
    let held = false;
    const acquire = async () => {
      try {
        const lock = await api.acquireLock(modelId);
        held = true;
        if (!cancelled) setLock({ kind: 'mine', expiresAt: lock.expiresAt });
      } catch (error) {
        if (cancelled) return;
        if (error instanceof ApiError && error.status === 409) {
          held = false;
          setLock({ kind: 'theirs', owner: error.problem.lockOwner ?? '?', expiresAt: error.problem.expiresAt });
        } else {
          useToast.getState().show(describeError(error), { tone: 'error' });
        }
      }
    };
    void acquire();
    const timer = window.setInterval(() => void acquire(), LOCK_RENEW_MS);
    const release = () => {
      if (held) void api.releaseLock(modelId, true).catch(() => {});
    };
    window.addEventListener('pagehide', release);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
      window.removeEventListener('pagehide', release);
      release();
    };
  }, [modelId, roles, ready, setLock]);
}

/** Браузер предупреждает о несохранённом при закрытии вкладки. */
export function useUnloadGuard(): void {
  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => {
      if (unsavedCount(useEditor.getState().history) > 0) {
        event.preventDefault();
      }
    };
    window.addEventListener('beforeunload', guard);
    return () => window.removeEventListener('beforeunload', guard);
  }, []);
}

/** Сохранение: сервер догоняет документ, затем фиксируется версия. */
export function useSave(): () => Promise<void> {
  return useCallback(async () => {
    const state = useEditor.getState();
    if (!state.doc || state.save.kind === 'saving') {
      return;
    }
    const modelId = state.doc.model.id;
    const ok = await state.sync();
    if (!ok) {
      const error = useEditor.getState().save;
      useToast.getState().show(
        t('status.saveFailed', { reason: describeError(error.kind === 'error' ? error.error : undefined) }),
        { tone: 'error' },
      );
      return;
    }
    try {
      useEditor.getState().setSaveState({ kind: 'saving' });
      const result = await api.saveVersion(modelId, uuidv7());
      useEditor.getState().markVersionSaved(result.version);
    } catch (error) {
      useEditor.getState().setSaveState({ kind: 'error', error });
      useToast.getState().show(t('status.saveFailed', { reason: describeError(error) }), { tone: 'error' });
    }
  }, []);
}
