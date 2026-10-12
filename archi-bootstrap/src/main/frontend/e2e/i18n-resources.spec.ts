import { expect, test } from '@playwright/test';
import { openModel, openView } from './support/mockApi';

test('UI-021: интерфейс на русском, archi_type в данных остаётся английским', async ({ page }) => {
  const api = await openModel(page);
  await openView(page, api, '01 ');
  const iabs = api.element('IABS');
  await page.getByPlaceholder('Поиск по модели').fill('IABS');
  await page.locator(`[data-row="${iabs.id}"]`).click();
  // Подписи кнопок и вкладок — русские.
  const buttons = await page.locator('.topbar button, .rtabs button, .tmodes button').allTextContents();
  for (const text of buttons.map((b) => b.trim()).filter(Boolean)) {
    expect(text, text).not.toMatch(/^(Save|Undo|Redo|Export|Search|Properties|Description|Folders|Types|List)$/);
  }
  await expect(page.getByTestId('properties')).toContainText('Компонент приложения');
  await expect(page.getByTestId('properties')).toContainText('archimate:ApplicationComponent');
});
