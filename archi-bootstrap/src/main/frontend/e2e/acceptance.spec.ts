import { readFileSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import { canvasOf, drag, focusChrome, rootNodes } from './support/canvas';
import { editorState, MockApi, openModel, openView, REFERENCE_FILE } from './support/mockApi';
import { relationTypes } from './support/relationMatrix';

/**
 * Приёмка этапа 4 — сценарии E2E из плана тестирования интерфейса: импорт и отрисовка,
 * недопустимая связь, цепочка отмены и повтора, выгрузка и обратный импорт.
 */
test.describe('приёмка этапа 4', () => {
  test('импорт файла → открытие представления → все объекты отрисованы', async ({ page }) => {
    const api = new MockApi();
    await api.install(page);
    await page.goto('/');
    await page.locator('input[type="file"]').setInputFiles(REFERENCE_FILE);
    await page.getByTestId('model-tree').waitFor();
    // Тело multipart с файлом Playwright не отдаёт — видно, что файл ушёл, а не его байты.
    expect(String(api.writes().find((c) => c.path === '/models/import')!.body)).toContain('Hamkorbank_AS_IS_strict.archimate');

    for (const prefix of ['02 ', '10 ', '11 ']) {
      const viewId = await openView(page, api, prefix);
      const view = api.views[viewId]!;
      const { canvas } = canvasOf(page, viewId);
      await expect(canvas.locator('.react-flow__node')).toHaveCount(view.nodes.length);
      await expect(canvas.locator('.react-flow__edge')).toHaveCount(view.edges.length);
    }
  });

  test('рисование недопустимой связи → тип не предлагается', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '04 ');
    const typeOf = (id: string) => api.tree.elements.find((e) => e.id === id)!.archiType as string;
    const roots = rootNodes(api, viewId);
    const source = roots.find((n) => typeOf(n.elementId) === 'archimate:SystemSoftware')!;
    const target = roots.find((n) => typeOf(n.elementId) === 'archimate:ApplicationComponent')!;
    const { box } = canvasOf(page, viewId);
    const from = await box(source.id);
    const to = await box(target.id);
    await drag(page, { x: from.x + from.width / 2, y: from.y + 1 }, { x: to.x + to.width / 2, y: to.y + to.height / 2 });

    await expect(page.getByTestId('relation-picker')).toBeVisible();
    const permitted = relationTypes('archimate:SystemSoftware', 'archimate:ApplicationComponent').map((r) => r.archiType);
    const offered = await page
      .getByTestId('relation-picker')
      .locator('[data-relation-type]')
      .evaluateAll((items) => items.map((item) => item.getAttribute('data-relation-type')!));
    expect(offered).toEqual(permitted);
    const forbidden = ['archimate:TriggeringRelationship', 'archimate:CompositionRelationship', 'archimate:FlowRelationship'].filter(
      (type) => !permitted.includes(type),
    );
    expect(forbidden.length).toBeGreaterThan(0);
    for (const type of forbidden) expect(offered).not.toContain(type);
  });

  test('undo/redo цепочки из пяти операций возвращает и повторяет всё', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '03 ');
    const snapshot = () =>
      editorState<string>(
        page,
        `JSON.stringify([s.doc.elements, s.doc.relationships, s.doc.loadedViews['${viewId}'].nodes, s.doc.loadedViews['${viewId}'].edges])`,
      );
    const original = await snapshot();
    const [a, b] = rootNodes(api, viewId);
    const { box, node, canvas } = canvasOf(page, viewId);

    // 1. Перемещение узла.
    const at = await box(a!.id);
    await drag(page, { x: at.x + at.width / 2, y: at.y + at.height / 2 }, { x: at.x + at.width / 2 + 60, y: at.y + at.height / 2 + 40 });
    // 2. Переименование элемента.
    await node(b!.id).click();
    await page.getByTestId('prop-name').fill('Пятиходовка');
    await page.getByTestId('prop-name').press('Enter');
    // 3. Собственная заливка.
    await page.getByTestId('style-fillColor').fill('#123456');
    // 4. Новый элемент с палитры.
    await focusChrome(page);
    await page.keyboard.press('ControlOrMeta+-');
    await page.keyboard.press('ControlOrMeta+-');
    const pane = (await canvas.boundingBox())!;
    await page.locator('.pal[data-archi-type="archimate:ApplicationComponent"]').dragTo(canvas, {
      targetPosition: { x: pane.width - 120, y: pane.height - 80 },
    });
    // 5. Авторазметка.
    await page.getByTestId('auto-layout').click();
    await page.locator('[data-strategy="compact"]').click();
    await expect.poll(() => editorState<number>(page, 's.history.ops.length')).toBe(5);
    const final = await snapshot();
    expect(final).not.toBe(original);

    await focusChrome(page);
    for (let i = 0; i < 5; i++) await page.keyboard.press('ControlOrMeta+z');
    expect(await snapshot()).toBe(original);
    await expect(page.getByTestId('undo')).toBeDisabled();
    for (let i = 0; i < 5; i++) await page.keyboard.press('ControlOrMeta+Shift+z');
    expect(await snapshot()).toBe(final);
  });

  test('экспорт → скачанный файл открывается импортом обратно', async ({ page }) => {
    const api = await openModel(page);
    const downloaded = page.waitForEvent('download');
    await page.getByTestId('export-menu').click();
    await page.getByRole('menuitem', { name: 'Модель .archimate' }).click();
    const download = await downloaded;
    expect(download.suggestedFilename()).toMatch(/\.archimate$/);
    const path = await download.path();
    const exported = readFileSync(path);
    expect(exported.equals(readFileSync(REFERENCE_FILE))).toBe(true);

    await page.getByTitle('Все модели').click();
    await page.locator('input[type="file"]').setInputFiles({
      name: download.suggestedFilename(),
      mimeType: 'application/xml',
      buffer: exported,
    });
    await page.getByTestId('model-tree').waitFor();
    const upload = api.writes().find((c) => c.path === '/models/import')!;
    expect(String(upload.body)).toContain(download.suggestedFilename());
    expect(await reimportedName(page)).toBe(api.tree.model.name);
  });
});

function reimportedName(page: Page): Promise<string> {
  return editorState<string>(page, 's.doc.model.name');
}
