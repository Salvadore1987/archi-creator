import { resolve } from 'node:path';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';
import { designTokensPlugin } from './build/design-tokens.ts';

/** Адрес приложения. Переопределяется, когда бэкенд поднят на другом порту. */
const BACKEND = process.env.ARCHI_BACKEND_URL ?? 'http://localhost:8080';

/** Корень репозитория: отсюда читаются токены и общая с сервером геометрия фигур. */
const REPO_ROOT = resolve(import.meta.dirname, '../../../..');

/**
 * Сборка кладёт результат прямо в `target/classes/static` модуля
 * `archi-bootstrap` — оттуда его подхватывает Spring Boot и запекает в jar.
 * Отдельного шага копирования нет: лишний шаг — лишнее место,
 * где пути разойдутся.
 *
 * Прокси нужен профилю `dev`: страница открывается на :5173,
 * а `/api` и `/actuator` уходят на приложение, как будто они того же
 * происхождения. Так фронтенд в разработке и в бою видит одни и те же
 * адреса, и CORS в код не просачивается — ни базового URL в сборке,
 * ни ветки «а в разработке спроси по другому адресу».
 *
 * Геометрия фигур и спрайт иконок лежат в ресурсах сервера
 * (`archi-bootstrap/src/main/resources/ui`): их же читает серверный
 * писатель SVG, и копии во фронтенде нет — алиас `@shared-ui`.
 */
export default defineConfig({
  plugins: [react(), designTokensPlugin()],
  resolve: {
    alias: {
      '@shared-ui': resolve(import.meta.dirname, '../resources/ui'),
    },
  },
  build: {
    outDir: '../../../target/classes/static',
    emptyOutDir: true,
    sourcemap: true,
    chunkSizeWarningLimit: 1500,
  },
  server: {
    port: 5173,
    // Порт не плавающий: он же прописан в redirectUris клиента Keycloak
    // (deploy/keycloak/archi-realm.json) и в CORS профиля dev.
    strictPort: true,
    fs: { allow: [REPO_ROOT] },
    proxy: {
      '/api': { target: BACKEND, changeOrigin: false },
      '/actuator': { target: BACKEND, changeOrigin: false },
    },
  },
  test: {
    include: ['src/**/*.test.ts', 'build/**/*.test.ts', 'perf/**/*.bench.ts'],
    environment: 'node',
  },
});
