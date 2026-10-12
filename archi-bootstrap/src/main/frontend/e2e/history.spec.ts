import { expect, test } from '@playwright/test';
import { editorState, openModel, openView } from './support/mockApi';

test('UI-017: 30 операций, возврат к пятой, затем повтор до конца', async ({ page }) => {
  const api = await openModel(page);
  await openView(page, api, '01 ');
  const iabs = api.element('IABS');
  await page.getByPlaceholder('Поиск по модели').fill('IABS');
  await page.locator(`[data-row="${iabs.id}"]`).click();
  const name = page.getByTestId('prop-name');
  for (let i = 1; i <= 30; i++) {
    await name.fill(`IABS ${i}`);
    await name.press('Enter');
  }
  await expect(page.getByTestId('unsaved')).toHaveText('несохранённых изменений: 30');
  await expect(page.getByTestId('undo')).toHaveAttribute('title', /IABS 29.*IABS 30/);

  await page.getByTestId('unsaved').click();
  await page.locator('[data-testid="history-panel"] [data-position="5"]').click();
  await expect(name).toHaveValue('IABS 5');
  await expect(page.locator(`.react-flow__node:has([data-element="${iabs.id}"])`)).toContainText('IABS 5');
  await expect(page.getByTestId('unsaved')).toHaveText('несохранённых изменений: 5');

  await page.getByTestId('model-tree').locator('.panel-head').click();
  for (let i = 0; i < 25; i++) await page.keyboard.press('ControlOrMeta+Shift+z');
  await expect(name).toHaveValue('IABS 30');
});

test('UI-017: сохранение двигает точку сохранения, отмена за неё — снова несохранено', async ({ page }) => {
  const api = await openModel(page);
  const iabs = api.element('IABS');
  await page.getByPlaceholder('Поиск по модели').fill('IABS');
  await page.locator(`[data-row="${iabs.id}"]`).click();
  await page.getByTestId('prop-name').fill('IABS — ядро');
  await page.getByTestId('prop-name').press('Enter');
  await page.getByTestId('save').click();
  await expect(page.getByTestId('save')).toBeHidden();
  expect(await editorState<number>(page, 's.history.saved')).toBe(1);

  await page.getByTestId('undo').click();
  await expect(page.getByTestId('unsaved')).toHaveText('несохранённых изменений: 1');
  await expect(page.locator(`[data-row="${iabs.id}"] .tr__dirty`)).toBeVisible();
  await page.getByTestId('save').click();
  await expect(page.getByTestId('save')).toBeHidden();
  const renames = api.writes().filter((c) => c.path === `/elements/${iabs.id}`).map((c) => c.body);
  expect(renames).toEqual([{ name: 'IABS — ядро' }, { name: 'IABS' }]);
});
