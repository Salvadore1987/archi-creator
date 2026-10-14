import { expect, test } from '@playwright/test';
import { openModel, openView, type MockApi } from './support/mockApi';

const PROPOSAL = '3f9c2e1d'.padEnd(40, '0');
const BASE = '9b1d77aa'.padEnd(40, '0');

/** Отчёт подменного сервера: конфликт имени IABS, новый узел на «01», удаление одной связи. */
function report(api: MockApi) {
  const iabs = api.element('IABS');
  const view = api.viewByName('01');
  const relation = api.tree.relationships[0] as { archiId: string; name?: string };
  return (resolutions: Record<string, string>) => {
    const conflictId = `${iabs.archiId}:name`;
    const resolution = resolutions[conflictId];
    return {
      modelId: api.modelId,
      ref: 'proposal/kanaly',
      refKind: 'BRANCH',
      commitSha: PROPOSAL,
      mergeBaseSha: BASE,
      baseVersionNo: 1,
      baseHash: 'b'.repeat(64),
      targetHash: 'c'.repeat(64),
      unchanged: false,
      applicable: resolution !== undefined,
      conflicts: [
        {
          id: conflictId, kind: 'ELEMENT', archiId: iabs.archiId, type: 'ApplicationComponent', name: 'IABS',
          field: 'name', base: 'IABS', ours: 'IABS «Ядро»', theirs: 'IABS 2', resolution,
        },
      ],
      cascade: [{ kind: 'RELATIONSHIP', archiId: relation.archiId, name: relation.name ?? '', reason: 'RELATION_ENDPOINT_REMOVED' }],
      summary: [{ kind: 'ELEMENT', added: 1, removed: 0, changed: 1 }],
      changes: [
        { kind: 'ELEMENT', change: 'ADDED', archiId: 'id-new-service', type: 'ApplicationService', name: 'Новый сервис', fields: [] },
        { kind: 'ELEMENT', change: 'CHANGED', archiId: iabs.archiId, type: 'ApplicationComponent', name: 'IABS', fields: ['name'] },
      ],
      marks: [
        { archiId: iabs.archiId, mark: 'CHANGED' },
        { archiId: relation.archiId, mark: 'REMOVED' },
        { archiId: 'id-new-service', mark: 'ADDED' },
      ],
      views: [
        {
          archiId: view.archiId,
          addedNodes: [{ archiId: 'id-new-node', elementArchiId: 'id-new-service', type: 'ApplicationService', name: 'Новый сервис', x: 40, y: 40, width: 120, height: 55 }],
        },
      ],
      findings: [],
    };
  };
}

test.describe('FR-39, INV-IXC-010: слияние ветки предложения на холсте', () => {
  test('итог слияния виден на холсте, конфликт решается по полю, применение — новая версия', async ({ page }) => {
    let api: MockApi | undefined;
    api = await openModel(page, { merge: (resolutions) => report(api!)(resolutions) });
    const viewId = await openView(page, api, '01');
    const iabs = api.element('IABS');

    await page.getByTestId('merge-open').click();
    await page.getByTestId('merge-ref').fill('proposal/kanaly');
    await page.getByTestId('merge-build').click();

    const panel = page.getByTestId('merge-panel');
    await expect(panel.getByTestId('merge-commits')).toContainText('3f9c2e1');
    await expect(panel.getByTestId('merge-summary')).toHaveText('Добавится: 1 · удалится: 0 · изменится: 1');
    const canvas = page.locator(`[data-testid="canvas"][data-view="${viewId}"]`);
    await expect(canvas.locator(`.archi-node[data-element="${iabs.id}"]`)).toHaveAttribute('data-merge', 'CHANGED');
    await expect(canvas.locator('[data-merge-ghost="merge:id-new-node"]')).toContainText('Новый сервис');
    await expect(panel.getByTestId('merge-cascade')).toContainText('удалён конец связи');

    const conflict = panel.getByTestId('merge-conflict');
    await expect(conflict).toContainText('IABS «Ядро»');
    await expect(conflict).toContainText('IABS 2');
    await expect(panel.getByTestId('merge-apply')).toBeDisabled();
    await expect(panel.getByTestId('merge-blocked')).toHaveText('Сделайте выбор по каждому конфликту.');

    const rebuilt = page.waitForRequest((r) => r.url().endsWith('/git/merge/preview') && r.method() === 'POST');
    // Выбор управляется отчётом: радиокнопка отмечается, когда сервер пересчитал слияние.
    await conflict.getByTestId('choose-theirs').click();
    expect((await rebuilt).postDataJSON()).toEqual({
      ref: 'proposal/kanaly',
      resolutions: { [`${iabs.archiId}:name`]: 'THEIRS' },
    });
    await expect(conflict.getByTestId('choose-theirs')).toBeChecked();
    await expect(panel.getByTestId('merge-apply')).toBeEnabled();

    const applied = page.waitForRequest((r) => r.url().endsWith('/git/merge') && r.method() === 'POST');
    await panel.getByTestId('merge-apply').click();
    expect((await applied).postDataJSON()).toEqual({
      ref: 'proposal/kanaly',
      commitSha: PROPOSAL,
      mergeBaseSha: BASE,
      baseHash: 'b'.repeat(64),
      resolutions: { [`${iabs.archiId}:name`]: 'THEIRS' },
    });
    await expect(page.getByText('Слияние применено: версия 2')).toBeVisible();
    await expect(page.getByTestId('merge-panel')).toHaveCount(0);
    await expect(page.locator('[data-merge]')).toHaveCount(0);
  });

  test('отказ сервера виден в панели', async ({ page }) => {
    await openModel(page);
    await page.getByTestId('merge-open').click();
    await page.getByTestId('merge-ref').fill('main');
    await page.getByTestId('merge-build').click();
    await expect(page.getByTestId('merge-panel').getByRole('alert')).toContainText('нет общего предка');
  });

  test('читатель слияния не видит', async ({ page }) => {
    await openModel(page, { roles: ['VIEWER'] });
    await expect(page.getByTestId('merge-open')).toHaveCount(0);
  });
});
