# Фронтенд

React 19 + TypeScript + Vite, канва на React Flow. Живёт внутри `archi-bootstrap`,
а не отдельным модулем реактора — [`ADR-0016`](../../../../spec/adr/0016-frontend-location.md).

## Разработка

Два процесса. Бэкенд на профиле `dev` (вход заглушкой `ARCHITECT`, Keycloak не нужен):

```bash
./mvnw -pl archi-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=dev
```

Vite рядом:

```bash
cd archi-bootstrap/src/main/frontend
npm install
npm run dev            # http://localhost:5173
```

Страница открывается на `:5173`, а `/api` и `/actuator` прокси уводит
на `:8080`. Поэтому в коде адреса относительные. Бэкенд на другом порту —
`ARCHI_BACKEND_URL=http://localhost:8088 npm run dev`.

Порт `5173` зафиксирован (`strictPort`): он же прописан в `redirectUris`
клиента Keycloak и в CORS профиля `dev`. Плавающий порт ломал бы вход.

В dev-сборке документ сессии доступен из консоли: `__archiEditor.getState()`.
Галерея всех силуэтов — `http://localhost:5173/?gallery`.

## Тесты

```bash
npm test               # vitest: история, дерево, геометрия, токены, статические проверки
npm run e2e            # Playwright: e2e/, visual/, perf/canvas-pan — API подменяется, сервер не нужен
npx playwright test --update-snapshots=all visual   # осознанно обновить эталон галереи
node e2e/fixtures/capture.mjs http://localhost:8080 <modelId>   # переснять фикстуру эталона
```

`npm test` гоняется и фазой `test` Maven. Playwright в CI подключается на этапе 4;
браузер ставится один раз: `npx playwright install chromium`.

## Сборка

Отдельно её запускать не нужно: `mvn package` в корне поднимает
`frontend-maven-plugin`, который делает `npm ci`, `npm test` и `npm run build`
и кладёт результат в `target/classes/static` — внутрь jar
([§10.2](../../../../docs/archi-creator.md#102-сборка)).
Собрать без фронтенда: `./mvnw package -P '!frontend'`.

## Устройство

| Каталог | Что внутри |
|---|---|
| `build/` | Плагин Vite: токены из `spec/ui/design-tokens.yaml` в CSS-переменные |
| `src/model/` | Документ модели, обратимые изменения, история, перевод в команды сервера |
| `src/canvas/` | Холст: узлы по `shapes.json`, связи по нотации, маршрутизация |
| `src/tree/` | Дерево модели и его правка |
| `src/panels/` | «Свойства», «Описание», сводка выделения |
| `src/i18n/` | Все строки интерфейса и русские названия типов |

Геометрия фигур (`shapes.json`) и спрайт иконок (`icons.svg`) лежат
в `../resources/ui/` — в classpath сервера: их же прочитает серверный
писатель SVG, копии во фронтенде нет. Токены — только в спеке.

Правки живут в браузере и уходят на сервер при сохранении
([`ADR-0018`](../../../../spec/adr/0018-local-edit-session.md)): отмена
до сохранения серверу не видна, а «Сохранить» синхронизирует и фиксирует версию.
