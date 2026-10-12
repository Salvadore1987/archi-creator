import { expect, test } from '@playwright/test';
import { openModel } from './support/mockApi';

test('UI-007: панель свойств правит объект модели', async ({ page }) => {
  const api = await openModel(page);
  const iabs = api.element('IABS');
  await page.getByPlaceholder('Поиск по модели').fill('IABS');
  await page.locator(`[data-row="${iabs.id}"]`).click();

  await expect(page.getByTestId('properties')).toContainText('Компонент приложения');
  await page.getByTestId('prop-documentation').fill('Ядро учёта банка');
  await page.getByTestId('prop-name').click();
  await page.getByRole('button', { name: 'Добавить свойство' }).click();
  await page.getByPlaceholder('ключ').last().fill('sla');
  await page.getByPlaceholder('значение').last().fill('99.9');
  await page.getByTestId('prop-name').click();
  await expect(page.getByTestId('unsaved')).toHaveText('несохранённых изменений: 2');

  await page.getByTestId('save').click();
  await expect(page.getByTestId('save')).toBeHidden();
  const patches = api.writes().filter((c) => c.method === 'PATCH' && c.path === `/elements/${iabs.id}`);
  expect(patches.map((c) => c.body)).toEqual([
    { documentation: 'Ядро учёта банка' },
    { properties: expect.arrayContaining([{ key: 'sla', value: '99.9' }]) },
  ]);
});

test('UI-007: у связи правятся имя и документация, концы видны', async ({ page }) => {
  const api = await openModel(page);
  const relationship = api.tree.relationships[0] as { id: string };
  await page.locator('.tr--root', { hasText: 'Relations' }).click();
  await page.evaluate((id) => {
    (window as unknown as { __archiEditor: { getState(): { select(ids: string[], o: string): void } } }).__archiEditor
      .getState()
      .select([id], 'external');
  }, relationship.id);
  await expect(page.getByTestId('properties')).toContainText('Источник');
  await expect(page.getByTestId('prop-name')).toBeEditable();
});
