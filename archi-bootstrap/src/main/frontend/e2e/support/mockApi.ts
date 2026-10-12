import type { Page, Route } from '@playwright/test';
import reference from '../fixtures/reference-model.json' with { type: 'json' };

type Json = Record<string, unknown>;

export interface RecordedCall {
  method: string;
  path: string;
  body: unknown;
}

export interface MockOptions {
  roles?: string[];
  /** Модель заблокирована другим пользователем. */
  lockedBy?: string;
}

/**
 * Сервер на снимке эталонной модели. Чтение отвечает из снимка, команды
 * правки записываются и отвечают как сервер: создание — `201` с телом,
 * остальное — `204`. Состояние сервера не меняется — тесты проверяют,
 * какие команды ушли.
 */
export class MockApi {
  readonly calls: RecordedCall[] = [];
  readonly tree: Json & { model: Json; elements: Json[]; views: Json[]; folders: Json[]; relationships: Json[] };
  readonly views: Record<string, Json & { nodes: Json[]; edges: Json[] }>;
  private versionNo = 1;

  constructor(private readonly options: MockOptions = {}) {
    const copy = JSON.parse(JSON.stringify(reference));
    this.tree = copy.tree;
    this.views = copy.views;
  }

  get modelId(): string {
    return this.tree.model.id as string;
  }

  viewByName(prefix: string): Json & { id: string; nodes: Json[]; edges: Json[] } {
    return Object.values(this.views).find((v) => String(v.name).startsWith(prefix)) as never;
  }

  element(name: string): Json & { id: string } {
    return this.tree.elements.find((e) => e.name === name) as never;
  }

  writes(): RecordedCall[] {
    return this.calls.filter((c) => c.method !== 'GET');
  }

  async install(page: Page): Promise<void> {
    await page.route('**/api/v1/**', (route) => this.handle(route));
  }

  private async handle(route: Route): Promise<void> {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace(/^\/api\/v1/, '');
    const method = request.method();
    let body: unknown = undefined;
    try {
      body = request.postDataJSON();
    } catch {
      body = request.postData();
    }
    this.calls.push({ method, path, body });
    const json = (status: number, data?: unknown) =>
      route.fulfill({
        status,
        contentType: status >= 400 ? 'application/problem+json' : 'application/json',
        body: data === undefined ? '' : JSON.stringify(data),
      });

    if (method === 'GET') {
      if (path === '/ui-config') return json(200, {});
      if (path === '/me') return json(200, { subject: 'e2e', displayName: 'Тест Тестов', roles: this.options.roles ?? ['ARCHITECT'] });
      if (path === '/models') return json(200, [this.tree.model]);
      if (path === `/models/${this.modelId}`) return json(200, this.tree);
      if (path === `/models/${this.modelId}/validate`) return json(200, reference.findings);
      if (path === `/models/${this.modelId}/versions`)
        return json(200, [{ versionNo: this.versionNo, author: 'e2e', createdAt: '2026-10-09T08:00:00Z', snapshotAvailable: true }]);
      if (path === `/models/${this.modelId}/lock`) return json(204);
      if (path === '/metamodel/elements') return json(200, reference.elementTypes);
      const view = path.match(/^\/views\/([^/]+)$/);
      if (view && this.views[view[1]!]) return json(200, this.views[view[1]!]);
      return json(404, { status: 404, title: 'Not Found', code: 'MDL_NOT_FOUND' });
    }

    if (path === `/models/${this.modelId}/lock`) {
      if (method === 'DELETE') return json(204);
      if (this.options.lockedBy) {
        return json(409, { status: 409, title: 'Conflict', code: 'INV-MDL-006', lockOwner: this.options.lockedBy });
      }
      return json(200, { modelId: this.modelId, owner: 'e2e', acquiredAt: '2026-10-09T08:00:00Z', expiresAt: '2099-01-01T00:00:00Z' });
    }
    if (path === `/models/${this.modelId}/versions` && method === 'POST') {
      this.versionNo++;
      return json(201, {
        created: true,
        version: { versionNo: this.versionNo, author: 'e2e', createdAt: new Date().toISOString(), snapshotAvailable: true },
      });
    }
    if (method === 'POST' && (path.endsWith('/elements') || path.endsWith('/folders') || path.endsWith('/relationships') || path.endsWith('/nodes') || path.endsWith('/edges'))) {
      return json(201, body);
    }
    if (method === 'PATCH' && (path.startsWith('/elements/') || path.startsWith('/relationships/'))) {
      return json(200, body);
    }
    return json(204);
  }
}

/** Открыть эталонную модель с подменённым API и дождаться дерева. */
export async function openModel(page: Page, options: MockOptions = {}): Promise<MockApi> {
  const api = new MockApi(options);
  await api.install(page);
  await page.goto(`/?model=${api.modelId}`);
  await page.getByTestId('model-tree').waitFor();
  return api;
}

/** Открыть представление двойным кликом в дереве: через поиск, чтобы строка точно была видна. */
export async function openView(page: Page, api: MockApi, prefix: string): Promise<string> {
  const view = api.viewByName(prefix);
  await page.locator(`[data-row="${view.id}"]`).dblclick();
  await page.locator(`[data-testid="canvas"][data-view="${view.id}"] .react-flow__node`).first().waitFor();
  return view.id;
}

/** Состояние документа сессии — видно из dev-сборки. */
export function editorState<T>(page: Page, pick: string): Promise<T> {
  return page.evaluate((expr) => {
    const state = (window as unknown as { __archiEditor: { getState(): unknown } }).__archiEditor.getState();
    return new Function('s', `return (${expr})`)(state);
  }, pick) as Promise<T>;
}
