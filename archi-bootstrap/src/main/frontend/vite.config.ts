import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

/** Адрес приложения. Переопределяется, когда бэкенд поднят на другом порту. */
const BACKEND = process.env.ARCHI_BACKEND_URL ?? 'http://localhost:8080';

/**
 * Сборка кладёт результат прямо в `target/classes/static` модуля
 * `archi-bootstrap` — оттуда его подхватывает Spring Boot и запекает в jar
 * (§10.2). Отдельного шага копирования нет: лишний шаг — лишнее место,
 * где пути разойдутся.
 *
 * Прокси нужен профилю `dev` (§10.3): страница открывается на :5173,
 * а `/api` и `/actuator` уходят на приложение, как будто они того же
 * происхождения. Так фронтенд в разработке и в бою видит одни и те же
 * адреса, и CORS в код не просачивается — ни базового URL в сборке,
 * ни ветки «а в разработке спроси по другому адресу».
 */
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../../../target/classes/static',
    emptyOutDir: true,
    sourcemap: true,
  },
  server: {
    port: 5173,
    // Порт не плавающий: он же прописан в redirectUris клиента Keycloak
    // (deploy/keycloak/archi-realm.json) и в CORS профиля dev.
    strictPort: true,
    proxy: {
      '/api': { target: BACKEND, changeOrigin: false },
      '/actuator': { target: BACKEND, changeOrigin: false },
    },
  },
});
