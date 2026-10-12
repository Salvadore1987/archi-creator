import { expect, test } from '@playwright/test';
import { readDesignTokens } from '../build/design-tokens';
import { MockApi } from '../e2e/support/mockApi';

const hexToRgb = (hex: string) => {
  const n = parseInt(hex.slice(1), 16);
  return `rgb(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255})`;
};

test.describe('UI-010: токены — единственный источник стиля', () => {
  const tokens = readDesignTokens();

  test('переменные слоёв на странице совпадают с файлом спецификации', async ({ page }) => {
    await new MockApi().install(page);
    await page.goto('/?gallery');
    for (const [layer, colors] of Object.entries(tokens.layers)) {
      const value = await page.evaluate((name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim(), `--layer-${layer}`);
      expect(value.toLowerCase(), layer).toBe(colors.fill.toLowerCase());
    }
  });

  test('заливка и обводка фигуры — из токена её слоя', async ({ page }) => {
    await new MockApi().install(page);
    await page.goto('/?gallery');
    const outline = page.locator('[data-type="archimate:ApplicationComponent"] .shape__outline');
    await expect(outline).toHaveCSS('fill', hexToRgb(tokens.layers.application!.fill));
    await expect(outline).toHaveCSS('stroke', hexToRgb(tokens.layers.application!.border));
    const business = page.locator('[data-type="archimate:BusinessActor"] .shape__outline');
    await expect(business).toHaveCSS('fill', hexToRgb(tokens.layers.business!.fill));
  });
});
