import { expect, test } from '@playwright/test';
import { MockApi } from '../e2e/support/mockApi';

/**
 * Канва на объёме требований: представление на 500 объектов открывается
 * за секунду, панорамирование идёт без провалов кадров. Представление
 * синтетическое — элементы эталонной модели, разложенные сеткой, со связями
 * соседей: в эталоне самое крупное — 69 объектов.
 */
function loadView(api: MockApi, size: number): string {
  const elements = api.tree.elements.filter((e) => e.supported) as Array<{ id: string }>;
  const relationships = api.tree.relationships as Array<{ id: string; sourceId: string; targetId: string }>;
  const viewId = '00000000-0000-7000-8000-000000000500';
  const nodes = Array.from({ length: size }, (_, i) => ({
    id: `00000000-0000-7000-8000-${String(i).padStart(12, '0')}`,
    archiId: `id-perf-${i}`,
    archiType: 'archimate:DiagramObject',
    kind: 'DIAGRAM_OBJECT',
    elementId: elements[i % elements.length]!.id,
    x: (i % 25) * 160,
    y: Math.floor(i / 25) * 90,
    width: 120,
    height: 55,
    sortOrder: (i + 1) * 1000,
  }));
  const byElement = new Map(nodes.map((n) => [n.elementId, n.id]));
  const edges = relationships
    .filter((r) => byElement.has(r.sourceId) && byElement.has(r.targetId))
    .map((r, i) => ({
      id: `00000000-0000-7000-9000-${String(i).padStart(12, '0')}`,
      archiId: `id-perf-e${i}`,
      archiType: 'archimate:Connection',
      relationshipId: r.id,
      sourceId: byElement.get(r.sourceId),
      targetId: byElement.get(r.targetId),
      bendpoints: [],
      sortOrder: 0,
    }));
  const diagrams = api.tree.folders.find((f) => f.folderType === 'DIAGRAMS') as { id: string };
  api.tree.views.push({ id: viewId, folderId: diagrams.id, archiId: 'id-perf', archiType: 'archimate:ArchimateDiagramModel', name: '00 Нагрузка', sortOrder: 0 });
  api.views[viewId] = {
    id: viewId, modelId: api.modelId, folderId: diagrams.id, archiId: 'id-perf', archiType: 'archimate:ArchimateDiagramModel',
    name: '00 Нагрузка', properties: [], editable: true, nodes, edges,
  };
  return viewId;
}

test('UI-013: открытие представления на 500 объектов — не дольше секунды', async ({ page }) => {
  const api = new MockApi();
  const viewId = loadView(api, 500);
  await api.install(page);
  await page.goto(`/?model=${api.modelId}`);
  await page.getByTestId('model-tree').waitFor();
  const row = page.locator(`[data-row="${viewId}"]`);
  const started = Date.now();
  await row.dblclick();
  await page.locator(`[data-testid="canvas"][data-view="${viewId}"] .react-flow__node`).first().waitFor();
  const elapsed = Date.now() - started;
  console.info(`canvas-open 500: ${elapsed} ms`);
  expect(elapsed).toBeLessThan(1000);
});

test('UI-013: панорамирование 500 объектов — кадры не длиннее, чем у 50 fps в медиане', async ({ page }) => {
  const api = new MockApi();
  const viewId = loadView(api, 500);
  await api.install(page);
  await page.goto(`/?model=${api.modelId}`);
  await page.locator(`[data-row="${viewId}"]`).dblclick();
  const canvas = page.locator(`[data-testid="canvas"][data-view="${viewId}"]`);
  await canvas.locator('.react-flow__node').first().waitFor();
  const box = (await canvas.boundingBox())!;

  await page.evaluate(() => {
    const w = window as unknown as { __frames: number[] };
    w.__frames = [];
    let last = performance.now();
    const tick = (now: number) => {
      w.__frames.push(now - last);
      last = now;
      if (w.__frames.length < 120) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  });
  // Пустое место холста: тянем за поле, а не за узел.
  await page.mouse.move(box.x + box.width - 30, box.y + box.height - 30);
  await page.mouse.down();
  for (let i = 0; i < 60; i++) {
    await page.mouse.move(box.x + box.width - 30 - i * 8, box.y + box.height - 30 - i * 4);
  }
  await page.mouse.up();
  await page.waitForFunction(() => (window as unknown as { __frames: number[] }).__frames.length >= 120);
  const frames = await page.evaluate(() => (window as unknown as { __frames: number[] }).__frames.slice(1).sort((a, b) => a - b));
  const median = frames[Math.floor(frames.length / 2)]!;
  console.info(`canvas-pan 500: median frame ${median.toFixed(1)} ms, p95 ${frames[Math.floor(frames.length * 0.95)]!.toFixed(1)} ms`);
  expect(median).toBeLessThan(20);
});
