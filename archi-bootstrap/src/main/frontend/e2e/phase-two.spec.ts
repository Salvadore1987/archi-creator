import { expect, test } from '@playwright/test';
import { editorState, openModel, openView } from './support/mockApi';

test.describe('FR-08: фаза 2 метамодели — Capability редактируется, а не только сохраняется', () => {
  test('способность на канве правится в «Свойствах», правка уходит при сохранении', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '11 ');
    const view = api.views[viewId]!;
    const leaf = view.nodes.find(
      (n) => n.elementId && !view.nodes.some((child) => child.parentId === n.id) &&
        api.tree.elements.some((e) => e.id === n.elementId && e.archiType === 'archimate:Capability'),
    )!;
    const capability = api.tree.elements.find((e) => e.id === leaf.elementId) as { id: string; name: string };
    const node = page.locator(`.react-flow__node:has([data-element="${capability.id}"])`).first();
    await expect(node.locator('.archi-node--opaque')).toHaveCount(0);

    // Через середину узла проходит связь — клик в стороне от линии.
    await node.click({ position: { x: 14, y: 10 } });
    await expect(page.getByTestId('properties')).toContainText('Способность');
    await expect(page.getByTestId('prop-name')).toBeEditable();
    await page.getByTestId('prop-name').fill('Мгновенные платежи');
    await page.getByTestId('prop-documentation').click();
    await expect(node).toContainText('Мгновенные платежи');

    await page.getByTestId('save').click();
    await expect(page.getByTestId('save')).toBeHidden();
    const patches = api.writes().filter((c) => c.method === 'PATCH' && c.path === `/elements/${capability.id}`);
    expect(patches.map((c) => c.body)).toEqual([{ name: 'Мгновенные платежи' }]);
  });

  test('палитра «Стратегия» и «Мотивация»: новая способность ставится на представление', async ({ page }) => {
    const api = await openModel(page);
    const viewId = await openView(page, api, '01 ');
    const palette = page.getByTestId('palette');
    await palette.getByRole('button', { name: 'Мотивация' }).click();
    await expect(palette.locator('.pal[data-archi-type="archimate:Goal"]')).toBeVisible();
    await palette.getByRole('button', { name: 'Стратегия' }).click();
    await expect(palette.locator('.pal[data-archi-type="archimate:ValueStream"]')).toBeVisible();

    const before = await page.locator('.react-flow__node').count();
    await palette.locator('.pal[data-archi-type="archimate:Capability"]').dragTo(page.getByTestId('canvas'), {
      targetPosition: { x: 200, y: 80 },
    });
    await expect(page.locator('.react-flow__node')).toHaveCount(before + 1);
    const created = await editorState<{ layer: string; supported: boolean }[]>(
      page,
      "Object.values(s.doc.elements).filter(e => e.archiType === 'archimate:Capability' && e.name === 'Способность')",
    );
    expect(created).toEqual([expect.objectContaining({ layer: 'STRATEGY', supported: true })]);

    await page.getByTestId('save').click();
    await expect(page.getByTestId('save')).toBeHidden();
    const posts = api.writes().filter((c) => c.method === 'POST' && c.path === `/models/${api.modelId}/elements`);
    expect(posts.map((c) => (c.body as { archiType: string }).archiType)).toEqual(['archimate:Capability']);
    expect(api.writes().map((c) => `${c.method} ${c.path}`)).toContain(`POST /views/${viewId}/nodes`);
  });
});
