import type { Page, Route } from '@playwright/test';
import reference from '../fixtures/reference-model.json' with { type: 'json' };
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { relationTypes } from './relationMatrix';

/** Эталонная модель — её же отдаёт выгрузка подменного сервера. */
export const REFERENCE_FILE = fileURLToPath(
  new URL('../../../../../../docs/Hamkorbank_AS_IS_strict.archimate', import.meta.url),
);

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
      if (path === `/models/${this.modelId}/export`) {
        return route.fulfill({ status: 200, contentType: 'application/xml', body: readFileSync(REFERENCE_FILE) });
      }
      if (path === '/metamodel/elements') return json(200, reference.elementTypes);
      if (path === '/metamodel/relations') {
        return json(200, relationTypes(url.searchParams.get('source') ?? '', url.searchParams.get('target') ?? ''));
      }
      const view = path.match(/^\/views\/([^/]+)$/);
      if (view && this.views[view[1]!]) return json(200, this.views[view[1]!]);
      return json(404, { status: 404, title: 'Not Found', code: 'MDL_NOT_FOUND' });
    }

    if (path === '/models/import' && method === 'POST') {
      return json(201, { modelId: this.modelId, status: 'APPLIED' });
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
    if (method === 'PATCH' && (path.startsWith('/elements/') || path.startsWith('/relationships/') || path.startsWith('/view-'))) {
      return json(200, body);
    }
    if (method === 'PUT' && /^\/view-nodes\/[^/]+\/parent$/.test(path)) return json(200, body);
    if (method === 'POST' && path.endsWith('/auto-layout')) return json(200, layered(body as LayoutRequest));
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

type LayoutRequest = {
  nodes: Array<{ id: string; parentId?: string; conceptType?: string; width: number; height: number }>;
  edges: Array<{ id: string }>;
};

const BANDS = ['MOTIVATION', 'STRATEGY', 'BUSINESS', 'APPLICATION', 'TECHNOLOGY', 'PHYSICAL', 'IMPLEMENTATION', 'OTHER'];

/**
 * Раскладка подменного сервера: полосы по слоям, ряды по шесть. Алгоритм сервера
 * проверяют его юнит-тесты; здесь важно, что интерфейс применяет ответ одним шагом.
 */
function layered(request: LayoutRequest) {
  const layerOf = new Map(reference.elementTypes.map((t) => [t.archiType, t.layer]));
  const ids = new Set(request.nodes.map((n) => n.id));
  const roots = request.nodes.filter((n) => !n.parentId || !ids.has(n.parentId));
  const band = (n: LayoutRequest['nodes'][number]) => BANDS.indexOf(layerOf.get(n.conceptType ?? '') ?? 'OTHER');
  const sorted = [...roots].sort((a, b) => band(a) - band(b));
  const nodes: Array<{ id: string; x: number; y: number; width: number; height: number }> = [];
  let y = 24;
  let previous = -1;
  let column = 0;
  let rowHeight = 0;
  for (const node of sorted) {
    if (band(node) !== previous || column === 6) {
      if (previous !== -1) y += rowHeight + (band(node) !== previous ? 72 : 36);
      previous = band(node);
      column = 0;
      rowHeight = 0;
    }
    const width = node.width > 0 ? node.width : 120;
    const height = node.height > 0 ? node.height : 55;
    nodes.push({ id: node.id, x: 24 + column * 200, y, width: node.width, height: node.height });
    rowHeight = Math.max(rowHeight, height);
    column += width > 180 ? 2 : 1;
  }
  return { nodes, edges: request.edges.map((e) => ({ id: e.id, bendpoints: [] })) };
}
