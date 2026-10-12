import { expect, test } from '@playwright/test';
import { editorState, openModel, openView } from './support/mockApi';

test.describe('UI-016: правка дерева на месте', () => {
  test('F2 переименовывает в строке, Esc отменяет правку', async ({ page }) => {
    const api = await openModel(page);
    const tieto = api.element('TIETO');
    await page.getByPlaceholder('Поиск по модели').fill('TIETO');
    await page.locator(`[data-row="${tieto.id}"]`).click();
    await page.getByRole('tree').press('F2');
    await page.locator('.tr__edit').fill('TIETO — процессинг');
    await page.locator('.tr__edit').press('Enter');
    await expect(page.locator(`[data-row="${tieto.id}"]`)).toContainText('TIETO — процессинг');

    await page.getByRole('tree').press('F2');
    await page.locator('.tr__edit').fill('другое');
    await page.locator('.tr__edit').press('Escape');
    await expect(page.locator(`[data-row="${tieto.id}"]`)).toContainText('TIETO — процессинг');
  });

  test('двойной клик по имени — переименование', async ({ page }) => {
    const api = await openModel(page);
    const tieto = api.element('TIETO');
    await page.getByPlaceholder('Поиск по модели').fill('TIETO');
    await page.locator(`[data-row="${tieto.id}"] .tr__n`).dblclick();
    await expect(page.locator('.tr__edit')).toBeFocused();
  });

  test('новая папка — сразу в режиме переименования; перенос перетаскиванием', async ({ page }) => {
    const api = await openModel(page);
    const tieto = api.element('TIETO');
    await page.getByPlaceholder('Поиск по модели').fill('TIETO');
    await page.locator(`[data-row="${tieto.id}"]`).click();
    await page.getByTitle('Создать папку').click();
    await expect(page.locator('.tr__edit')).toBeFocused();
    await page.locator('.tr__edit').fill('Процессинг');
    await page.locator('.tr__edit').press('Enter');

    const folderId = await editorState<string>(page, "Object.values(s.doc.folders).find(f => f.name === 'Процессинг').id");
    await page.locator(`[data-row="${tieto.id}"]`).dragTo(page.locator(`[data-row="${folderId}"]`));
    expect(await editorState<string>(page, `s.doc.elements['${tieto.id}'].folderId`)).toBe(folderId);

    await page.getByTestId('save').click();
    await expect(page.getByTestId('save')).toBeHidden();
    const writes = api.writes().map((c) => `${c.method} ${c.path}`);
    expect(writes).toEqual(
      expect.arrayContaining([`POST /models/${api.modelId}/folders`, `POST /models/${api.modelId}/tree/move`]),
    );
  });

  test('«Переместить в папку…» ищет по полному пути и не предлагает чужие корни', async ({ page }) => {
    const api = await openModel(page);
    const tieto = api.element('TIETO');
    await page.getByPlaceholder('Поиск по модели').fill('TIETO');
    await page.locator(`[data-row="${tieto.id}"]`).click({ button: 'right' });
    await page.getByRole('menuitem', { name: 'Переместить в папку…' }).click();
    await page.getByPlaceholder('Путь к папке').fill('Интеграционный');
    const options = page.locator('.pop__fi');
    await expect(options.first()).toHaveText('Application / Интеграционный слой');
    await expect(options.filter({ hasText: /^(Business|Technology|Strategy)/ })).toHaveCount(0);
    await options.first().click();
    await expect(page.locator(`[data-row="${tieto.id}"]`)).toBeVisible();
    const folder = await editorState<string>(page, `s.doc.folders[s.doc.elements['${tieto.id}'].folderId].name`);
    expect(folder).toBe('Интеграционный слой');
  });

  test('удаление показывает число связей до подтверждения', async ({ page }) => {
    const api = await openModel(page);
    const iabs = api.element('IABS');
    await page.getByPlaceholder('Поиск по модели').fill('IABS');
    await page.locator(`[data-row="${iabs.id}"]`).click();
    await page.getByRole('tree').press('Delete');
    const dialog = page.getByTestId('delete-dialog');
    await expect(dialog).toContainText('Вместе с ними удалятся связи: 60');
    await dialog.getByRole('button', { name: 'Отмена' }).click();
    await expect(page.locator(`[data-row="${iabs.id}"]`)).toBeVisible();

    await page.getByRole('tree').press('Delete');
    await dialog.getByRole('button', { name: 'Удалить' }).click();
    await expect(page.locator(`[data-row="${iabs.id}"]`)).toHaveCount(0);
    await page.getByRole('status').getByRole('button', { name: 'Отменить' }).click();
    await expect(page.locator(`[data-row="${iabs.id}"]`)).toBeVisible();
  });

  test('множественное выделение Ctrl/Cmd и Shift — сводка в правой панели', async ({ page }) => {
    await openModel(page);
    await page.getByRole('button', { name: 'Список', exact: true }).click();
    const rows = page.locator('[data-kind="element"]');
    await rows.nth(0).click();
    await rows.nth(3).click({ modifiers: ['Shift'] });
    await expect(page.getByTestId('bulk')).toContainText('Выбрано: 4');
    await rows.nth(1).click({ modifiers: ['ControlOrMeta'] });
    await expect(page.getByTestId('bulk')).toContainText('Выбрано: 3');
  });

  test('регрессия: клик в дереве не прокручивает строку, выделение извне — прокручивает', async ({ page }) => {
    const api = await openModel(page);
    await openView(page, api, '01 ');
    const tree = page.getByRole('tree');
    await page.getByRole('button', { name: 'Список', exact: true }).click();
    await tree.evaluate((el) => (el.scrollTop = 600));
    const before = await tree.evaluate((el) => el.scrollTop);
    const box = (await tree.boundingBox())!;
    // Строка у нижнего края, видна частично: прокрутка её «в зону видимости» сдвинула бы список.
    await page.mouse.click(box.x + 60, box.y + box.height - 6);
    await page.mouse.dblclick(box.x + 60, box.y + box.height - 6);
    expect(await tree.evaluate((el) => el.scrollTop)).toBe(before);

    const physical = api.element('Физическое лицо');
    await tree.evaluate((el) => (el.scrollTop = 0));
    await page.getByPlaceholder('Поиск по модели').press('Escape');
    await page.getByRole('button', { name: 'Папки', exact: true }).click();
    await page.getByRole('button', { name: 'Свернуть всё' }).click();
    await page.locator(`.react-flow__node:has([data-element="${physical.id}"])`).click();
    await expect(page.locator(`[data-row="${physical.id}"]`)).toBeInViewport();
  });
});
