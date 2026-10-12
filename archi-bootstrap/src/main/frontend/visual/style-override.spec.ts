import { expect, test } from '@playwright/test';
import { readDesignTokens } from '../build/design-tokens';
import { MockApi } from '../e2e/support/mockApi';

test.describe('UI-011: собственный стиль объекта приоритетнее токена слоя', () => {
  test('fillColor из файла вместо токена слоя', async ({ page }) => {
    await new MockApi().install(page);
    await page.goto('/?gallery');
    await expect(page.locator('[data-type="own-style"] .shape__outline')).toHaveCSS('fill', 'rgb(192, 192, 192)');
    await expect(page.locator('[data-type="own-style"] .shape__name')).toHaveCSS('font-weight', '700');
  });

  test('выделение — вторая обводка акцентом, заливка не меняется', async ({ page }) => {
    const accent = readDesignTokens().values.accent!;
    await new MockApi().install(page);
    await page.goto('/?gallery');
    const selected = page.locator('[data-type="own-style-selected"]');
    await expect(selected.locator('.shape__outline')).toHaveCSS('fill', 'rgb(192, 192, 192)');
    await expect(selected.locator('.shape__selection')).toHaveCount(1);
    const n = parseInt(accent.slice(1), 16);
    await expect(selected.locator('.shape__selection')).toHaveCSS('stroke', `rgb(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255})`);
  });

  test('на холсте эталона узел с собственной заливкой сохраняет её', async ({ page }) => {
    const api = new MockApi();
    const styled = Object.values(api.views).flatMap((v) => v.nodes.map((n) => ({ view: v, node: n }))).find((x) => (x.node.style as { fillColor?: string } | undefined)?.fillColor);
    test.skip(!styled, 'в эталоне нет узла с собственной заливкой');
    await api.install(page);
    await page.goto(`/?model=${api.modelId}`);
    await page.locator(`[data-row="${styled!.view.id}"]`).dblclick();
    const fill = (styled!.node.style as { fillColor: string }).fillColor;
    const n = parseInt(fill.slice(1), 16);
    await expect(page.locator(`[data-id="${styled!.node.id}"] .shape__outline`)).toHaveCSS('fill', `rgb(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255})`);
  });
});
