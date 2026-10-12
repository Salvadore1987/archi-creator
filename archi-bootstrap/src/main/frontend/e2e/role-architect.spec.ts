import { expect, test } from '@playwright/test';
import { openModel } from './support/mockApi';

test('UI-015: архитектору — «Свойства», палитра и инструменты', async ({ page }) => {
  const api = await openModel(page, { roles: ['ARCHITECT'] });
  await expect(page.getByTestId('lock-chip')).toHaveText(/Редактирует/);
  await expect(page.getByTestId('palette')).toBeVisible();
  await expect(page.getByTestId('edit-toolbar')).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Свойства' })).toHaveAttribute('aria-selected', 'true');
  expect(api.writes().some((c) => c.method === 'POST' && c.path.endsWith('/lock'))).toBe(true);
});
