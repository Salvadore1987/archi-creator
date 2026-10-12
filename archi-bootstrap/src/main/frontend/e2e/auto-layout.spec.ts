import { expect, test } from '@playwright/test';
import { editorState, openModel, openView } from './support/mockApi';

type Box = { id: string; elementId?: string; parentId?: string; x: number; y: number; height: number };

test('UI-005: авторазметка — приложения над технологиями, одна операция, один Ctrl+Z', async ({ page }) => {
  const api = await openModel(page);
  const viewId = await openView(page, api, '04 ');
  const nodes = () => editorState<Box[]>(page, `Object.values(s.doc.loadedViews['${viewId}'].nodes)`);
  const before = await nodes();

  await page.getByTestId('auto-layout').click();
  await page.locator('[data-strategy="layered"]').click();
  await expect(page.getByTestId('undo')).toHaveAttribute('title', /Авторазметка: По слоям ArchiMate/);

  const after = (await nodes()).filter((n) => !n.parentId);
  const layerOf = (n: Box) => api.tree.elements.find((e) => e.id === n.elementId)?.layer;
  const application = after.filter((n) => layerOf(n) === 'APPLICATION');
  const technology = after.filter((n) => layerOf(n) === 'TECHNOLOGY');
  expect(application.length).toBeGreaterThan(0);
  expect(technology.length).toBeGreaterThan(0);
  expect(Math.max(...application.map((n) => n.y + n.height))).toBeLessThan(Math.min(...technology.map((n) => n.y)));
  expect(await editorState<number>(page, 's.history.ops.length')).toBe(1);

  const request = api.calls.find((c) => c.path === `/views/${viewId}/auto-layout`)!;
  expect((request.body as { nodes: unknown[] }).nodes).toHaveLength(before.length);

  await page.getByTestId('model-tree').locator('.panel-head').click();
  await page.keyboard.press('ControlOrMeta+z');
  const restored = await nodes();
  expect(restored.map((n) => [n.id, n.x, n.y])).toEqual(before.map((n) => [n.id, n.x, n.y]));

  await page.keyboard.press('ControlOrMeta+Shift+z');
  await page.getByTestId('save').click();
  await expect(page.getByTestId('save')).toBeHidden();
  expect(api.writes().some((c) => c.method === 'PUT' && c.path === `/views/${viewId}/layout`)).toBe(true);
});

test('UI-005: зритель не видит авторазметки', async ({ page }) => {
  const api = await openModel(page, { roles: ['VIEWER'] });
  await openView(page, api, '07 ');
  await expect(page.getByTestId('auto-layout')).toHaveCount(0);
});
