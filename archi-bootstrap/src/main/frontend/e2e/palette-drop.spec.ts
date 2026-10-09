import { expect, test } from '@playwright/test';
import { editorState, openModel, openView } from './support/mockApi';

test('UI-002: перетаскивание с палитры создаёт элемент и его узел', async ({ page }) => {
  const api = await openModel(page);
  const viewId = await openView(page, api, '01 ');
  const before = await page.locator('.react-flow__node').count();

  await page.locator('.pal[data-archi-type="archimate:ApplicationComponent"]').dragTo(page.getByTestId('canvas'), {
    targetPosition: { x: 200, y: 80 },
  });
  await expect(page.locator('.react-flow__node')).toHaveCount(before + 1);
  const created = await editorState<{ name: string; layer: string }[]>(
    page,
    "Object.values(s.doc.elements).filter(e => e.name === 'Компонент приложения')",
  );
  expect(created).toHaveLength(1);

  await page.getByTestId('save').click();
  await expect(page.getByTestId('save')).toBeHidden();
  const writes = api.writes().map((c) => `${c.method} ${c.path}`);
  expect(writes).toContain(`POST /models/${api.modelId}/elements`);
  expect(writes).toContain(`POST /views/${viewId}/nodes`);
  expect(writes.indexOf(`POST /models/${api.modelId}/elements`)).toBeLessThan(writes.indexOf(`POST /views/${viewId}/nodes`));
});
