import { expect, test } from '@playwright/test';
import { canvasOf, drag, focusChrome, nodeState, rootNodes } from './support/canvas';
import { editorState, openModel, openView } from './support/mockApi';

/**
 * Паритет с Archi по базовому редактированию — по сценарию на пункт. Горячие
 * клавиши — как в Archi.
 */
test.describe('UI-006: паритет с Archi', () => {
  test('undo/redo: операции холста и дерева — в одном стеке', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '03 ');
    const [node] = rootNodes(api, viewId);
    const { box } = canvasOf(page, viewId);

    const at = await box(node!.id);
    await drag(page, { x: at.x + at.width / 2, y: at.y + at.height / 2 }, { x: at.x + at.width / 2 + 100, y: at.y + at.height / 2 + 60 });
    const moved = await nodeState<number>(page, viewId, node!.id, 'x');
    expect(moved).not.toBe(node!.x);

    await page.locator(`[data-row="${node!.elementId}"]`).first().click();
    await page.getByTestId('prop-name').fill('Переименован на холсте');
    await page.getByTestId('prop-name').press('Enter');
    await focusChrome(page);

    await page.keyboard.press('ControlOrMeta+z');
    await expect(canvasOf(page, viewId).node(node!.id)).not.toContainText('Переименован на холсте');
    expect(await nodeState<number>(page, viewId, node!.id, 'x')).toBe(moved);
    await page.keyboard.press('ControlOrMeta+z');
    expect(await nodeState<number>(page, viewId, node!.id, 'x')).toBe(node!.x);
    await page.keyboard.press('ControlOrMeta+Shift+z');
    expect(await nodeState<number>(page, viewId, node!.id, 'x')).toBe(moved);
    await page.keyboard.press('Control+y');
    await expect(canvasOf(page, viewId).node(node!.id)).toContainText('Переименован на холсте');
  });

  test('множественный выбор и рамка: Shift+рамка выделяет, перетаскивание двигает всех одной операцией', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '03 ');
    const nodes = rootNodes(api, viewId);
    const { box, canvas } = canvasOf(page, viewId);
    const boxes = await Promise.all(nodes.map((n) => box(n.id)));
    const left = Math.min(...boxes.map((b) => b.x)) - 10;
    const top = Math.min(...boxes.map((b) => b.y)) - 10;
    const right = Math.max(...boxes.map((b) => b.x + b.width)) + 10;
    const bottom = Math.max(...boxes.map((b) => b.y + b.height)) + 10;

    await page.keyboard.down('Shift');
    await drag(page, { x: left, y: top }, { x: right, y: bottom });
    await page.keyboard.up('Shift');
    await expect.poll(() => editorState<number>(page, 's.selection.ids.length')).toBe(nodes.length);
    await expect(canvas.locator('.react-flow__node.selected')).toHaveCount(nodes.length);

    const first = boxes[0]!;
    await drag(page, { x: first.x + first.width / 2, y: first.y + first.height / 2 }, { x: first.x + first.width / 2 + 40, y: first.y + first.height / 2 + 40 });
    await expect(page.getByTestId('undo')).toHaveAttribute('title', new RegExp(`Перемещено на холсте: ${nodes.length}`));

    await page.keyboard.down('ControlOrMeta');
    await canvasOf(page, viewId).node(nodes[1]!.id).click();
    await page.keyboard.up('ControlOrMeta');
    expect(await editorState<number>(page, 's.selection.ids.length')).toBe(nodes.length - 1);
  });

  test('zoom/pan: Ctrl+= крупнее, Ctrl+- мельче, Ctrl+0 — 100 %, перетаскивание фона двигает холст', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '03 ');
    await focusChrome(page);
    const zoom = page.getByTestId('zoom-value');
    await page.keyboard.press('ControlOrMeta+0');
    await expect(zoom).toHaveText('100%');
    const value = async () => parseInt((await zoom.textContent())!, 10);
    await page.keyboard.press('ControlOrMeta+=');
    await expect.poll(value).toBeGreaterThan(100);
    const larger = await value();
    await page.keyboard.press('ControlOrMeta+-');
    await expect.poll(value).toBeLessThan(larger);
    await page.keyboard.press('ControlOrMeta+-');
    await expect.poll(value).toBeLessThan(100);

    const { canvas } = canvasOf(page, viewId);
    const pane = (await canvas.boundingBox())!;
    const before = await editorState<{ x: number }>(page, `s.viewports['${viewId}']`);
    await drag(page, { x: pane.x + pane.width - 30, y: pane.y + pane.height - 80 }, { x: pane.x + pane.width - 230, y: pane.y + pane.height - 120 });
    await expect.poll(async () => (await editorState<{ x: number }>(page, `s.viewports['${viewId}']`)).x).not.toBe(before.x);
  });

  test('сетка и привязка: узел встаёт на шаг сетки; выключенная сетка не видна и не привязывает', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '03 ');
    const [node] = rootNodes(api, viewId);
    const { box, canvas } = canvasOf(page, viewId);
    await expect(canvas.locator('.react-flow__background')).toHaveCount(1);

    let at = await box(node!.id);
    await drag(page, { x: at.x + 30, y: at.y + 20 }, { x: at.x + 30 + 37, y: at.y + 20 + 23 });
    const x = await nodeState<number>(page, viewId, node!.id, 'x');
    const y = await nodeState<number>(page, viewId, node!.id, 'y');
    expect(x % 20).toBe(0);
    expect(y % 20).toBe(0);

    await page.getByTitle('Сетка и привязка').click();
    await expect(canvas.locator('.react-flow__background')).toHaveCount(0);
    at = await box(node!.id);
    await page.keyboard.press('ControlOrMeta+0');
    at = await box(node!.id);
    await drag(page, { x: at.x + 30, y: at.y + 20 }, { x: at.x + 30 + 37, y: at.y + 20 + 23 });
    const free = await nodeState<number>(page, viewId, node!.id, 'x');
    expect(free % 20).not.toBe(0);
  });
});
