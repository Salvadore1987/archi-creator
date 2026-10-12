import { expect, test, type Page } from '@playwright/test';
import { editorState, openModel, openView } from './support/mockApi';
import { relationTypes } from './support/relationMatrix';

/** Протянуть связь от верхней границы одного узла к центру другого. */
async function drawRelation(page: Page, viewId: string, sourceNode: string, targetNode: string) {
  const canvas = page.locator(`[data-testid="canvas"][data-view="${viewId}"]`);
  const source = (await canvas.locator(`.react-flow__node[data-id="${sourceNode}"]`).boundingBox())!;
  const target = (await canvas.locator(`.react-flow__node[data-id="${targetNode}"]`).boundingBox())!;
  await page.mouse.move(source.x + source.width / 2, source.y + 1);
  await page.mouse.down();
  await page.mouse.move(target.x + target.width / 2, target.y + target.height / 2, { steps: 8 });
  await expect(page.getByTestId('connect-line')).toBeVisible();
  await page.mouse.up();
}

test('UI-003: список для пары ApplicationComponent → BusinessProcess совпадает с матрицей', async ({ page }) => {
  const api = await openModel(page);
  const viewId = await openView(page, api, '07 ');
  const view = api.views[viewId]!;
  const typeOf = (elementId: unknown) => api.tree.elements.find((e) => e.id === elementId)?.archiType;
  const related = (a: unknown, b: unknown) =>
    api.tree.relationships.some((r) => (r.sourceId === a && r.targetId === b) || (r.sourceId === b && r.targetId === a));
  const components = view.nodes.filter((n) => typeOf(n.elementId) === 'archimate:ApplicationComponent');
  const processes = view.nodes.filter((n) => typeOf(n.elementId) === 'archimate:BusinessProcess');
  const [component, process] = components
    .flatMap((c) => processes.map((p) => [c, p] as const))
    .find(([c, p]) => !related(c.elementId, p.elementId))!;

  await drawRelation(page, viewId, component.id as string, process.id as string);

  const picker = page.getByTestId('relation-picker');
  await expect(picker).toBeVisible();
  const expected = relationTypes('archimate:ApplicationComponent', 'archimate:BusinessProcess');
  const offered = await picker.locator('[data-relation-type]').evaluateAll((items) =>
    items.map((item) => item.getAttribute('data-relation-type')),
  );
  expect(offered).toEqual(expected.map((r) => r.archiType));
  await expect(picker.locator('[aria-selected="true"]')).toHaveAttribute('data-relation-type', expected[0]!.archiType);
  expect(offered).not.toContain('archimate:CompositionRelationship');

  await page.keyboard.press('Enter');
  await expect(picker).toBeHidden();
  const created = await editorState<{ archiType: string; sourceId: string; targetId: string }[]>(
    page,
    `Object.values(s.doc.relationships).filter((r) => r.sourceId === '${component.elementId}' && r.targetId === '${process.elementId}')`,
  );
  expect(created.map((r) => r.archiType)).toEqual([expected[0]!.archiType]);
  await expect(page.getByTestId('undo')).toHaveAttribute('title', /Связь «Обслуживание»/);

  await page.getByTestId('save').click();
  await expect(page.getByTestId('save')).toBeHidden();
  const writes = api.writes().map((c) => `${c.method} ${c.path}`);
  expect(writes).toContain(`POST /models/${api.modelId}/relationships`);
  expect(writes).toContain(`POST /views/${viewId}/edges`);
});

test('UI-003: дубль не создаётся — повторное рисование той же связи только сообщает', async ({ page }) => {
  const api = await openModel(page);
  const viewId = await openView(page, api, '01 ');
  const view = api.views[viewId]!;
  const edge = view.edges.find((e) => view.nodes.some((n) => n.id === e.sourceId) && view.nodes.some((n) => n.id === e.targetId))!;
  const relationship = api.tree.relationships.find((r) => r.id === edge.relationshipId)!;

  await drawRelation(page, viewId, edge.sourceId as string, edge.targetId as string);
  await page.locator(`[data-testid="relation-picker"] [data-relation-type="${relationship.archiType}"]`).click();

  await expect(page.getByText('Эта связь уже нарисована')).toBeVisible();
  expect(await editorState<number>(page, 's.history.ops.length')).toBe(0);
});

test('UI-003: Esc закрывает список, связь не создаётся', async ({ page }) => {
  const api = await openModel(page);
  const viewId = await openView(page, api, '07 ');
  const view = api.views[viewId]!;
  const [a, b] = view.nodes.filter((n) => !n.parentId);
  await drawRelation(page, viewId, a!.id as string, b!.id as string);
  await expect(page.getByTestId('relation-picker')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('relation-picker')).toBeHidden();
  expect(await editorState<number>(page, 's.history.ops.length')).toBe(0);
});
