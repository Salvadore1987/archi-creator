import { expect, test } from '@playwright/test';
import { editorState, openModel, openView } from './support/mockApi';

test('UI-008: переключение вкладок не теряет масштаб и выделение', async ({ page }) => {
  const api = await openModel(page);
  const first = await openView(page, api, '01 ');
  const second = await openView(page, api, '03 ');
  await page.locator(`[data-view-tab="${first}"]`).click();
  await page.getByTitle('Крупнее').click();
  await page.getByTitle('Крупнее').click();
  await page.waitForTimeout(400);
  const zoomed = await page.locator('.zoomer__val').textContent();
  const iabs = api.element('IABS');
  await page.locator(`.react-flow__node:has([data-element="${iabs.id}"])`).click();

  await page.locator(`[data-view-tab="${second}"]`).click();
  await expect(page.locator(`[data-testid="canvas"][data-view="${second}"]`)).toBeVisible();
  await page.locator(`[data-view-tab="${first}"]`).click();
  await expect(page.locator('.zoomer__val')).toHaveText(zoomed!);
  await expect(page.locator(`.react-flow__node:has([data-element="${iabs.id}"])`)).toHaveClass(/selected/);
  expect(await editorState<string[]>(page, 's.openViews.length')).toBe(2);
});

test('UI-008: несохранённые изменения общие для модели, а не для вкладки', async ({ page }) => {
  const api = await openModel(page);
  await openView(page, api, '01 ');
  const physical = api.element('Физическое лицо');
  await page.locator(`.react-flow__node:has([data-element="${physical.id}"])`).click();
  await page.getByTestId('canvas').press('Delete');
  await openView(page, api, '03 ');
  await expect(page.getByTestId('unsaved')).toHaveText('несохранённых изменений: 1');
});
