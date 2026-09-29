# Фронтенд

React 19 + TypeScript + Vite. Живёт внутри `archi-bootstrap`, а не отдельным
модулем реактора — [`ADR-0016`](../../../../spec/adr/0016-frontend-location.md).

## Разработка

Два процесса. Бэкенд на профиле `dev`:

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
на `:8080`. Поэтому в коде адреса относительные: `fetch('/actuator/health')`
работает одинаково и в разработке, и в собранном jar. Базового URL в сборке
нет, ветки «а в разработке спроси по другому адресу» — тоже.

Бэкенд на другом порту — `ARCHI_BACKEND_URL=http://localhost:8088 npm run dev`.

Порт `5173` зафиксирован (`strictPort`): он же прописан в `redirectUris`
клиента Keycloak и в CORS профиля `dev`. Плавающий порт ломал бы вход.

## Сборка

Отдельно её запускать не нужно: `mvn package` в корне поднимает
`frontend-maven-plugin`, который делает `npm ci && npm run build` и кладёт
результат в `target/classes/static` — внутрь jar ([§10.2](../../../../docs/archi-creator.md#102-сборка)).

Собрать без фронтенда: `./mvnw package -P '!frontend'`.

## Что здесь есть и чего нет

Есть каркас: точка входа, `QueryClientProvider`, пустое хранилище документа
на Zustand и один экран, который показывает, что бэкенд отвечает.

Нет ничего из этапа 3: ни канвы, ни дерева, ни панели свойств, ни токенов
слоёв ArchiMate. Токены сюда не переносились сознательно — их единственный
источник [`spec/ui/design-tokens.yaml`](../../../../spec/ui/design-tokens.yaml)
(FR-20), и копия до канвы была бы вторым источником правды.
