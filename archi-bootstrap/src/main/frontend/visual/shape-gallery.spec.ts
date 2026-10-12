import { expect, test } from '@playwright/test';
import { MockApi } from '../e2e/support/mockApi';

/**
 * Снимок галереи силуэтов. Эталон — утверждённая отрисовка в репозитории:
 * изменение силуэта, иконки или слоя видно как разница пикселей и требует
 * осознанного обновления эталона (`npx playwright test --update-snapshots`).
 */
test('UI-009: силуэты и угловые иконки всех типов фазы 1', async ({ page }) => {
  await new MockApi().install(page);
  await page.goto('/?gallery');
  await page.getByTestId('gallery').waitFor();
  await expect(page.getByTestId('gallery')).toHaveScreenshot('shape-gallery.png');
});
