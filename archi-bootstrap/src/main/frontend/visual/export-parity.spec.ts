import { readFileSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import { openModel } from '../e2e/support/mockApi';

/**
 * Сверка канвы и серверного экспорта: снимок представления на холсте при 100 %
 * против PNG, напечатанного сервером из того же файла. Эталоны сервера лежат
 * в `visual/export-parity/` — их держит в актуальном виде `ExportParityFixturesTest`.
 * Расхождение выше допуска — падение сборки: два рендерера не должны разойтись.
 */

/** Представления эталона: префикс имени в дереве и идентификатор Archi эталона сервера. */
const VIEWS = [
  { prefix: '01 ', archiId: 'id-f11fbcb80991efa5a637e786' },
  { prefix: '04 ', archiId: 'id-0071f2a029a55a5baf3764d7' },
  { prefix: '05 ', archiId: 'id-c249b4ce7da44d6b354f9e9d' },
  { prefix: '06 ', archiId: 'id-8a09711388778e059c6d5fb9' },
  { prefix: '10 ', archiId: 'id-56477524f08510e0e54a5d04' },
];

/** После размытия 3×3 канал различается больше чем на столько — пиксель считается разным. */
const CHANNEL_TOLERANCE = 48;
/** Допустимая доля разных пикселей на всей картинке. */
const MAX_DIFF_RATIO = 0.002;
/**
 * Допустимая доля разных пикселей в худшем квадрате 24×24. Сглаживание даёт разреженный
 * шум (до 0,06), неверный перенос или сдвиг подписи — плотное пятно (от 0,2).
 */
const MAX_BLOCK_RATIO = 0.12;
/** Отступ рамки эталона от края холста. */
const PAD = 8;

interface Frame {
  x: number;
  y: number;
  width: number;
  height: number;
}

const fixture = (name: string) => new URL(`./export-parity/${name}`, import.meta.url);

// Chromium в Linux по умолчанию хинтует шрифт и округляет ширины глифов до пикселя:
// строка выходит шире и переносится раньше, чем в браузерах Windows и macOS.
// Сверка идёт с раскладкой на дробных ширинах — её видят пользователи и её считает сервер.
test.use({ viewport: { width: 2700, height: 1300 }, launchOptions: { args: ['--font-render-hinting=none'] } });

for (const { prefix, archiId } of VIEWS) {
  test(`UI-019: канва совпадает с серверным PNG — ${prefix.trim()}`, async ({ page }, testInfo) => {
    const frame = JSON.parse(readFileSync(fixture(`${archiId}.json`), 'utf8')) as Frame;
    const server = readFileSync(fixture(`${archiId}.png`));
    const api = await openModel(page, { roles: ['VIEWER'] });
    const view = api.viewByName(prefix);

    // Вид задаётся до открытия: холст встаёт на 100 % так, что рамка эталона — в углу.
    await page.evaluate(
      ({ id, x, y }) => {
        const store = (window as unknown as { __archiEditor: { getState(): { setViewport(id: string, v: object): void } } })
          .__archiEditor;
        store.getState().setViewport(id, { x, y, zoom: 1 });
      },
      { id: view.id, x: PAD - frame.x, y: PAD - frame.y },
    );
    await page.locator(`[data-row="${view.id}"]`).dblclick();
    const flow = page.locator(`[data-testid="canvas"][data-view="${view.id}"] .react-flow`);
    await flow.locator('.react-flow__node').first().waitFor();
    await page.addStyleTag({
      content: `.react-flow__background, .react-flow__minimap, .react-flow__panel, .react-flow__attribution,
        .canvas-tools, .archi-node__connect { display: none !important; }`,
    });
    await page.evaluate(() => document.fonts.ready);
    const pane = (await flow.boundingBox())!;
    expect(pane.width).toBeGreaterThan(frame.width + PAD);
    expect(pane.height).toBeGreaterThan(frame.height + PAD);

    const canvas = await page.screenshot({
      clip: { x: pane.x + PAD, y: pane.y + PAD, width: frame.width, height: frame.height },
    });
    const result = await compare(page, canvas, server);
    if (result.ratio > MAX_DIFF_RATIO || result.worst > MAX_BLOCK_RATIO) {
      await testInfo.attach('canvas.png', { body: canvas, contentType: 'image/png' });
      await testInfo.attach('server.png', { body: server, contentType: 'image/png' });
      await testInfo.attach('diff.png', { body: Buffer.from(result.diff, 'base64'), contentType: 'image/png' });
    }
    expect(result.ratio, `доля разных пикселей ${result.ratio.toFixed(4)}`).toBeLessThanOrEqual(MAX_DIFF_RATIO);
    expect(result.worst, `худший квадрат ${result.worst.toFixed(3)}`).toBeLessThanOrEqual(MAX_BLOCK_RATIO);
  });
}

/**
 * Сравнение в браузере: доля пикселей, где после размытия хоть один канал разошёлся
 * больше допуска, та же доля в худшем квадрате 24×24 и карта разницы.
 */
async function compare(page: Page, canvas: Buffer, server: Buffer): Promise<{ ratio: number; worst: number; diff: string }> {
  return page.evaluate(
    async ({ a, b, tolerance }) => {
      const load = async (base64: string) => {
        const image = await createImageBitmap(await (await fetch(`data:image/png;base64,${base64}`)).blob());
        const surface = new OffscreenCanvas(image.width, image.height);
        const context = surface.getContext('2d')!;
        context.drawImage(image, 0, 0);
        return context.getImageData(0, 0, image.width, image.height);
      };
      const [left, right] = await Promise.all([load(a), load(b)]);
      const width = Math.min(left.width, right.width);
      const height = Math.min(left.height, right.height);
      // Размытие 3×3 гасит разницу сглаживания глифов, но не сдвиг слова или линии.
      const blur = (img: ImageData) => {
        const out = new Float32Array(width * height * 3);
        for (let y = 0; y < height; y++) {
          for (let x = 0; x < width; x++) {
            for (let c = 0; c < 3; c++) {
              let sum = 0;
              let n = 0;
              for (let dy = -1; dy <= 1; dy++) {
                for (let dx = -1; dx <= 1; dx++) {
                  const xx = x + dx;
                  const yy = y + dy;
                  if (xx < 0 || yy < 0 || xx >= width || yy >= height) continue;
                  sum += img.data[(yy * img.width + xx) * 4 + c]!;
                  n++;
                }
              }
              out[(y * width + x) * 3 + c] = sum / n;
            }
          }
        }
        return out;
      };
      const l = blur(left);
      const r = blur(right);
      const diff = new OffscreenCanvas(width, height);
      const out = diff.getContext('2d')!.createImageData(width, height);
      let differing = 0;
      const block = 24;
      const columns = Math.ceil(width / block);
      const blocks = new Uint32Array(columns * Math.ceil(height / block));
      for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
          const i = (y * width + x) * 3;
          const k = (y * width + x) * 4;
          let delta = 0;
          for (let c = 0; c < 3; c++) delta = Math.max(delta, Math.abs(l[i + c]! - r[i + c]!));
          const bad = delta > tolerance;
          if (bad) {
            differing++;
            blocks[Math.floor(y / block) * columns + Math.floor(x / block)]!++;
          }
          out.data[k] = bad ? 255 : l[i]! * 0.3;
          out.data[k + 1] = bad ? 0 : l[i + 1]! * 0.3;
          out.data[k + 2] = bad ? 0 : l[i + 2]! * 0.3;
          out.data[k + 3] = 255;
        }
      }
      diff.getContext('2d')!.putImageData(out, 0, 0);
      const blob = await diff.convertToBlob({ type: 'image/png' });
      const bytes = new Uint8Array(await blob.arrayBuffer());
      let binary = '';
      for (const byte of bytes) binary += String.fromCharCode(byte);
      const sizePenalty = Math.abs(left.width * left.height - right.width * right.height);
      const worst = Math.max(0, ...blocks) / (block * block);
      return { ratio: (differing + sizePenalty) / (right.width * right.height), worst, diff: btoa(binary) };
    },
    { a: canvas.toString('base64'), b: server.toString('base64'), tolerance: CHANNEL_TOLERANCE },
  );
}
