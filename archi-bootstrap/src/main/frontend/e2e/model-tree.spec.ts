import { expect, test } from '@playwright/test';
import { editorState, openModel, openView } from './support/mockApi';

test.describe('UI-012: дерево модели повторяет папки файла', () => {
  test('девять корней со счётчиками по ветвям', async ({ page }) => {
    await openModel(page);
    const roots = page.locator('.tr--root');
    await expect(roots).toHaveCount(9);
    await expect(page.locator('.tr--root', { hasText: 'Application' }).locator('.tr__cnt')).toHaveText('242');
  });

  test('живой поиск подсвечивает совпадения и раскрывает ветви', async ({ page }) => {
    await openModel(page);
    await page.getByPlaceholder('Поиск по модели').fill('IABS');
    await expect(page.locator('.tr mark').first()).toHaveText(/iabs/i);
    await expect(page.locator('[data-kind="element"]', { hasText: /^IABS/ }).first()).toBeVisible();
    await page.getByPlaceholder('Поиск по модели').press('Escape');
    await expect(page.locator('.tr mark')).toHaveCount(0);
  });

  test('⌘F/Ctrl+F ведёт в поиск', async ({ page }) => {
    await openModel(page);
    await page.locator('body').press('ControlOrMeta+f');
    await expect(page.getByPlaceholder('Поиск по модели')).toBeFocused();
  });

  test('фильтр «Не размещены» — 45 элементов, комбинируется с поиском', async ({ page }) => {
    await openModel(page);
    await page.getByRole('button', { name: /Не размещены/ }).click();
    await expect(page.locator('.tfoot__shown')).toHaveText('показано 45');
    await page.getByPlaceholder('Поиск по модели').fill('ЦОД');
    await expect(page.locator('.tfoot__shown')).not.toHaveText('показано 45');
  });

  test('маркеры: ×N представлений у IABS', async ({ page }) => {
    await openModel(page);
    await page.getByPlaceholder('Поиск по модели').fill('IABS');
    const iabs = page.locator('[data-kind="element"]').filter({ has: page.locator('.tr__n', { hasText: /^IABS$/ }) });
    await expect(iabs.locator('.tr__v')).toHaveText('×4');
  });

  test('режим «Типы» — группы по численности', async ({ page }) => {
    await openModel(page);
    await page.getByRole('button', { name: 'Типы', exact: true }).click();
    await expect(page.locator('.tr--folder').first()).toContainText('Компонент приложения');
    await expect(page.locator('.tr--folder').first().locator('.tr__cnt')).toHaveText('242');
  });

  test('выделение синхронно с холстом в обе стороны', async ({ page }) => {
    const api = await openModel(page);
    await openView(page, api, '01 ');
    const iabs = api.element('IABS');
    await page.getByPlaceholder('Поиск по модели').fill('IABS');
    await page.locator(`[data-row="${iabs.id}"]`).click();
    await expect(page.locator(`.react-flow__node:has([data-element="${iabs.id}"])`)).toHaveClass(/selected/);

    const physical = api.element('Физическое лицо');
    await page.getByPlaceholder('Поиск по модели').fill('');
    await page.locator(`.react-flow__node:has([data-element="${physical.id}"])`).click();
    await expect(page.locator(`[data-row="${physical.id}"]`)).toHaveClass(/is-sel/);
    expect(await editorState<string[]>(page, 's.selection.ids')).toEqual([physical.id]);
  });
});
