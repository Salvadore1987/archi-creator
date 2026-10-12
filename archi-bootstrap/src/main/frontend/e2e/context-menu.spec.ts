import { expect, test } from '@playwright/test';
import { openModel } from './support/mockApi';

test.describe('UI-018: контекстное меню по роли', () => {
  test('у системной папки нет пункта удаления', async ({ page }) => {
    await openModel(page);
    await page.locator('.tr--root', { hasText: 'Application' }).click({ button: 'right' });
    await expect(page.getByRole('menu')).toBeVisible();
    await expect(page.getByRole('menuitem', { name: 'Удалить' })).toHaveCount(0);
    await expect(page.getByRole('menuitem', { name: 'Создать вложенную папку' })).toBeVisible();
  });

  test('системную папку не удалить и клавишей — с объяснением', async ({ page }) => {
    await openModel(page);
    await page.locator('.tr--root', { hasText: 'Application' }).click();
    await page.getByRole('tree').press('Delete');
    await expect(page.getByRole('status')).toContainText('Системная папка формата Archi');
  });

  test('читатель получает только неизменяющие пункты', async ({ page }) => {
    const api = await openModel(page, { roles: ['VIEWER'] });
    const iabs = api.element('IABS');
    await page.getByPlaceholder('Поиск по модели').fill('IABS');
    await page.locator(`[data-row="${iabs.id}"]`).click({ button: 'right' });
    const items = await page.getByRole('menuitem').allTextContents();
    expect(items).toEqual(['Показать на представлении', 'Где используется', 'Копировать имя']);
  });

  test('«Где используется» открывает карточку элемента', async ({ page }) => {
    const api = await openModel(page);
    const iabs = api.element('IABS');
    await page.getByPlaceholder('Поиск по модели').fill('IABS');
    await page.locator(`[data-row="${iabs.id}"]`).click({ button: 'right' });
    await page.getByRole('menuitem', { name: 'Где используется' }).click();
    await expect(page.getByTestId('description')).toContainText('Размещён в представлениях');
  });
});
