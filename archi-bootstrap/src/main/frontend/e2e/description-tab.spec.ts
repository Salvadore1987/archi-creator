import { expect, test } from '@playwright/test';
import { openModel } from './support/mockApi';

test.describe('UI-014: вкладка «Описание» читается, а не правится', () => {
  test('карточка IABS: связи, анализ влияния, свойства, представления', async ({ page }) => {
    const api = await openModel(page);
    const iabs = api.element('IABS');
    await page.getByPlaceholder('Поиск по модели').fill('IABS');
    await page.locator(`[data-row="${iabs.id}"]`).click();
    await page.getByRole('tab', { name: 'Описание' }).click();

    const card = page.getByTestId('description');
    await expect(card).toContainText('Участвует в связях · 60');
    await expect(card.getByTestId('relation-row')).toHaveCount(7);
    await card.getByRole('button', { name: 'показать все 60' }).click();
    await expect(card.getByTestId('relation-row')).toHaveCount(60);
    await expect(card.getByTestId('impact')).toContainText('напрямую затрагиваются 12');
    await expect(card.getByTestId('impact')).toContainText('ещё 12 элементов второго уровня');
    await expect(card.getByTestId('impact')).toContainText('44 вложенных');
    await expect(card).toContainText('Oracle 19c');
    await expect(card.locator('input, textarea')).toHaveCount(0);
  });

  test('клик по связи переводит выбор на второй конец', async ({ page }) => {
    const api = await openModel(page);
    const iabs = api.element('IABS');
    await page.getByPlaceholder('Поиск по модели').fill('IABS');
    await page.locator(`[data-row="${iabs.id}"]`).click();
    await page.getByRole('tab', { name: 'Описание' }).click();
    const first = page.getByTestId('relation-row').first();
    const otherName = (await first.locator('.rel__n').textContent())!;
    await first.click();
    await expect(page.getByTestId('description').locator('h2')).toHaveText(otherName);
  });
});
