import { expect, test } from '@playwright/test';
import { editorState, openModel, openView } from './support/mockApi';

test('UI-020: неподдержанный объект виден, двигается, но не правится', async ({ page }) => {
  const api = await openModel(page);
  const viewId = await openView(page, api, '11 ');
  // Узел-лист: у контейнера середину закрывают вложенные узлы.
  const view = api.views[viewId]!;
  const leaf = view.nodes.find(
    (n) => n.elementId && !view.nodes.some((child) => child.parentId === n.id) &&
      api.tree.elements.some((e) => e.id === n.elementId && e.archiType === 'archimate:Capability'),
  )!;
  const capability = api.tree.elements.find((e) => e.id === leaf.elementId) as { id: string; name: string };
  const node = page.locator(`.react-flow__node:has([data-element="${capability.id}"])`).first();
  await expect(node).toContainText(capability.name);
  await expect(node.locator('.archi-node--opaque')).toHaveCount(1);
  await expect(node.locator('[data-archi-type="archimate:Capability"]')).toHaveCount(1);

  // Через середину узла проходит связь — клик в стороне от линии, как сделал бы человек.
  await node.click({ position: { x: 14, y: 10 } });
  await expect(page.getByTestId('prop-name')).not.toBeEditable();

  const before = await editorState<number>(page, `Object.values(s.doc.loadedViews['${viewId}'].nodes).find(n => n.elementId === '${capability.id}').x`);
  const box = (await node.boundingBox())!;
  await page.mouse.move(box.x + 14, box.y + 10);
  await page.mouse.down();
  await page.mouse.move(box.x + 134, box.y + 10, { steps: 8 });
  await page.mouse.up();
  const after = await editorState<number>(page, `Object.values(s.doc.loadedViews['${viewId}'].nodes).find(n => n.elementId === '${capability.id}').x`);
  expect(after).not.toBe(before);
});
