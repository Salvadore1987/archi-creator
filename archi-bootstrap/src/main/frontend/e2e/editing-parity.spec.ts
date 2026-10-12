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
    // Под нагрузкой отпускание обрабатывается не сразу — ждём правку, а не читаем мгновенно.
    await expect.poll(() => nodeState<number>(page, viewId, node!.id, 'x')).not.toBe(node!.x);
    const moved = await nodeState<number>(page, viewId, node!.id, 'x');

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
    const { box, canvas } = canvasOf(page, viewId);
    // Узел ближе всех к середине холста: у края перетаскивание прокручивает холст само.
    const pane = (await canvas.boundingBox())!;
    const middle = { x: pane.x + pane.width / 2, y: pane.y + pane.height / 2 };
    const distances = await Promise.all(
      rootNodes(api, viewId).map(async (n) => {
        const b = await box(n.id);
        return { n, d: Math.hypot(b.x + b.width / 2 - middle.x, b.y + b.height / 2 - middle.y) };
      }),
    );
    const node = distances.sort((a, b) => a.d - b.d)[0]!.n;
    await expect(canvas.locator('.react-flow__background')).toHaveCount(1);

    let at = await box(node.id);
    await drag(page, { x: at.x + 30, y: at.y + 20 }, { x: at.x + 30 + 37, y: at.y + 20 + 23 });
    const x = await nodeState<number>(page, viewId, node.id, 'x');
    const y = await nodeState<number>(page, viewId, node.id, 'y');
    expect(x % 20).toBe(0);
    expect(y % 20).toBe(0);

    await page.getByTitle('Сетка и привязка').click();
    await expect(canvas.locator('.react-flow__background')).toHaveCount(0);
    at = await box(node.id);
    await drag(page, { x: at.x + 30, y: at.y + 20 }, { x: at.x + 30 + 37, y: at.y + 20 + 23 });
    await expect.poll(() => nodeState<number>(page, viewId, node.id, 'x')).not.toBe(x);
    const free = await nodeState<number>(page, viewId, node.id, 'x');
    expect(free % 20).not.toBe(0);
  });

  test('группы, заметки, соединители: с палитры «Прочее», заметка внутри группы, подпись и текст правятся', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '03 ');
    const { canvas } = canvasOf(page, viewId);
    await focusChrome(page);
    await page.keyboard.press('ControlOrMeta+-');
    await page.keyboard.press('ControlOrMeta+-');
    const pane = (await canvas.boundingBox())!;
    await page.getByTestId('palette').getByRole('button', { name: 'Прочее' }).click();

    await page.locator('.pal[data-archi-type="archimate:Group"]').dragTo(canvas, {
      targetPosition: { x: pane.width - 260, y: pane.height - 170 },
    });
    const groupId = await editorState<string>(
      page,
      `Object.values(s.doc.loadedViews['${viewId}'].nodes).find((n) => n.kind === 'GROUP' && n.label === 'Группа').id`,
    );
    await page.getByTestId('prop-label').fill('Контур подписи');
    await page.getByTestId('prop-label').press('Enter');
    await expect(canvasOf(page, viewId).node(groupId)).toContainText('Контур подписи');

    const group = (await canvasOf(page, viewId).box(groupId));
    await page.locator('.pal[data-archi-type="archimate:Note"]').dragTo(canvas, {
      targetPosition: { x: group.x - pane.x + 60, y: group.y - pane.y + 50 },
    });
    const note = await editorState<{ id: string; parentId?: string }>(
      page,
      `Object.values(s.doc.loadedViews['${viewId}'].nodes).find((n) => n.kind === 'NOTE')`,
    );
    expect(note.parentId).toBe(groupId);
    await page.getByTestId('prop-content').fill('Строка 1\nСтрока 2');
    await page.getByTestId('prop-content').blur();
    await expect(canvasOf(page, viewId).node(note.id)).toContainText('Строка 1');

    await page.locator('.pal[data-archi-type="archimate:Junction"]').dragTo(canvas, {
      targetPosition: { x: pane.width - 60, y: 40 },
    });
    const junction = await editorState<{ archiType: string; name: string; folderId: string }>(
      page,
      `Object.values(s.doc.elements).find((e) => e.archiType === 'archimate:Junction')`,
    );
    expect(junction.name).toBe('');
    const junctionNode = await editorState<{ width: number; height: number }>(
      page,
      `Object.values(s.doc.loadedViews['${viewId}'].nodes).find((n) => s.doc.elements[n.elementId]?.archiType === 'archimate:Junction')`,
    );
    expect([junctionNode.width, junctionNode.height]).toEqual([15, 15]);

    await page.getByTestId('save').click();
    await expect(page.getByTestId('save')).toBeHidden();
    const placed = api.writes().filter((c) => c.method === 'POST' && c.path === `/views/${viewId}/nodes`).map((c) => c.body as Record<string, unknown>);
    expect(placed.find((b) => b.kind === 'GROUP')).toMatchObject({ label: 'Группа' });
    expect(placed.find((b) => b.kind === 'NOTE')).toMatchObject({ parentId: groupId });
    expect(api.writes().find((c) => c.method === 'PATCH' && c.path === `/view-nodes/${groupId}`)?.body).toEqual({ label: 'Контур подписи' });
    expect(api.writes().find((c) => c.method === 'PATCH' && c.path === `/view-nodes/${note.id}`)?.body).toEqual({ content: 'Строка 1\nСтрока 2' });
  });

  test('переопределение стиля: заливка и шрифт узла — сильнее токена слоя, уходят в PATCH, сбрасываются', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '03 ');
    const [node] = rootNodes(api, viewId);
    const { node: nodeOf } = canvasOf(page, viewId);
    await nodeOf(node!.id).click();

    await page.getByTestId('style-fillColor').fill('#ff8800');
    await expect(nodeOf(node!.id).locator('.shape__outline')).toHaveAttribute('fill', '#ff8800');
    await page.getByTestId('style-bold').click();
    await page.getByTestId('style-font-size').selectOption('12');
    await expect(page.getByTestId('undo')).toHaveAttribute('title', /Оформление/);
    expect(await nodeState<{ fillColor: string; font: string }>(page, viewId, node!.id, 'style')).toEqual({
      fillColor: '#ff8800',
      font: '1|Segoe UI|12.0|1|',
    });

    await page.getByTestId('save').click();
    await expect(page.getByTestId('save')).toBeHidden();
    const patches = api.writes().filter((c) => c.method === 'PATCH' && c.path === `/view-nodes/${node!.id}`);
    expect(patches.at(-1)!.body).toEqual({ style: { fillColor: '#ff8800', font: '1|Segoe UI|12.0|1|' } });

    await page.getByTestId('style-reset-all').click();
    await expect(nodeOf(node!.id).locator('.shape__outline')).not.toHaveAttribute('fill', '#ff8800');
    expect(await nodeState<unknown>(page, viewId, node!.id, 'style')).toBeUndefined();
    await page.getByTestId('save').click();
    await expect(page.getByTestId('save')).toBeHidden();
    expect(api.writes().filter((c) => c.path === `/view-nodes/${node!.id}`).at(-1)!.body).toEqual({ style: {} });
  });

  test('копирование и вставка: в том же представлении — копия элемента, в другом — тот же элемент со связью', async ({ page }) => {
    const api = await openModel(page);
    const contextId = await openView(page, api, '12 ');
    const view = api.views[contextId]!;
    const edge = view.edges.find((e) => {
      const s = view.nodes.find((n) => n.id === e.sourceId);
      const t = view.nodes.find((n) => n.id === e.targetId);
      return s && t && !s.parentId && !t.parentId;
    })!;
    const ends = [edge.sourceId, edge.targetId] as string[];
    const { node } = canvasOf(page, contextId);
    await node(ends[0]!).click();
    await page.keyboard.down('ControlOrMeta');
    await node(ends[1]!).click();
    await page.keyboard.up('ControlOrMeta');
    await page.keyboard.press('ControlOrMeta+c');
    await expect(page.getByText('Скопировано объектов: 2')).toBeVisible();

    const elementsBefore = await editorState<number>(page, 'Object.keys(s.doc.elements).length');
    await page.keyboard.press('ControlOrMeta+v');
    const copies = await editorState<string[]>(page, "Object.values(s.doc.elements).filter((e) => e.name.endsWith('(копия)')).map((e) => e.name)");
    expect(copies).toHaveLength(2);
    expect(await editorState<number>(page, 'Object.keys(s.doc.elements).length')).toBe(elementsBefore + 2);
    const copiedRelations = await editorState<number>(
      page,
      `Object.values(s.doc.relationships).filter((r) => s.doc.elements[r.sourceId]?.name.endsWith('(копия)')).length`,
    );
    expect(copiedRelations).toBe(1);
    await page.keyboard.press('ControlOrMeta+z');
    expect(await editorState<number>(page, 'Object.keys(s.doc.elements).length')).toBe(elementsBefore);

    const otherId = await openView(page, api, '03 ');
    const pane = (await canvasOf(page, otherId).canvas.boundingBox())!;
    await page.mouse.click(pane.x + pane.width - 20, pane.y + 20);
    const nodesBefore = await editorState<number>(page, `Object.keys(s.doc.loadedViews['${otherId}'].nodes).length`);
    await page.keyboard.press('ControlOrMeta+v');
    expect(await editorState<number>(page, `Object.keys(s.doc.loadedViews['${otherId}'].nodes).length`)).toBe(nodesBefore + 2);
    expect(await editorState<number>(page, 'Object.keys(s.doc.elements).length')).toBe(elementsBefore);
    const pastedEdge = await editorState<string[]>(
      page,
      `Object.values(s.doc.loadedViews['${otherId}'].edges).filter((e) => e.relationshipId === '${edge.relationshipId}').map((e) => e.id)`,
    );
    expect(pastedEdge).toHaveLength(1);

    await page.getByTestId('save').click();
    await expect(page.getByTestId('save')).toBeHidden();
    const writes = api.writes();
    expect(writes.some((c) => c.method === 'POST' && c.path.endsWith('/elements'))).toBe(false);
    expect(writes.filter((c) => c.method === 'POST' && c.path === `/views/${otherId}/nodes`)).toHaveLength(2);
    expect(writes.find((c) => c.method === 'POST' && c.path === `/views/${otherId}/edges`)?.body).toMatchObject({
      relationshipId: edge.relationshipId,
    });
  });

  test('выравнивание и направляющие: по опорному узлу одной операцией; у края другого узла — линия и доводка', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '03 ');
    const [a, b, c] = rootNodes(api, viewId);
    const { node, box } = canvasOf(page, viewId);
    await node(a!.id).click();
    await page.keyboard.down('ControlOrMeta');
    await node(b!.id).click();
    await node(c!.id).click();
    await page.keyboard.up('ControlOrMeta');
    await expect(page.getByTestId('align-tools')).toBeVisible();
    await page.locator('[data-align="left"]').click();
    await expect(page.getByTestId('undo')).toHaveAttribute('title', /Выровнять по левому краю/);
    const anchorX = await nodeState<number>(page, viewId, c!.id, 'x');
    expect(await nodeState<number>(page, viewId, a!.id, 'x')).toBe(anchorX);
    expect(await nodeState<number>(page, viewId, b!.id, 'x')).toBe(anchorX);
    expect(await editorState<number>(page, 's.history.ops.length')).toBe(1);

    await page.getByTitle('Сетка и привязка').click();
    await page.keyboard.press('ControlOrMeta+z');
    // Выделены все три — перетаскивание повезло бы всех; оставляем один.
    await page.keyboard.press('Escape');
    await page.keyboard.press('ControlOrMeta+-');
    await page.keyboard.press('ControlOrMeta+-');
    await expect.poll(async () => parseInt((await page.getByTestId('zoom-value').textContent())!, 10)).toBeLessThan(60);
    await page.waitForTimeout(200);
    // Свободное место — под самым нижним узлом; левый край a — в 3 экранных пикселях от края b.
    const lowest = await editorState<number>(
      page,
      `Math.max(...Object.values(s.doc.loadedViews['${viewId}'].nodes).filter((n) => !n.parentId).map((n) => n.y + n.height))`,
    );
    const target = await box(b!.id);
    await node(a!.id).click();
    const moving = await box(a!.id);
    const bFlow = { x: await nodeState<number>(page, viewId, b!.id, 'x'), y: await nodeState<number>(page, viewId, b!.id, 'y') };
    const scale = target.width / (await nodeState<number>(page, viewId, b!.id, 'width'));
    const dropY = target.y + (lowest + 60 - bFlow.y) * scale;
    await page.mouse.move(moving.x + 5, moving.y + 5);
    await page.mouse.down();
    await page.mouse.move(target.x + 3 + 5, dropY + 5, { steps: 10 });
    await expect(page.getByTestId('guide-lines')).toBeVisible();
    await page.mouse.up();
    await expect(page.getByTestId('guide-lines')).toHaveCount(0);
    expect(await nodeState<string | undefined>(page, viewId, a!.id, 'parentId')).toBeUndefined();
    // По отпусканию край или середина узла совпадает с краем или серединой другого точно.
    const aligned = await editorState<boolean>(
      page,
      `(() => {
        const nodes = Object.values(s.doc.loadedViews['${viewId}'].nodes).filter((n) => !n.parentId);
        const a = nodes.find((n) => n.id === '${a!.id}');
        const xs = (n) => [n.x, n.x + n.width / 2, n.x + n.width];
        const ys = (n) => [n.y, n.y + n.height / 2, n.y + n.height];
        const others = nodes.filter((n) => n !== a);
        return others.some((o) => xs(a).some((v) => xs(o).includes(v)) || ys(a).some((v) => ys(o).includes(v)));
      })()`,
    );
    expect(aligned).toBe(true);
  });

  test('точки перегиба: тянем середину — точка добавлена в формате Archi, тянем точку — сдвинута, двойной щелчок — убрана', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '03 ');
    const view = api.views[viewId]!;
    const edge = view.edges.find((e) => (e.bendpoints as unknown[] | undefined ?? []).length === 0)!;
    await page.evaluate((id) => {
      (window as unknown as { __archiEditor: { getState(): { select(ids: string[], o: string): void } } }).__archiEditor
        .getState()
        .select([id], 'canvas');
    }, edge.relationshipId as string);
    const insert = page.locator('[data-bend-insert="0"]');
    await expect(insert).toBeVisible();
    const from = (await insert.boundingBox())!;
    await drag(page, { x: from.x + 3, y: from.y + 3 }, { x: from.x + 3 + 30, y: from.y + 3 + 60 });
    const bends = () => editorState<Array<{ startX: number; endX: number }>>(page, `s.doc.loadedViews['${viewId}'].edges['${edge.id}'].bendpoints`);
    await expect.poll(async () => (await bends()).length).toBe(1);
    await expect(page.getByTestId('undo')).toHaveAttribute('title', /Точки перегиба связи/);

    const handle = page.locator('[data-bendpoint="0"]');
    const before = (await bends())[0]!;
    const at = (await handle.boundingBox())!;
    await drag(page, { x: at.x + 4, y: at.y + 4 }, { x: at.x + 4 + 40, y: at.y + 4 });
    await expect.poll(async () => (await bends())[0]!.startX).toBeGreaterThan(before.startX);

    await page.getByTestId('save').click();
    await expect(page.getByTestId('save')).toBeHidden();
    const layout = api.writes().filter((c) => c.method === 'PUT' && c.path === `/views/${viewId}/layout`).map((c) => c.body as { edges: Array<{ id: string; bendpoints: unknown[] }> });
    expect(layout.at(-1)!.edges[0]).toMatchObject({ id: edge.id });
    expect(layout.at(-1)!.edges[0]!.bendpoints).toHaveLength(1);

    await handle.dblclick();
    await expect.poll(async () => (await bends()).length).toBe(0);
  });

  test('удаление ребра: Delete убирает ребро с представления, связь остаётся в модели; отмена возвращает', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '03 ');
    const edge = api.views[viewId]!.edges[0]!;
    await page.evaluate((id) => {
      (window as unknown as { __archiEditor: { getState(): { select(ids: string[], o: string): void } } }).__archiEditor
        .getState()
        .select([id], 'canvas');
    }, edge.relationshipId as string);
    const drawn = canvasOf(page, viewId).canvas.locator(`.react-flow__edge[data-id="${edge.id}"]`);
    await drawn.focus();
    await drawn.press('Delete');
    await expect.poll(() => editorState<unknown>(page, `s.doc.loadedViews['${viewId}'].edges['${edge.id}']`)).toBeUndefined();
    expect(await editorState<unknown>(page, `s.doc.relationships['${edge.relationshipId}'] !== undefined`)).toBe(true);

    await page.getByTestId('save').click();
    await expect(page.getByTestId('save')).toBeHidden();
    expect(api.writes().map((c) => `${c.method} ${c.path}`)).toContain(`DELETE /view-edges/${edge.id}`);
    expect(api.writes().some((c) => c.method === 'DELETE' && c.path.startsWith('/relationships/'))).toBe(false);

    await page.getByTestId('undo').click();
    await expect.poll(() => editorState<string>(page, `s.doc.loadedViews['${viewId}'].edges['${edge.id}']?.id`)).toBe(edge.id);
  });

  test('навигатор: мини-карта представления показывает все узлы, клик по ней двигает холст', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '02 ');
    const { canvas } = canvasOf(page, viewId);
    await expect(canvas.locator('.react-flow__minimap')).toHaveCount(0);
    await page.getByTestId('navigator-toggle').click();
    const map = canvas.locator('.react-flow__minimap');
    await expect(map).toBeVisible();
    await expect(map.locator('.react-flow__minimap-node')).toHaveCount(api.views[viewId]!.nodes.length);

    await page.keyboard.press('ControlOrMeta+0');
    const before = await editorState<{ x: number; y: number }>(page, `s.viewports['${viewId}']`);
    const box = (await map.boundingBox())!;
    await drag(page, { x: box.x + box.width / 2, y: box.y + box.height / 2 }, { x: box.x + box.width / 2 + 30, y: box.y + box.height / 2 + 20 });
    await expect
      .poll(async () => (await editorState<{ x: number }>(page, `s.viewports['${viewId}']`)).x)
      .not.toBe(before?.x);
  });

  test('поиск по модели и «где используется»: Ctrl+F, строка найдена, представления элемента открываются с выделением', async ({ page }) => {
    const api = await openModel(page);
    const iabs = api.element('IABS');
    await page.keyboard.press('ControlOrMeta+f');
    await expect(page.getByPlaceholder('Поиск по модели')).toBeFocused();
    await page.keyboard.type('IABS');
    const row = page.locator(`[data-row="${iabs.id}"]`).first();
    await expect(row).toBeVisible();
    await row.click({ button: 'right' });
    await page.getByRole('menuitem', { name: 'Где используется' }).click();
    const card = page.getByTestId('description');
    const views = card.getByRole('button', { name: /^0[0-9] / });
    const second = views.nth(1);
    await expect(second).toBeVisible();
    const name = (await second.textContent())!.trim();
    await second.click();
    const viewId = api.viewByName(name.slice(0, 3)).id;
    await expect(page.locator(`[data-view-tab="${viewId}"]`)).toHaveAttribute('aria-selected', 'true');
    await expect(canvasOf(page, viewId).canvas.locator(`.react-flow__node.selected:has([data-element="${iabs.id}"])`)).toHaveCount(1);
  });
});
