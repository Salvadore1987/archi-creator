import { expect, type Page } from '@playwright/test';
import { editorState, type MockApi } from './mockApi';

/** Холст открытого представления и его узлы — по узлу или по элементу. */
export function canvasOf(page: Page, viewId: string) {
  const canvas = page.locator(`[data-testid="canvas"][data-view="${viewId}"]`);
  return {
    canvas,
    node: (nodeId: string) => canvas.locator(`.react-flow__node[data-id="${nodeId}"]`),
    async box(nodeId: string) {
      return (await canvas.locator(`.react-flow__node[data-id="${nodeId}"]`).boundingBox())!;
    },
  };
}

/** Перетащить мышью из точки в точку — с промежуточными шагами, как рука. */
export async function drag(page: Page, from: { x: number; y: number }, to: { x: number; y: number }, steps = 8) {
  await page.mouse.move(from.x, from.y);
  await page.mouse.down();
  await page.mouse.move(to.x, to.y, { steps });
  await page.mouse.up();
}

/** Узлы представления верхнего уровня над элементами — в порядке файла. */
export function rootNodes(api: MockApi, viewId: string) {
  return api.views[viewId]!.nodes.filter((n) => !n.parentId && n.elementId) as Array<{
    id: string;
    elementId: string;
    x: number;
    y: number;
    width: number;
    height: number;
  }>;
}

export function nodeState<T>(page: Page, viewId: string, nodeId: string, field: string) {
  return editorState<T>(page, `s.doc.loadedViews['${viewId}'].nodes['${nodeId}']?.${field}`);
}

/** Фокус в нейтральное место корпуса, чтобы горячие клавиши шли редактору, а не полю. */
export async function focusChrome(page: Page) {
  await page.getByTestId('model-tree').locator('.panel-head').click();
  await expect(page.locator('input:focus, textarea:focus')).toHaveCount(0);
}
