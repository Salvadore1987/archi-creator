import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

/**
 * Сборка кладёт результат прямо в `target/classes/static` модуля
 * `archi-bootstrap` — оттуда его подхватывает Spring Boot и запекает в jar
 * (§10.2). Отдельного шага копирования нет: лишний шаг — лишнее место,
 * где пути разойдутся.
 */
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../../../target/classes/static',
    emptyOutDir: true,
    sourcemap: true,
  },
});
