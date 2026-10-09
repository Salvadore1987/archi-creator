import { expect, test } from '@playwright/test';
import { openModel } from './support/mockApi';

test('UI-015: читателю — «Описание», только чтение, без палитры и инструментов', async ({ page }) => {
  const api = await openModel(page, { roles: ['VIEWER'] });
  await expect(page.getByTestId('lock-chip')).toHaveText(/только чтение/);
  await expect(page.getByTestId('palette')).toHaveCount(0);
  await expect(page.getByTestId('edit-toolbar')).toHaveCount(0);
  await expect(page.getByRole('tab', { name: 'Описание' })).toHaveAttribute('aria-selected', 'true');

  const iabs = api.element('IABS');
  await page.getByPlaceholder('Поиск по модели').fill('IABS');
  await page.locator(`[data-row="${iabs.id}"]`).click();
  await page.getByRole('tab', { name: 'Свойства' }).click();
  await expect(page.getByTestId('prop-name')).not.toBeEditable();
  await expect(page.getByRole('button', { name: 'Экспорт .archimate' })).toBeVisible();
  expect(api.writes().filter((c) => c.path.endsWith('/lock'))).toHaveLength(0);
});

test('UI-015: чужая блокировка — модель только для чтения с именем владельца', async ({ page }) => {
  await openModel(page, { roles: ['ARCHITECT'], lockedBy: 'ivanov' });
  await expect(page.getByTestId('lock-chip')).toHaveText(/ivanov/);
  await expect(page.getByTestId('undo')).toBeDisabled();
});
