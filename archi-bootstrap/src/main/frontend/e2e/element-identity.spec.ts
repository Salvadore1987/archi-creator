import { expect, test } from '@playwright/test';
import { openModel, openView } from './support/mockApi';

test('UI-001: переименование на одном представлении видно на втором открытом', async ({ page }) => {
  const api = await openModel(page);
  const iabs = api.element('IABS');
  const first = await openView(page, api, '01 ');
  const second = await openView(page, api, '05 ');

  await page.locator(`[data-view-tab="${first}"]`).click();
  await page.locator(`.react-flow__node:has([data-element="${iabs.id}"])`).click();
  const name = page.getByTestId('prop-name');
  await name.fill('IABS — ядро учёта');
  await name.press('Enter');

  await page.locator(`[data-view-tab="${second}"]`).click();
  await expect(page.locator(`.react-flow__node:has([data-element="${iabs.id}"])`).first()).toContainText('IABS — ядро учёта');
  await expect(page.locator(`[data-row="${iabs.id}"]`)).toContainText('IABS — ядро учёта');
});

test('UI-001: снятие с представления убирает размещение, а не элемент', async ({ page }) => {
  const api = await openModel(page);
  const physical = api.element('Физическое лицо');
  await openView(page, api, '01 ');
  await page.locator(`.react-flow__node:has([data-element="${physical.id}"])`).click();
  await page.getByTestId('canvas').press('Delete');
  await expect(page.locator(`.react-flow__node:has([data-element="${physical.id}"])`)).toHaveCount(0);
  await expect(page.locator(`[data-row="${physical.id}"]`)).toBeVisible();
});
