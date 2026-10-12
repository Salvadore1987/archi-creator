import { UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { ApiError, configureAuth } from '../api/http';
import { api } from '../api/endpoints';
import type { UiConfig } from '../api/types';

/** Как вошли: через Keycloak или заглушкой профиля dev, где сервер сам подставляет архитектора. */
export type AuthMode = { kind: 'oidc'; manager: UserManager } | { kind: 'stub' };

/**
 * Вход. Сервер говорит, куда идти за токеном; без адреса Keycloak — профиль
 * dev, и токен не нужен. Код авторизации с PKCE: секрета у SPA нет.
 * Токен держится в sessionStorage — вкладка живёт своей сессией.
 */
export async function initAuth(): Promise<AuthMode> {
  const config = await loadConfig();
  if (!config.oidc) {
    configureAuth(async () => null, () => {});
    return { kind: 'stub' };
  }
  const manager = new UserManager({
    authority: config.oidc.authority,
    client_id: config.oidc.clientId,
    redirect_uri: `${window.location.origin}${window.location.pathname}`,
    post_logout_redirect_uri: `${window.location.origin}${window.location.pathname}`,
    response_type: 'code',
    scope: 'openid profile',
    automaticSilentRenew: true,
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  });
  const params = new URLSearchParams(window.location.search);
  if (params.has('code') && params.has('state')) {
    const user = await manager.signinRedirectCallback();
    const returnTo = typeof user.state === 'string' ? user.state : window.location.pathname;
    window.history.replaceState(null, '', returnTo);
  }
  let user = await manager.getUser();
  if (!user || user.expired) {
    await manager.signinRedirect({ state: window.location.pathname + window.location.search });
    // Страница уходит на Keycloak; дальше этот код не исполняется.
    await new Promise(() => {});
  }
  configureAuth(
    async () => {
      user = await manager.getUser();
      return user?.access_token ?? null;
    },
    () => void manager.signinRedirect({ state: window.location.pathname + window.location.search }),
  );
  return { kind: 'oidc', manager };
}

async function loadConfig(): Promise<UiConfig> {
  try {
    return await api.uiConfig();
  } catch (error) {
    // Сервер без настройки входа отвечает 404 — значит, вход ему не нужен.
    if (error instanceof ApiError && error.status === 404) {
      return {};
    }
    throw error;
  }
}

export async function signOut(mode: AuthMode): Promise<void> {
  if (mode.kind === 'oidc') {
    await mode.manager.signoutRedirect();
  }
}
