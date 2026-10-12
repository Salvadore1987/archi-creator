import { expect, test, type Page } from '@playwright/test';
import { editorState, openModel, openView, type MockApi } from './support/mockApi';

/** Взять узел за середину и отпустить над серединой другого. */
async function dropOnto(page: Page, viewId: string, nodeId: string, targetId: string) {
  const canvas = page.locator(`[data-testid="canvas"][data-view="${viewId}"]`);
  const node = (await canvas.locator(`.react-flow__node[data-id="${nodeId}"]`).boundingBox())!;
  const target = (await canvas.locator(`.react-flow__node[data-id="${targetId}"]`).boundingBox())!;
  await page.mouse.move(node.x + node.width / 2, node.y + node.height / 2);
  await page.mouse.down();
  await page.mouse.move(target.x + target.width / 2, target.y + target.height / 2, { steps: 10 });
  await page.mouse.up();
}

/** Два узла верхнего уровня, между элементами которых ещё нет связей. */
function unrelatedPair(api: MockApi, viewId: string) {
  const view = api.views[viewId]!;
  const related = (a: unknown, b: unknown) =>
    api.tree.relationships.some((r) => (r.sourceId === a && r.targetId === b) || (r.sourceId === b && r.targetId === a));
  const roots = view.nodes.filter((n) => !n.parentId && n.elementId);
  const pairs = roots.flatMap((a) => roots.filter((b) => b !== a).map((b) => [a, b] as const));
  const [parent, child] = pairs.find(([a, b]) => !related(a.elementId, b.elementId))!;
  const named = (n: (typeof roots)[number]) => ({
    id: n.id as string,
    elementId: n.elementId as string,
    name: api.tree.elements.find((e) => e.id === n.elementId)!.name as string,
  });
  return { parent: named(parent), child: named(child) };
}

test('UI-004: вложение компонента в компонент создаёт композицию — она в дереве и во «Описании»', async ({ page }) => {
  const api = await openModel(page);
  const viewId = await openView(page, api, '03 ');
  const { parent, child } = unrelatedPair(api, viewId);

  await dropOnto(page, viewId, child.id as string, parent.id as string);

  await expect
    .poll(() => editorState<string | undefined>(page, `s.doc.loadedViews['${viewId}'].nodes['${child.id}'].parentId`))
    .toBe(parent.id);
  const implied = await editorState<string[]>(
    page,
    `Object.values(s.doc.relationships).filter((r) => r.sourceId === '${parent.elementId}' && r.targetId === '${child.elementId}').map((r) => r.archiType)`,
  );
  expect(implied).toEqual(['archimate:CompositionRelationship']);
  await expect(page.getByTestId('undo')).toHaveAttribute('title', new RegExp(`Вложено в «${parent.name}»`));

  await page.getByPlaceholder('Поиск по модели').fill(parent.name);
  await page.locator(`[data-row="${parent.elementId}"]`).first().click();
  await page.getByRole('tab', { name: 'Описание' }).click();
  const all = page.getByTestId('description').getByRole('button', { name: /показать все/ });
  if (await all.isVisible()) await all.click();
  await expect(page.getByTestId('description').getByTestId('relation-row').filter({ hasText: child.name })).toContainText(
    'состоит из',
  );

  await page.getByTestId('save').click();
  await expect(page.getByTestId('save')).toBeHidden();
  const writes = api.writes().map((c) => `${c.method} ${c.path}`);
  expect(writes).toContain(`PUT /view-nodes/${child.id}/parent`);
  expect(writes).toContain(`POST /models/${api.modelId}/relationships`);
  const reparent = api.writes().find((c) => c.path === `/view-nodes/${child.id}/parent`)!;
  expect((reparent.body as { parentId: string }).parentId).toBe(parent.id);
});

test('UI-004: родитель едет с детьми; вынос возвращает в корень, связь остаётся; отмена — одним шагом', async ({ page }) => {
  const api = await openModel(page);
  const viewId = await openView(page, api, '03 ');
  const { parent, child } = unrelatedPair(api, viewId);
  const canvas = page.locator(`[data-testid="canvas"][data-view="${viewId}"]`);
  const parentNode = canvas.locator(`.react-flow__node[data-id="${parent.id}"]`);
  await parentNode.click();
  const corner = (await parentNode.locator('.react-flow__resize-control.handle.bottom.right').boundingBox())!;
  await page.mouse.move(corner.x + corner.width / 2, corner.y + corner.height / 2);
  await page.mouse.down();
  await page.mouse.move(corner.x + 260, corner.y + 160, { steps: 8 });
  await page.mouse.up();
  await dropOnto(page, viewId, child.id as string, parent.id as string);
  await expect
    .poll(() => editorState<string | undefined>(page, `s.doc.loadedViews['${viewId}'].nodes['${child.id}'].parentId`))
    .toBe(parent.id);

  const childBox = (await canvas.locator(`.react-flow__node[data-id="${child.id}"]`).boundingBox())!;
  const parentBox = (await parentNode.boundingBox())!;
  await page.mouse.move(parentBox.x + 12, parentBox.y + 12);
  await page.mouse.down();
  await page.mouse.move(parentBox.x + 12 + 24, parentBox.y + 12 + 18, { steps: 6 });
  await page.mouse.up();
  const childAfter = (await canvas.locator(`.react-flow__node[data-id="${child.id}"]`).boundingBox())!;
  expect(Math.round(childAfter.x - childBox.x)).toBeGreaterThan(10);
  expect(await editorState<string>(page, `s.doc.loadedViews['${viewId}'].nodes['${child.id}'].parentId`)).toBe(parent.id);
  expect(await editorState<string | undefined>(page, `s.doc.loadedViews['${viewId}'].nodes['${parent.id}'].parentId`)).toBeUndefined();

  const nestedCount = await editorState<number>(page, 'Object.keys(s.doc.relationships).length');
  const box = (await canvas.locator(`.react-flow__node[data-id="${child.id}"]`).boundingBox())!;
  const pane = (await canvas.boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(pane.x + pane.width - 40, pane.y + pane.height - 60, { steps: 10 });
  await page.mouse.up();
  await expect
    .poll(() => editorState<string | undefined>(page, `s.doc.loadedViews['${viewId}'].nodes['${child.id}'].parentId`))
    .toBeUndefined();
  await expect(page.getByTestId('undo')).toHaveAttribute('title', /Вынесено из контейнера/);
  expect(await editorState<number>(page, 'Object.keys(s.doc.relationships).length')).toBe(nestedCount);
  await page.keyboard.press('ControlOrMeta+z');

  const relationships = await editorState<number>(page, 'Object.keys(s.doc.relationships).length');
  await page.keyboard.press('ControlOrMeta+z');
  await page.keyboard.press('ControlOrMeta+z');
  expect(await editorState<string | undefined>(page, `s.doc.loadedViews['${viewId}'].nodes['${child.id}'].parentId`)).toBeUndefined();
  expect(await editorState<number>(page, 'Object.keys(s.doc.relationships).length')).toBe(relationships - 1);
});

test('UI-004: ребро к вложенному узлу не поднимается над узлами уровня этого узла', async ({ page }) => {
  const api = await openModel(page);
  const viewId = await openView(page, api, '01 ');
  const view = api.views[viewId]!;
  const depth = (id: unknown): number => {
    const node = view.nodes.find((n) => n.id === id);
    return node?.parentId ? 1 + depth(node.parentId) : 0;
  };
  const nested = view.edges.find((e) => depth(e.sourceId) > 0 || depth(e.targetId) > 0)!;
  expect(nested).toBeDefined();
  const expected = Math.max(depth(nested.sourceId), depth(nested.targetId));
  const zIndex = await page
    .locator(`[data-testid="canvas"][data-view="${viewId}"] .react-flow__edge[data-id="${nested.id}"]`)
    .evaluate((edge) => getComputedStyle(edge.closest('svg')!).zIndex);
  expect(Number(zIndex)).toBe(expected);
});
