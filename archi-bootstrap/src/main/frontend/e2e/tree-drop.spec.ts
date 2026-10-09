import { expect, test } from '@playwright/test';
import { openModel, openView } from './support/mockApi';

test('UI-002: из дерева — только размещение, повтор не создаёт дубля', async ({ page }) => {
  const api = await openModel(page);
  const viewId = await openView(page, api, '01 ');
  const view = api.views[viewId]!;
  const placed = new Set(view.nodes.map((n) => n.elementId));
  const candidate = api.tree.elements.find((e) => e.supported && !placed.has(e.id as string)) as { id: string; name: string };

  await page.getByPlaceholder('Поиск по модели').fill(candidate.name);
  const row = page.locator(`[data-row="${candidate.id}"]`);
  const canvas = page.getByTestId('canvas');
  await row.dragTo(canvas, { targetPosition: { x: 150, y: 60 } });
  await expect(page.locator(`.react-flow__node:has([data-element="${candidate.id}"])`)).toHaveCount(1);

  await row.dragTo(canvas, { targetPosition: { x: 400, y: 60 } });
  await expect(page.locator(`.react-flow__node:has([data-element="${candidate.id}"])`)).toHaveCount(1);
  await expect(page.getByRole('status')).toContainText('уже на этом представлении');

  await page.getByTestId('save').click();
  await expect(page.getByTestId('save')).toBeHidden();
  expect(api.writes().filter((c) => c.path === `/models/${api.modelId}/elements`)).toHaveLength(0);
  expect(api.writes().filter((c) => c.path === `/views/${viewId}/nodes`)).toHaveLength(1);
});
