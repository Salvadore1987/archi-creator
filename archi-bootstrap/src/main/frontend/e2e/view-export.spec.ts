import { expect, test } from '@playwright/test';
import { openModel, openView } from './support/mockApi';

test.describe('FR-41, FR-43: экспорт из меню «Экспорт»', () => {
  test('читатель выгружает открытое представление в SVG и PNG, имя файла — от сервера', async ({ page }) => {
    const api = await openModel(page, { roles: ['VIEWER'] });
    const viewId = await openView(page, api, '01');

    await page.getByTestId('export-menu').click();
    const svgRequest = page.waitForRequest((r) => r.url().includes(`/api/v1/views/${viewId}/export?fmt=svg`));
    const svgDownload = page.waitForEvent('download');
    await page.getByRole('menuitem', { name: 'Представление — SVG' }).click();
    await svgRequest;
    expect((await svgDownload).suggestedFilename()).toBe('view-v1.svg');

    await page.getByTestId('export-menu').click();
    const pngRequest = page.waitForRequest((r) => r.url().includes(`/api/v1/views/${viewId}/export?fmt=png&scale=2`));
    await page.getByRole('menuitem', { name: 'Представление — PNG ×2' }).click();
    await pngRequest;
  });

  test('без открытого представления — только модель и пакет всех представлений', async ({ page }) => {
    const api = await openModel(page, { roles: ['VIEWER'] });
    await page.getByTestId('export-menu').click();
    await expect(page.getByRole('menuitem', { name: /Представление/ })).toHaveCount(0);

    const zip = page.waitForEvent('download');
    const request = page.waitForRequest((r) => r.url().includes(`/api/v1/models/${api.modelId}/views/export?fmt=png`));
    await page.getByRole('menuitem', { name: 'Все представления — PNG, архив' }).click();
    await request;
    expect((await zip).suggestedFilename()).toBe('views-v1.zip');
  });

  test('FR-43, FR-44: модель выгружается в Open Exchange архивом, число потерь — в сообщении', async ({ page }) => {
    const api = await openModel(page, { roles: ['VIEWER'] });
    await page.getByTestId('export-menu').click();
    const zip = page.waitForEvent('download');
    const request = page.waitForRequest((r) => r.url().includes(`/api/v1/models/${api.modelId}/export?fmt=oef`));
    await page.getByRole('menuitem', { name: 'Модель — Open Exchange (OEF), архив' }).click();
    await request;
    expect((await zip).suggestedFilename()).toBe('model-v1.oef.zip');
    await expect(page.getByText('Не перенеслось в OEF: 9')).toBeVisible();
  });

  test('при несохранённых правках меню предупреждает, что выгружается сохранённая версия', async ({ page }) => {
    const api = await openModel(page);
    const iabs = api.element('IABS');
    await page.getByPlaceholder('Поиск по модели').fill('IABS');
    await page.locator(`[data-row="${iabs.id}"]`).click();
    await page.getByRole('tab', { name: 'Свойства' }).click();
    await page.getByTestId('prop-name').fill('IABS 2');
    await page.getByTestId('prop-name').press('Enter');

    await page.getByTestId('export-menu').click();
    await expect(page.getByRole('menu')).toContainText('Несохранённые правки в файл не попадут');
  });
});
