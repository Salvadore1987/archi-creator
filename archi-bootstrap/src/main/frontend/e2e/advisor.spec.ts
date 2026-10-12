import { expect, test } from '@playwright/test';
import { editorState, openModel, openView, type AdvisorMock } from './support/mockApi';

const META = { sessionId: '00000000-0000-7000-8000-000000000001', outcome: 'COMPLETED', promptTokens: 900, completionTokens: 120, contextTruncated: false };

/**
 * Помощник в интерфейсе: поток находок, переход к элементу, план фильтрует палитру,
 * отказ сервера не ломает редактор. Модель помощник не меняет — ни одной команды
 * правки после его ответов не уходит.
 */
test.describe('помощник', () => {
  test('ревью выдаёт находки, переход к элементу выделяет его на холсте и в дереве', async ({ page }) => {
    const advisor: AdvisorMock = { replies: {} };
    const api = await openModel(page, { advisor });
    await openView(page, api, '01 ');
    const iabs = api.element('IABS');
    advisor.replies!.review = {
      frames: [
        ['token', { text: 'Проверяю модель. ' }],
        ['finding', {
          id: '00000000-0000-7000-8000-0000000000f1', severity: 'WARNING', code: 'DUPLICATE_ELEMENT',
          message: 'IABS и IABS NG описывают одну систему', targetKind: 'ELEMENT',
          target: { archiId: iabs.archiId, name: 'IABS' }, suggestion: 'Оставьте одно имя',
        }],
        ['meta', META],
      ],
    };

    await page.getByTestId('advisor').getByRole('button', { name: 'Ревью модели' }).click();
    const finding = page.getByTestId('advisor-finding');
    await expect(finding).toHaveCount(1);
    await expect(finding).toContainText('DUPLICATE_ELEMENT');
    await finding.getByRole('button', { name: 'Перейти к элементу' }).click();

    await expect(page.locator(`.react-flow__node:has([data-element="${iabs.id}"])`)).toHaveClass(/selected/);
    await expect(page.locator(`[data-row="${iabs.id}"]`)).toHaveClass(/is-sel/);
    expect(await editorState<string[]>(page, 's.selection.ids')).toEqual([iabs.id]);
    expect(api.writes().filter((c) => !c.path.startsWith('/ai/') && !c.path.endsWith('/lock'))).toEqual([]);
  });

  test('план помощника фильтрует палитру по своим типам, пока фильтр не сброшен', async ({ page }) => {
    const advisor: AdvisorMock = {
      replies: {
        plan: {
          frames: [
            ['plan', {
              elements: [
                { archiType: 'archimate:BusinessActor', name: 'Клиент' },
                { archiType: 'archimate:ApplicationService', name: 'Выдача карты' },
              ],
              relationships: [{ archiType: 'archimate:ServingRelationship', sourceName: 'Выдача карты', targetName: 'Клиент', permitted: true }],
            }],
            ['meta', META],
          ],
        },
      },
    };
    await openModel(page, { advisor });

    await page.getByPlaceholder('Опишите фрагмент архитектуры — помощник предложит план').fill('Выдача карты клиенту');
    await page.getByRole('button', { name: 'Предложить план' }).click();

    await expect(page.getByTestId('advisor-plan')).toContainText('Выдача карты');
    const palette = page.getByTestId('palette');
    await expect(palette.locator('[data-archi-type]')).toHaveCount(2);
    await expect(palette.locator('[data-archi-type="archimate:BusinessActor"]')).toBeVisible();
    await page.getByRole('button', { name: 'Показать всю палитру' }).click();
    await expect(palette.locator('[data-archi-type]')).not.toHaveCount(2);
  });

  test('исчерпанный бюджет — объяснение в панели, редактор работает', async ({ page }) => {
    const advisor: AdvisorMock = {
      replies: {
        review: { status: 429, problem: { status: 429, title: 'Too Many Requests', code: 'BUDGET_EXHAUSTED', resetsAt: '2026-11-01' } },
      },
    };
    const api = await openModel(page, { advisor });

    await page.getByTestId('advisor').getByRole('button', { name: 'Ревью модели' }).click();

    await expect(page.getByTestId('advisor-error')).toContainText('2026-11-01');
    const viewId = await openView(page, api, '01 ');
    await expect(page.locator(`[data-testid="canvas"][data-view="${viewId}"] .react-flow__node`).first()).toBeVisible();
  });

  test('выключенный помощник — панели нет', async ({ page }) => {
    await openModel(page, { advisor: { enabled: false } });
    await expect(page.getByTestId('inspector')).toBeVisible();
    await expect(page.getByTestId('advisor')).toHaveCount(0);
  });

  test('документация по элементу из карточки — Markdown со ссылками на объекты', async ({ page }) => {
    const advisor: AdvisorMock = { replies: {} };
    const api = await openModel(page, { advisor, roles: ['VIEWER'] });
    const iabs = api.element('IABS');
    advisor.replies!.document = {
      frames: [
        ['token', { text: '## Влияние\n' }],
        ['answer', { markdown: `## Влияние\n- [IABS](archi:${iabs.archiId}) — ядро`, references: [{ archiId: iabs.archiId, name: 'IABS' }] }],
        ['meta', META],
      ],
    };
    await page.getByPlaceholder('Поиск по модели').fill('IABS');
    await page.locator(`[data-row="${iabs.id}"]`).click();

    await page.getByTestId('advisor-document').click();

    const answer = page.getByTestId('advisor-answer');
    await expect(answer.locator('h3')).toHaveText('Влияние');
    await expect(answer.getByRole('button', { name: 'IABS' })).toBeVisible();
    expect(api.calls.find((c) => c.path === '/ai/document')!.body).toEqual({ modelId: api.modelId, elementId: iabs.archiId });
  });
});
