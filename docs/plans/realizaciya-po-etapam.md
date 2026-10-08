# План реализации Archi Creator по этапам

## Контекст

План производен от нормативной спецификации [`spec/`](../../spec/README.md) и от
порядка работ [§12](../archi-creator.md#12-этапы-работ). Он не вводит новых
требований: каждая задача ссылается на требование (`FR-xx`/`NFR-xx`), якорь
(`INV-*`, `UC-*`, `UI-*`) или решение (`ADR-*`), из которого она следует.
Если задача ни на что не ссылается — это либо пробел спеки (заводить якорь),
либо лишняя работа.

**Состояние на 2026-09-29:** **этап 0 закрыт целиком.** Приложение поднимается
`docker compose up -d`, `/actuator/health` = UP, вход через Keycloak отдаёт JWT
с ролью. Есть конфигурация и профили, миграция `V1`, ресурс-сервер OAuth2,
realm Keycloak, каркас фронтенда внутри `archi-bootstrap` (`ADR-0016`),
ArchUnit на изоляцию контекстов и конвейер CI с тремя гейтами.

Доменной логики по-прежнему нет: в двенадцати модулях `package-info.java`,
и тесты пока только каркасные — семь штук, все в `archi-bootstrap`. Следующий
этап — 1, кодек `.archimate`.

**Как вести файл.** Сделанное — ✅, несделанное — ⬜. Отметка ставится по факту,
а не по намерению: задача закрыта, когда есть код **и** тест из таблицы «Маппинг
на тесты» соответствующего `invariants.md`. Значки, а не `- [x]`: галочку видно
в любом просмотрщике, включая тот, что не умеет task list. Расхождения плана с фактом, найденные по ходу дефекты и
отменённые варианты писать в раздел [«Журнал»](#журнал) — через месяц «почему
сделано так» отвечает он.

## Порядок и зависимости

```
0 ──▶ 1 ──▶ 2 ──▶ 3 ──▶ 4 ──┬──▶ 5
                            ├──▶ 5a (нужен shapes.json из 3)
                            ├──▶ 6 ──▶ 6a
                            └──▶ 7a ──▶ 7b ──▶ 7c
```

Строгая последовательность 1 → 2 → 3 → 4 не декоративна: канва опирается на
структуру, которую даёт кодек, а кодек закрывается round-trip референсной модели.
Браться за 3 раньше, чем зелёные golden-file тесты, смысла нет ([§12](../archi-creator.md#12-этапы-работ)).
После 4 ветки 5, 5a, 6, 7a независимы друг от друга.

## Охват требований по этапам

| Этап | Закрывает | BC / слой |
|---|---|---|
| 0 | FR-28 (частично) | bootstrap, инфраструктура |
| 1 | FR-01…FR-03, FR-07…FR-10, FR-21, FR-50, NFR-02, NFR-05 | interchange-domain, modeling-domain |
| 2 | FR-01, FR-02, FR-04…FR-06, FR-11, FR-28, FR-29, FR-45, FR-46, FR-48…FR-50, NFR-02, NFR-03, NFR-07 | modeling + interchange, все четыре слоя |
| 3 | FR-03, FR-11, FR-12, FR-16…FR-21, FR-30…FR-36, FR-42, NFR-01, NFR-08 | ui |
| 4 | FR-10, FR-13…FR-16, FR-35 | ui + modeling |
| 5 | FR-22…FR-27, FR-51…FR-53, NFR-04, NFR-06 | advisor |
| 5a | FR-41, FR-42 | interchange (export) |
| 6 | FR-08 | modeling (метамодель фазы 2) |
| 6a | FR-43, FR-44 | interchange (oef) |
| 7a | FR-37, FR-40, FR-46, FR-47 | interchange (git) |
| 7b | FR-38 | interchange (git) |
| 7c | FR-39 | interchange (git) |

---

## Этап 0 — каркас

**Критерий готовности (§12):** `docker compose up` поднимает пустое приложение
с логином. **Закрыт 2026-09-29**, проверка — в разделе «Выход этапа».

### Сделано

- ✅ Multi-module Maven по [`ADR-0001`](../../spec/adr/0001-maven-multi-module.md):
      17 проектов, jar в `archi-bootstrap/target/`
- ✅ Граница домена держится сборкой: `maven-enforcer-plugin` в шести модулях
      (`domain` и `application` трёх контекстов)
- ✅ Класс запуска `uz.salvadore.hamkorbank.archi.ArchiCreatorApplication`
- ✅ Проверки целостности спеки: `tools/check-links.py`,
      `tools/check-traceability.py`, `tools/render-requirements-index.py`

### Конфигурация и запуск

- ✅ `archi-bootstrap/src/main/resources/application.yaml`: datasource, Flyway,
      issuer Keycloak, модель ИИ (`claude-sonnet-5`), actuator, пул соединений
      и таймауты из [`spec/nfr/modeling.yaml`](../../spec/nfr/modeling.yaml)
- ✅ Профили `dev` и `prod` ([§10.3](../archi-creator.md#103-профили-и-конфигурация)):
      в `dev` авторизация заглушкой `ARCHITECT`, в `prod` Keycloak обязателен,
      CORS закрыт, Flyway `validate`, логи JSON
- ✅ Секреты только через переменные окружения и `.env` вне репозитория
      (`ANTHROPIC_API_KEY`, пароль БД, client secret) — NFR-06
- ✅ Flyway `V1__baseline.sql`: `workspace` с полями `ai_enabled`,
      `ai_monthly_token_limit`, `strict_import`, `git_repo_url`, `git_branch`,
      `git_token_ref` ([§4.1](../database.md#41-сущности), [§11.6](../database.md#116-что-нужно-сделать-уже-на-этапе-1))
- ✅ `docker-compose.yml`: `app`, `postgres:16`, `keycloak:26`
      ([§10.1](../archi-creator.md#101-состав)); путь к jar — `archi-bootstrap/target/`
- ✅ Realm Keycloak с ролями `VIEWER`, `ARCHITECT`, `ADMIN` (FR-28), экспорт
      realm в репозиторий для воспроизводимого поднятия
- ✅ Spring Security OAuth2 Resource Server в `archi-bootstrap`, маппинг ролей
      из JWT, заглушка профиля `dev`
- ✅ Обёртка `mvnw`: каталог `.mvn/` был пуст, версия Maven зависела от машины.
      Тип `only-script`, версия 3.9.16 в `.mvn/wrapper/maven-wrapper.properties`

### Фронтенд-каркас

- ✅ **Решение:** фронтенд живёт в `archi-bootstrap/src/main/frontend`,
      собирается `frontend-maven-plugin` там же — реактор остаётся
      семнадцатипроектным, jar одним.
      [`ADR-0016`](../../spec/adr/0016-frontend-location.md)
- ✅ `package.json`, Vite, React 19, TypeScript, Zustand, TanStack Query
      ([§3.2](../archi-creator.md#32-стек))
- ✅ `frontend-maven-plugin` в сборке: `npm ci && npm run build` →
      `target/classes/static` ([§10.2](../archi-creator.md#102-сборка))
- ✅ Vite dev-сервер с прокси на `:8080` для профиля `dev`

### Контур проверок

- ✅ ArchUnit в `archi-bootstrap`: изоляция контекстов друг от друга
      (`modeling` не знает `advisor` и наоборот) — сборкой это не проверяется,
      см. `spec/README.md`, «Верификация»
- ✅ CI ([§10.4](../archi-creator.md#104-ci)): сборка → юнит → фронтенд → образ.
      Golden-file, интеграционные и Playwright не заведены пустыми: зелёная
      галочка над ненаписанным тестом хуже отсутствующей. Приходят на этапах
      1, 2 и 4 соответственно — места названы комментариями в `ci.yml`
- ✅ Gate спеки в CI: `check-links.py`, `check-traceability.py`,
      `render-requirements-index.py --check` — каждый возвращает ненулевой код
      при первом нарушении
- ✅ Правило conventional commits и проверка сообщения:
      `tools/check-commit-message.py`, хук `.githooks/commit-msg`
      (CLAUDE.md, раздел «Коммиты»)
- ✅ Gate сообщений коммитов в CI:
      `tools/check-commit-message.py --range origin/main..HEAD` — хук живёт
      на машине разработчика и обходится `--no-verify`, гейт не обходится

**Выход этапа**

- ✅ `docker compose up -d` поднимает приложение, `/actuator/health` = UP —
      три контейнера `healthy`, Flyway применяет `V1`, фронтенд отдаётся
      с `/` и из jar
- ✅ Вход через Keycloak отдаёт JWT с ролью, приложение её видит —
      токен `administrator` открывает `/actuator/metrics` (200), токены
      `architect` и `viewer` получают 403, запрос без токена — 401 (FR-28)

Чего в этапе **нет** и это не недоделка: браузерный вход (страницу логина
открывает Keycloak, но SPA пока не ведёт на неё и токен не хранит —
клиент OIDC приходит вместе с реальным экраном этапа 3) и golden-file,
интеграционные и Playwright-шаги CI — их нечем наполнить до этапов 1, 2 и 4.

---

## Этап 1 — метамодель и кодек `.archimate`

**Критерий готовности (§12):** референсная модель проходит round-trip.
**Главный якорь:** `INV-IXC-005` — остальные существуют, чтобы он не сломался.

### 1.1 Метамодель (`archi-modeling-domain`)

- ✅ `ArchiType` как VO со шаблоном `^archimate:[A-Za-z]+$` — **не enum**:
      типы вне текущей фазы обязаны сохраняться (FR-03,
      [aggregates.yaml#ArchiType](../../spec/domain/modeling/aggregates.yaml))
- ✅ Каталог типов с фазами (FR-07, FR-08), вывод `Layer` из `archiType`
      с `OTHER` для незнакомых, реестр `xsi:type` ↔ внутренний тип
      (`ArchiTypeRegistry`, [§3.3](../backend.md#33-состав-модулей-бэкенда)).
      Три пункта плана слиты в один: это один класс, и `xsi:type` совпадает
      с внутренним типом буквально — реестр не переводит строки, а отвечает,
      что за тип перед ним
- ✅ Одиннадцать типов связей (FR-09): Composition, Aggregation, Assignment,
      Realization, Serving, Access, Influence, Triggering, Flow, Specialization,
      Association
- ✅ Матрица допустимых связей ArchiMate 3.2 таблицей-ресурсом (`INV-MDL-007`,
      FR-10); применяется к созданию, **не** к импорту
- ✅ Правила вложенности: какая связь подразумевается при помещении элемента
      в элемент (нужны на этапе 4 для FR-14, живут здесь)

### 1.2 Чтение (`archi-interchange-domain`)

Кодек лежит в доменном модуле: StAX — часть JDK, запрет enforcer'а не нарушается.

- ✅ VO: `ArchiId`, `DocumentOrder`, `RawXmlFragment`, `DocumentNode`,
      `ModelDocument`, `ContentHash`
      ([aggregates.yaml](../../spec/domain/interchange/aggregates.yaml))
- ✅ Порт `ArchiDocumentReader` и потоковая реализация на StAX (NFR-02:
      документ целиком в памяти не держим)
- ✅ Идентификаторы сохраняются буквально, не перегенерируются
      (`INV-IXC-001`, [`ADR-0002`](../../spec/adr/0002-dual-identity.md))
- ✅ Девять корневых папок с `folderType` + произвольная пользовательская
      вложенность (`INV-MDL-009`)
- ✅ Порядок узлов среди соседей фиксируется (`DocumentOrder`) — условие
      round-trip ([§3.4](../backend.md#34-кодек-archimate--ключевые-решения), п. 3)
- ✅ `bounds` относительно родителя, у детей `Group` — относительно группы
- ✅ Свойства `property key/value`, `documentation`, `viewpoint`
- ✅ Неизвестные узлы и атрибуты → `RawXmlFragment` с адресом родителя
      и позицией, `supported = false` (FR-03, `INV-IXC-001`)
- ✅ `targetConnections` восстанавливается из `sourceConnection`, порядок
      идентификаторов в атрибуте воспроизводится как есть
- ✅ Отказ на повреждённых данных независимо от режима (FR-50, `INV-IXC-007`):
      невалидный XML с номером строки и позиции, ссылка на несуществующий id,
      дубль `archiId`, связь без конца

Чтение и проверка повреждений — один проход и один таск: читатель видит каждую
ссылку и каждый id в момент разбора, отдельный проверяющий обходил бы дерево
второй раз. Дефекты собираются все, а не до первого (`CorruptDocumentTest`).

### 1.3 Запись (`archi-interchange-domain`)

- ✅ Порт `ArchiDocumentWriter`, детерминированная реализация `ArchiXmlWriter`
      (`INV-IXC-004`): порядок атрибутов из документа, LF, без метки времени,
      без зависимости от хеш-таблиц и локали; обход в порядке читателя;
      `RawXmlFragment` по адресу без нормализации; собственный стиль объекта
      (`fillColor`, `font`, `lineColor`) дословно (FR-21, `INV-IXC-005`).
      Четыре пункта плана — свойства одного класса, закрыты одним таском
- ⬜ Генератор новых `archi_id` в формате `id-<32 hex>`, как в Archi
      ([`ADR-0002`](../../spec/adr/0002-dual-identity.md))

### 1.4 Сессии и отчёты (домен, без хранения)

- ⬜ `ImportSession` со state machine `RECEIVED → PARSED → VALIDATED → APPLIED`,
      отказ из любого нетерминального (`INV-IXC-002`)
- ⬜ Идемпотентность по `Idempotency-Key` + `sourceHash` (`INV-IXC-003`)
- ⬜ `ImportFinding` с `severity`, `code`, `archiId`, `xmlLine`
- ⬜ `strictMode`: `ERROR` отклоняет только в строгом режиме (`INV-IXC-007`,
      FR-49, [§8.3](../backend.md#83-строгость-импорта))
- ⬜ `ExportJob`, `LossReport`, `LossEntry`; для `ARCHIMATE` список потерь
      обязан быть пуст (`INV-IXC-006`)

### 1.5 Фикстуры и тесты

Набор из [§9.1](../backend.md#91-golden-file-тесты-round-trip--критический-контур):

- ⬜ `hamkorbank_as_is.archimate` — копия `docs/Hamkorbank_AS_IS_strict.archimate`
      (~400 элементов, 12 представлений, вложенные группы, кириллица)
- ⬜ `capability_and_location.archimate` — элементы вне фазы 1
- ⬜ `nested_containment.archimate` — `Group` → `DiagramObject`, относительные
      координаты
- ⬜ `all_relationship_types.archimate` — 11 типов связей и `Junction`
- ⬜ `styled_objects.archimate` — собственные `fillColor`, `font`, `lineColor`
- ⬜ `unknown_extension.archimate` — неизвестные узлы и атрибуты
- ⬜ Хелпер `assertXmlEquivalent`: нормализация форматирования, строгий
      контроль порядка узлов, id и содержимого

Тесты (имена — контракт из таблиц «Маппинг на тесты»):

- ✅ `ArchiReaderTest#unknownNodeIsPreservedAsRawFragment` (`INV-IXC-001`)
- ⬜ `ImportSessionStateMachineTest#applyIsAllowedOnlyFromValidated` (`INV-IXC-002`)
- ⬜ `ImportIdempotencyTest#sameKeyReturnsExistingSession` (`INV-IXC-003`)
- ✅ `ArchiWriterTest#writingTwiceProducesIdenticalBytes` (`INV-IXC-004`)
- ✅ `ArchiCodecTest#documentSurvivesWriteReadCycle` (`INV-IXC-005`)
- ⬜ `RoundTripGoldenFileTest#hamkorbankAsIsSurvivesRoundTrip` (`INV-IXC-005`, NFR-05)
- ⬜ `RoundTripGoldenFileTest#opaqueNodesSurvive` (FR-03)
- ⬜ `StrictImportTest#errorRejectsOnlyInStrictMode`,
      `#corruptedFileIsRejectedInBothModes` (`INV-IXC-007`)
- ✅ `RelationMatrixTest#forbiddenRelationIsRejectedOnCreate`,
      `#importedViolationIsReportedNotRejected` (`INV-MDL-007`)
- ✅ Параметризованные тесты матрицы по тройкам «источник — цель — тип связи»
      ([§9.2](../backend.md#92-юнит-тесты))
- ✅ Правила вложенности: подразумеваемая связь на каждую пару типов
- ⬜ Производительность: файл на 5 000 строк XML ≤ 3 с (NFR-02)

**Выход этапа**

- ⬜ Round-trip референсной модели зелёный, регрессия блокирует сборку (NFR-05)
- ⬜ Gate round-trip включён в CI отдельным шагом

---

## Этап 2 — хранение, API, блокировки, версии

**Критерий готовности (§12):** импорт → БД → экспорт без потерь через API.

### 2.1 Схема БД (Flyway, `archi-bootstrap`)

Сущности и ограничения — [§4.1](../database.md#41-сущности),
[§4.2](../database.md#42-индексы-и-ограничения).

- ⬜ `model`, `model_folder`, `element`, `relationship`, `view`, `view_node`,
      `view_edge`, `element_property`, `relationship_property`, `view_property`,
      `model_version`, `model_lock`, `ai_audit_log`
- ⬜ `UNIQUE (model_id, archi_id)` на всех сущностях с `archi_id` (`INV-MDL-001`)
- ⬜ `INDEX (model_id, archi_type)`, `INDEX (view_id, parent_id, sort_order)`
- ⬜ `relationship.source_element_id/target_element_id` — `ON DELETE RESTRICT`
      (`INV-MDL-004`)
- ⬜ `sort_order` разреженный, шаг 1000, во всех таблицах с порядком
      (`INV-MDL-005`, [§11.6](../database.md#116-что-нужно-сделать-уже-на-этапе-1))
- ⬜ `raw_xml` заполняется для всего неподдержанного (FR-03) — иначе цикл
      БД → Git → БД потеряет данные
- ⬜ `model_version.snapshot` — `bytea` gzip, nullable; `git_sha` nullable
- ⬜ Таблица ключей идемпотентности (`INV-MDL-003`) и таблица `import_session`
      с находками

### 2.2 Домен (`archi-modeling-domain`)

- ⬜ Агрегаты `ArchitectureModel`, `View`, `ModelLock`, `ModelVersion`,
      `Workspace`
- ⬜ Сущности `ModelFolder`, `Element`, `Relationship`, `ViewNode`, `ViewEdge`
- ⬜ VO: идентификаторы на `UUIDv7` (генерация на стороне приложения),
      `SortOrder`, `Bounds`, `Bendpoint`, `PropertyEntry`, `StyleOverride`,
      `RawXml`, `EditorIdentity`
- ⬜ State machine модели: `ACTIVE → DELETED → PURGED`, прямой
      `ACTIVE → PURGED` запрещён (`INV-MDL-002`)
- ⬜ State machine блокировки: `HELD → RELEASED | EXPIRED`; просроченная
      блокировка не даёт прав (`INV-MDL-006`)
- ⬜ Инварианты как проверки домена: `INV-MDL-001`, `004`, `005`, `007`,
      `008`, `009`, `010`
- ⬜ Доменные события `ModelVersionCommitted`, `ModelLockReleased`,
      `ModelDeleted` ([events.yaml](../../spec/domain/modeling/events.yaml)),
      публикация `direct` после коммита (`ADR-0003`, временно)

### 2.3 Персистентность (`archi-*-adapter-persistence`)

- ⬜ JPA-сущности и маппинг на домен (домен о JPA не знает — enforcer)
- ⬜ Чтение дерева модели одним запросом ([§4.3](../database.md#43-стратегия-хранения))
- ⬜ Payload представления по требованию, сборка дерева одним запросом
- ⬜ Оптимистичная блокировка `version` на агрегатах (NFR-07)
- ⬜ Репозитории портов: `ModelRepository`, `ElementRepository`,
      `RelationshipRepository`, `FolderRepository`, `ViewRepository`,
      `ModelLockRepository`, `ModelVersionRepository`, `ImportSessionRepository`

### 2.4 Сценарии (`archi-*-application`)

- ⬜ `UC-MDL-001` — создать, переименовать, удалить модель; девять корневых
      папок сразу при создании (`FolderTreeInitializer`)
- ⬜ `UC-MDL-002` — создать элемент, разместить на представлении
- ⬜ `UC-MDL-003` — создать связь, `SuggestRelationTypes` по матрице
- ⬜ `UC-MDL-004` — сохранить модель как версию, `LabelVersion`,
      `RollbackToVersion`; порты `SnapshotWriter`, `GitPublisher` (необязателен)
- ⬜ `UC-MDL-005` — захват, освобождение и принудительное снятие блокировки
- ⬜ `UC-MDL-006` — переместить, переименовать, создать папку, удалить
- ⬜ `UC-IXC-001` — импорт файла: применение одной транзакцией, отчёт
- ⬜ `UC-IXC-002` — экспорт модели в `.archimate` по версии
      (`ModelDocumentAssembler`, `VersionSnapshotReader`)
- ⬜ `LockGuard` как общий порт: любая запись требует `HELD`-блокировки автора
      (`INV-MDL-006`)
- ⬜ Идемпотентность команд изменения: тот же ключ и то же тело → первый
      результат, другое тело → `409` (`INV-MDL-003`)

### 2.5 REST и контракты (`archi-*-adapter-rest`)

- ⬜ Наполнить `paths` в трёх `spec/contracts/*/rest-api.openapi.yaml` — по
      одному endpoint'у на экспонированный use case со ссылкой на `UC-*`
      (сейчас `paths: {}`, это осознанный скелет)
- ⬜ Эндпоинты из [§5](../backend.md#5-rest-api): `/models`, `/models/{id}`,
      `/models/import`, `/models/{id}/export`, `/models/{id}/lock`,
      `/models/{id}/versions*`, `/models/{id}/validate`, `/elements*`,
      `/relationships*`, `/views/{id}`, `/views/{id}/layout`,
      `/views/{id}/nodes`, `/view-nodes/{id}`, `/metamodel/*`
- ⬜ DTO `ModelTree`, `ViewPayload`, `LayoutPatch`, `ElementPatch`,
      `VersionInfo`, `LockInfo`, `Finding` ([§5.1](../backend.md#51-ключевые-dto))
- ⬜ Problem Details RFC 7807 с полем `code`, `@RestControllerAdvice`
      ([§8.1](../backend.md#81-формат-ответа))
- ⬜ Таблица сценариев ошибок [§8.2](../backend.md#82-основные-сценарии):
      `409` блокировка и конкурентное сохранение, `422` недопустимая связь,
      `409` удаление элемента со связями, `400` битый XML, `422` строгий импорт
- ⬜ Отчёт валидации метамодели без ИИ: `GET /models/{id}/validate` → `Finding[]`
      (FR-10 для импортированных нарушений)
- ⬜ Экспорт каталога в CSV ([§5.2](../backend.md#52-экспорт-каталога-в-csv),
      FR-45): zip из `elements.csv` и `relations.csv`, UTF-8 **с BOM**,
      разделитель `;`, параметр `sep`, динамические столбцы свойств по частоте
      ключа, фильтр `folder=<id>`, доступно `VIEWER`

### 2.6 Доступ

- ⬜ Маппинг ролей на операции по таблице `security.authorization`
      в [`spec/nfr/modeling.yaml`](../../spec/nfr/modeling.yaml) (FR-28)
- ⬜ Чужую блокировку снимает только `ADMIN`; `PurgeModel`, `RestoreModel` —
      только `ADMIN`
- ⬜ **Заблокировано спекой:** ACL на уровне модели (FR-29) не имеет ни
      инварианта, ни use case'а. Сначала завести `INV-MDL-011` и `UC-MDL-007`,
      потом писать код — иначе механизм доступа окажется вне нормативной части
      (см. [«Журнал»](#журнал), п. 8)

### 2.7 Версии и ретеншен

- ⬜ Версия на каждое сохранение: автор, время, комментарий, монотонный
      `versionNo` (`INV-MDL-010`, FR-06)
- ⬜ Метка версии (`label`) — релиз архитектуры, снимок не удаляется (FR-48)
- ⬜ Правило ретеншена ([§4.4](../database.md#44-хранение-и-очистка-версий),
      FR-46): последние 30 дней, помеченные, последняя за календарный месяц
- ⬜ Фоновая задача очистки написана, но **выключена** до этапа 7a: без Git
      снимок — единственная копия (FR-47, `SnapshotRetentionTest#purgeIsDisabledWithoutGitBinding`)

### 2.8 Наблюдаемость

- ⬜ Метрики из `spec/nfr/*.yaml`: `*_usecase_requests_total`,
      `*_usecase_duration_seconds`, `*_usecase_errors_total` с label `error_code`
      = код инварианта, `modeling_lock_wait_seconds`,
      `modeling_validation_findings_total`
- ⬜ Actuator: `/actuator/health`, `/metrics`, `/prometheus`
      ([§8.4](../backend.md#84-логирование-и-наблюдаемость))
- ⬜ Структурированные логи JSON с `traceId`, пользователем и id модели,
      маскирование PII
- ⬜ Алерты `HighErrorRateSaveModel`, `SlowOpenModelP95`, `ConcurrentSaveConflicts`

### 2.9 Тесты этапа

- ⬜ `ModelPersistenceIT#duplicateArchiIdViolatesUniqueConstraint`
- ⬜ `ModelLifecycleIT#purgeRequiresDeletedState`
- ⬜ `IdempotencyIT#sameKeyDifferentBodyReturns409`
- ⬜ `ElementDeletionIT#restrictViolationReturns409`
- ⬜ `ConcurrentSaveIT#secondWriterGets409` (NFR-07)
- ⬜ `ValidationReportIT#importedViolationsAppearInReport`
- ⬜ `ModelImportIT#folderTreeSurvivesRoundTrip`
- ⬜ `ImportIT#applyTwiceIsRejected`, `#retryDoesNotCreateSecondModel`,
      `#corruptedFileIsRejectedInBothModes`
- ⬜ `ExportJobTest#exportPinsSourceVersion` (`INV-IXC-008`)
- ⬜ Ролевой доступ: `VIEWER` → `403` на запись, `ARCHITECT` → `200`
- ⬜ Testcontainers `postgres:16` и `keycloak:26`
      ([§9.3](../backend.md#93-интеграционные-тесты-testcontainers))
- ⬜ Нагрузочный профиль NFR-03: p95 ≤ 300 мс на операциях кроме ИИ и импорта
      (у требования нет якоря намеренно — проверяет профиль)

**Выход этапа**

- ⬜ Цикл «импорт файла → сохранение в БД → экспорт через API» даёт исходный
      файл, сравнение `assertXmlEquivalent` зелёное

---

## Этап 3 — канва, визуальный язык, дерево

**Критерий готовности (§12):** реальная модель открывается и редактируется.
**Правила:** `UI-001`, `UI-002`, `UI-006`…`UI-013`, `UI-014`…`UI-018`, `UI-020`, `UI-021`.

### 3.1 Каркас приложения

- ⬜ Структура: канва, палитра, дерево, панель свойств, статус-строка —
      четырёхпанельный вариант A ([§6.5](../frontend.md#65-компоновка-главного-экрана--решение-принято),
      `ADR-0004`)
- ⬜ Состояние: Zustand для документа модели, TanStack Query для серверных данных
- ⬜ Русский интерфейс, строки в ресурсах (NFR-08, `UI-021`) — вынесены сразу,
      а не «потом локализуем»
- ⬜ Макеты как референс: `open docs/mockups/index.html` — дерево, карточка
      элемента и редактирование там собраны на реальных данных

### 3.2 Токены (`UI-010`, FR-20)

- ⬜ Генерация CSS-переменных из [`spec/ui/design-tokens.yaml`](../../spec/ui/design-tokens.yaml)
      на сборке — файл спеки единственный источник
- ⬜ Те же значения доступны серверному писателю (этап 5a), без второй копии
- ⬜ Статическая проверка: литералов цвета в компонентах канвы нет
- ⬜ Тема одна, переключателя нет (`ADR-0011`): графитовый корпус, светлый холст

### 3.3 Общая геометрия фигур (`UI-019`, FR-42)

- ⬜ `shapes.json` по контракту [`spec/ui/shapes-contract.yaml`](../../spec/ui/shapes-contract.yaml):
      `outline`, `corner_icon`, `label_box`, `radius_token`, `layer`
- ⬜ SVG-спрайт иконок, один для канвы и сервера
- ⬜ Запись для каждого `ArchiType` фазы 1 обязательна; типы фаз 2 и 3 —
      силуэт-заглушка (`UI-020`, FR-03)
- ⬜ Фигуры рисуются **инлайн-SVG внутри узла React Flow**, не CSS
      (`ADR-0007`: CSS-силуэт серверный писатель воспроизвести не может)

### 3.4 Канва

- ⬜ Рендер представления из `ViewPayload`: узлы, рёбра, вложенность, геометрия
      относительно родителя
- ⬜ Отрисовка узла по [§6.3](../frontend.md#63-правила-отрисовки-узла): иконка
      14×14, имя в 1–2 строки с обрезкой на третьей, заливка = токен слоя
- ⬜ Приоритет собственного стиля объекта над токеном (`UI-011`, FR-21)
- ⬜ Выделение двойной обводкой, не заливкой — иначе теряется код слоя
- ⬜ Связи по нотации ([§6.4](../frontend.md#64-связи)): наконечники, пунктир,
      ортогональная маршрутизация, скругление углов, подпись
- ⬜ Zoom/pan, сетка, привязка
- ⬜ Неподдержанный объект видно и можно двигать, семантически не редактируется
      (`UI-020`, FR-03)
- ⬜ Вкладки открытых представлений (`UI-008`, FR-18)

### 3.5 Дерево модели (`UI-012`, `UI-013`, FR-30, FR-31)

- ⬜ Папки из файла с исходной вложенностью, рекурсивные счётчики по ветвям
- ⬜ Три режима группировки: папки / типы / плоский список
- ⬜ Живой поиск с подсветкой, авторазвёрткой совпадений и пересчётом
      счётчиков; ⌘F/Ctrl+F, Esc сбрасывает
- ⬜ Фильтры: «Все», «На представлении», «Не размещены», «Замечания»;
      комбинируются с поиском
- ⬜ Маркеры строки: не размещён, есть находка, число представлений
- ⬜ Виртуализация: не более 50 строк в ветви, остальные по «показать ещё»
- ⬜ Отклик поиска ≤ 100 мс на 2 000 элементов (NFR-01)
- ⬜ Двусторонняя синхронизация выделения с холстом и панелью свойств

### 3.6 Правая панель

- ⬜ Вкладка «Свойства» (`UI-007`, FR-17): имя, документация, свойства
      key/value, слой, тип, идентификатор; правит объект модели, не представление
- ⬜ Вкладка «Описание» (`UI-014`, FR-32, `ADR-0013`): заголовок, документация,
      участие в связях с русскими названиями типов (первые 7 + «показать все N»),
      анализ влияния, свойства, список представлений
- ⬜ Анализ влияния по исходящим Serving, Realization, Triggering, Flow, Access:
      прямые, второй уровень, вложенные ([§6.7](../frontend.md#67-вкладка-описание-правая-панель))
- ⬜ Навигация кликом по связи: раскрыть ветви, прокрутить, перерисовать карточку
- ⬜ Поведение по роли (`UI-015`, FR-33): `VIEWER` — «Описание» по умолчанию,
      «Свойства» только для чтения, палитра и панель инструментов скрыты,
      место отдано дереву

### 3.7 Палитра и размещение

- ⬜ Палитра типов из `GET /metamodel/elements`, переключается на слой
      выбранного элемента
- ⬜ Drag-and-drop с палитры и из дерева на холст (`UI-002`, FR-12)

### 3.8 История изменений (`UI-017`, FR-35)

- ⬜ Стек операций парами `apply`/`revert` со списком затронутых элементов
- ⬜ `Ctrl/Cmd+Z`, `Ctrl/Cmd+Shift+Z`, `Ctrl+Y`, кнопки панели инструментов
      с названием конкретной операции в подсказке
- ⬜ Панель истории: список, текущая позиция, точка сохранения, переход кликом
- ⬜ Пометка «изменён» считается как разница позиции и точки сохранения,
      отмена снимает пометки сама; отмена *за* точку сохранения тоже
      несохранённое изменение
- ⬜ Счётчик несохранённых в статус-строке, кнопка «Сохранить» только при
      наличии изменений
- ⬜ **Регрессионный тест на дефект макета** ([§6.9](../frontend.md#69-история-изменений)):
      клик внутри дерева не прокручивает строку; прокрутка только когда
      выделение приходит извне. Из кода причина не видна, регрессия вероятна

### 3.9 Редактирование дерева (`UI-016`, `UI-018`, FR-34, FR-36)

- ⬜ Переименование по `F2`/двойному клику прямо в строке
- ⬜ Перемещение перетаскиванием и «Переместить в папку…» с поиском по полному
      пути; работает для множественного выделения
- ⬜ Создание вложенной папки, сразу в режиме переименования
- ⬜ Удаление с предварительным показом числа затрагиваемых связей
- ⬜ Множественное выделение: `Ctrl/Cmd` — переключение, `Shift` — диапазон;
      правая панель переключается на сводку с групповыми операциями
- ⬜ Системные папки формата не удаляются, сообщение объясняет почему
      (`INV-MDL-009`)
- ⬜ Контекстное меню по роли: `VIEWER` получает только неизменяющие пункты
- ⬜ Перетаскивание меняет папку, но **не** порядок внутри папки — порядок
      значим для round-trip

### 3.10 Тесты этапа

- ⬜ `e2e/element-identity.spec.ts` (`UI-001`), `e2e/palette-drop.spec.ts`,
      `e2e/tree-drop.spec.ts` (`UI-002`)
- ⬜ `e2e/properties-panel.spec.ts` (`UI-007`), `e2e/view-tabs.spec.ts` (`UI-008`)
- ⬜ `e2e/model-tree.spec.ts` (`UI-012`), `e2e/description-tab.spec.ts` (`UI-014`)
- ⬜ `e2e/role-viewer.spec.ts`, `e2e/role-architect.spec.ts` (`UI-015`)
- ⬜ `e2e/tree-editing.spec.ts` (`UI-016`)
- ⬜ `visual/shape-gallery` против эталона Archi (`UI-009`), `visual/tokens.spec.ts`
      (`UI-010`), `visual/style-override.spec.ts` (`UI-011`)
- ⬜ `perf/tree-search.bench.ts`, `perf/canvas-pan.bench.ts` на референсной
      модели (`UI-013`, NFR-01: pan/zoom 60 fps, открытие представления ≤ 1 с)

---

## Этап 4 — умные связи, вложенность, авторазметка, паритет

**Критерий готовности (§12):** сценарии E2E [§9.4](../frontend.md#94-e2e-playwright) зелёные.

- ⬜ Умное рисование связи (`UI-003`, FR-13): тянем от границы, показывается
      список допустимых типов для пары, вероятный выбран по умолчанию; список
      берётся из `GET /metamodel/relations`
- ⬜ Недопустимый тип не предлагается вовсе; попытка создать — `422`
      с причиной и допустимыми вариантами (`INV-MDL-007`)
- ⬜ Вложенность (`UI-004`, FR-14): помещение элемента внутрь другого создаёт
      подразумеваемую связь (composition/assignment по типу пары), дети
      перемещаются вместе с родителем
- ⬜ Авторазметка (`UI-005`, FR-15): раскладка по слоям — бизнес сверху,
      технологии снизу, минимизация пересечений; `POST /views/{id}/auto-layout`
      со стратегиями `layered`/`hierarchy`/`compact`
- ⬜ Авторазметка детерминирована и не даёт наложений ([§9.2](../backend.md#92-юнит-тесты))
- ⬜ Сохранение геометрии после перемещений: `PUT /views/{id}/layout`

### Паритет с Archi (`UI-006`, FR-16)

Тринадцать пунктов, по E2E-сценарию на каждый (`e2e/editing-parity.spec.ts`).
Undo/redo и zoom/pan заведены на этапе 3 — здесь они закрываются на канве,
а не только в дереве.

- ⬜ Undo/redo на операциях канвы, единый стек с деревом (`UI-017`)
- ⬜ Множественный выбор и рамка выделения
- ⬜ Копирование и вставка (в том же и в другом представлении)
- ⬜ Выравнивание и направляющие
- ⬜ Точки перегиба связей (bend points) — пишутся в `ViewEdge.bendpoints`
- ⬜ Zoom/pan на канве
- ⬜ Сетка и привязка
- ⬜ Группы (`Group`) с детьми в координатах группы (`ViewNodeKind.GROUP`)
- ⬜ Заметки (`Note`)
- ⬜ Соединители (`Junction`) — узел без `elementId` (`INV-MDL-008`)
- ⬜ Переопределение стиля отдельного объекта: цвет заливки, шрифт
      (`StyleOverride`, сохраняется при экспорте — `INV-IXC-005`)
- ⬜ Навигатор / структура модели
- ⬜ Поиск по модели и «где используется»
- ⬜ `e2e/smart-relation.spec.ts`, `e2e/nesting.spec.ts`,
      `e2e/auto-layout.spec.ts`, `e2e/editing-parity.spec.ts`
- ⬜ Сценарии §9.4: импорт → открытие → все объекты отрисованы; недопустимая
      связь не предлагается; цепочка undo/redo из пяти операций; экспорт →
      обратный импорт

---

## Этап 5 — ИИ-помощник

**Критерий готовности (§12):** четыре функции работают, аудит пишется.
**Граница:** помощник **не изменяет модель** (FR-22, `INV-ADV-004`) — любой
результат это текст, ссылки и подсказки.

### 5.1 Домен (`archi-advisor-domain`)

- ⬜ `AdvisorSession` со state machine `REQUESTED → STREAMING → COMPLETED`,
      `REJECTED` только из `REQUESTED` — отказ принимается до расхода токенов
      (`INV-ADV-002`)
- ⬜ `TokenBudget` по паре (`workspaceId`, `period`), `OK → WARNING → EXHAUSTED`;
      расход не уменьшается, возврат только повышением лимита (`INV-ADV-006`)
- ⬜ `Finding` с обязательной ссылкой на объект модели (`INV-ADV-008`, FR-23)
- ⬜ `AuditEntry` — ровно одна на сессию, включая отклонённые (`INV-ADV-001`,
      FR-27)
- ⬜ `ModelContext` как анти-коррупционный слой с обязательным флагом
      `truncated` (`INV-ADV-007`)
- ⬜ `ModelPlan`, `AdvisorAnswer`, `ElementRef`, `TokenUsage`, `BudgetPeriod`
- ⬜ Результат помощника структурно не может содержать команд изменения модели
      (`INV-ADV-004`)

### 5.2 Сценарии и адаптеры

- ⬜ `UC-ADV-001` — ревью модели, поток `Finding`
- ⬜ `UC-ADV-002` — `ExplainElement`, `PlanModel`, `GenerateDocumentation`
- ⬜ Порты `ModelContextReader`, `LlmPort`, `TokenBudgetPort`, `AuditWriter`
- ⬜ Построение контекста ([§7.3](../backend.md#73-контекст-запроса)): компактный
      JSON; при превышении лимита — подграф (для подсказки элемент и соседи
      на два шага, для ревью обработка по слоям с агрегацией)
- ⬜ Клиент Anthropic API, модель `claude-sonnet-5` параметром конфигурации
- ⬜ Потоковая отдача SSE, первые токены ≤ 3 с (NFR-04, поле `firstTokenAt`)
- ⬜ Структурированные результаты через tool use со схемой — `Finding[]`
      и `ModelPlan` приходят валидными без парсинга свободного текста
- ⬜ Ключ Anthropic только на сервере, в браузер не попадает (NFR-06,
      `INV-ADV-009`)
- ⬜ Эндпоинты `/ai/review`, `/ai/explain`, `/ai/plan`, `/ai/document`

### 5.3 Лимиты и видимость ([§7.5](../backend.md#75-лимиты-расходов))

- ⬜ Месячный бюджет `ai_monthly_token_limit`: 80 % — предупреждение
      администратору, 100 % — `429` с датой сброса (FR-51, `INV-ADV-005`)
- ⬜ Ограничение частоты на пользователя, `429` + `Retry-After` (FR-52)
- ⬜ Верхняя граница контекста: сужение до подграфа, иначе `413`
      с предложением выбрать представление (`INV-ADV-007`)
- ⬜ Не более одного активного запроса на пользователя: повторное нажатие
      отменяет предыдущий поток
- ⬜ `workspace.ai_enabled = false` убирает панель и закрывает `/ai/**` (`403`)
- ⬜ Расход администратору в разрезе месяца, пользователя и функции (FR-53)
- ⬜ События `AdvisorSessionCompleted`, `TokenBudgetThresholdReached`,
      `TokenBudgetExhausted`

### 5.4 Интерфейс помощника

- ⬜ Панель помощника с потоковым выводом
- ⬜ Переход к элементу из находки: выделение на канве и в дереве
- ⬜ Фильтрация палитры по типам из `ModelPlan` (FR-25)
- ⬜ Кнопка «Собрать документацию» в карточке элемента (FR-26)
- ⬜ Исчерпанный бюджет и недоступность Anthropic (`503`) не ломают редактор

### 5.5 Тесты

- ⬜ Тесты по таблице «Маппинг на тесты» в
      [`advisor/invariants.md`](../../spec/domain/advisor/invariants.md) —
      `INV-ADV-001`…`INV-ADV-009`
- ⬜ `/ai/**` при `ai_enabled = false` → `403`; Anthropic мокируется на уровне
      HTTP ([§9.3](../backend.md#93-интеграционные-тесты-testcontainers))
- ⬜ Построение контекста: размер, усечение, выбор подграфа ([§9.2](../backend.md#92-юнит-тесты))
- ⬜ `e2e`: ревью выдаёт находки, переход к элементу выделяет его на канве

---

## Этап 5a — серверный экспорт представлений

**Критерий готовности (§12):** экспорт совпадает с канвой в пределах допуска
визуальных тестов.

- ⬜ `SvgWriter` в `archi-interchange-*`: печатает `ViewPayload` в SVG по тому
      же `shapes.json` и тем же токенам, что канва (FR-42, `UI-019`)
- ⬜ Интерфейс `Rasterizer`, реализация на Apache Batik → PNG @1x/@2x/@4x;
      интерфейс нужен ради возможной замены на `resvg`
- ⬜ Шрифты (Archivo, IBM Plex Mono) в образе; жадный перенос подписи по ширине
      считает сервер — единственная честная зона расхождения с браузером
- ⬜ Параметры `fmt`, `scale`, `outline`, `background`
      ([§6.10](../backend.md#610-экспорт-представлений))
- ⬜ `outline=true` переводит подписи в кривые для получателей без наших шрифтов
- ⬜ Границы: представление целиком по содержимому с полями; выше
      4 000 × 4 000 при `scale=4` — отказ с предложением уменьшить плотность
- ⬜ Пакетный экспорт `GET /models/{id}/views/export` → zip, имена файлов —
      слаги представлений (та же функция, что в §11.2)
- ⬜ Доступно роли `VIEWER` (FR-41)
- ⬜ `ExportJob` прибит к версии (`INV-IXC-008`), отчёт о потерях непуст для
      SVG и PNG (`INV-IXC-006`)
- ⬜ `ExportJobTest#renderedJobAlwaysHasLossReport`
- ⬜ Сверка канвы и экспорта: снимок канвы против серверного PNG на наборе
      фикстур; расхождение выше допуска — падение сборки ([§9.4](../frontend.md#94-e2e-playwright))
- ⬜ `BatchViewExportIT#allImagesComeFromOneVersion`

---

## Этап 6 — фаза 2 метамодели: Motivation и Strategy

**Критерий готовности (§12):** `Capability` редактируется, а не только сохраняется.

- ⬜ Каталог типов расширен слоями Motivation и Strategy (FR-08)
- ⬜ Матрица допустимых связей дополнена парами новых типов (`INV-MDL-007`)
- ⬜ Записи в `shapes.json` для новых типов вместо силуэта-заглушки
      (токены слоёв `motivation` и `strategy` уже есть)
- ⬜ Палитра, дерево и панель свойств работают с новыми типами наравне
- ⬜ Фикстура `capability_and_location.archimate` переходит из «непрозрачные
      объекты» в «поддержанные» **без** поломки round-trip — объект, ставший
      поддержанным, обязан писаться так же, как читался
- ⬜ `Layer.PHYSICAL` и `IMPLEMENTATION` остаются фазой 3 и продолжают
      обрабатываться по FR-03

---

## Этап 6a — экспорт в Open Exchange Format

**Критерий готовности (§12):** выгруженный файл валиден по XSD и открывается
сторонним инструментом; отчёт о потерях полон.
**Импорта из OEF нет** (`ADR-0008`) — не предлагать.

- ⬜ Официальная XSD `http://www.opengroup.org/xsd/archimate/3.0/` в ресурсах
      сборки
- ⬜ `OefWriter` по таблице соответствий [§3.5](../backend.md#35-экспорт-в-open-exchange-format):
      папки → `organizations`/`item`, свойства → `propertyDefinitions` +
      `propertyDefinitionRef`, `name`/`documentation` с `xml:lang="ru"`,
      геометрия → `view`/`node`/`connection`, стиль → `style`
- ⬜ Проверка результата по XSD **перед отдачей**: невалидный результат не
      отдаётся, а считается ошибкой сервера (FR-43)
- ⬜ Отчёт о потерях (FR-44, `INV-IXC-006`): `raw_xml` неподдержанных сущностей
      не переносится вовсе, вложенность и точки перегиба переносятся частично,
      тип корневой папки в OEF не выражается
- ⬜ Фикстуры `oef_export_*.xml`: валидность по XSD, ожидаемые объекты на месте,
      отчёт содержит ожидаемые позиции
- ⬜ `OefWriterTest#rawFragmentsAreListedAsLosses`,
      `OefExportIT#lossReportListsRawFragments`
- ⬜ Ручная проверка: файл открывается сторонним инструментом

---

## Этап 7a — экспорт в Git

**Критерий готовности (§12):** повторный экспорт без правок даёт побайтово тот
же результат; цикл БД → Git → БД проходит без потерь.

### 7a.0 Блокирующее решение

- ⬜ Закрыть [`ADR-0003`](../../spec/adr/0003-event-publishing.md): `direct`
      для Git-выгрузки не годится — окно потери события ломает правило очистки
      снимков (FR-47). Нужен `transactional_outbox`. Решение принимается
      **здесь**, чтобы не заводить outbox за шесть этапов до первого потребителя

### 7a.1 Формат (`INV-IXC-011`, `ADR-0006`)

- ⬜ Раскладка [§11.2](../backend.md#112-формат-разложенный-yaml):
      `model.yaml`, `folders.yaml`, файл на элемент, на связь, на представление
- ⬜ Имя файла `<слаг>.<8 hex от id>.yaml`, слаг транслитерируется в латиницу
- ⬜ Принадлежность папке — **поле** `folder`, а не каталог; каталоги
      `elements/<слой>/` только чтобы не держать 400 файлов в одной директории
- ⬜ Порядок — разреженное `position`, шаг 1000
- ⬜ Неподдержанные сущности — поле `raw_xml` блочным скаляром (иначе цикл
      БД → Git → БД теряет данные)
- ⬜ Комментарий с именами концов в заголовке файла связи
- ⬜ `format_version` в `model.yaml`
- ⬜ Детерминированный писатель: фиксированный порядок ключей, сортировка
      свойств, LF, без YAML-якорей и переносов по ширине (`INV-IXC-004`)
- ⬜ `.gitattributes`: для `models/**` текстовое слияние отключено (`INV-IXC-010`)
- ⬜ Сгенерированный `.archimate` в репозиторий **не** кладётся; вместо него
      CI-задача, собирающая его артефактом на теге

### 7a.2 Механика

- ⬜ Агрегат `GitBinding` (`CONFIGURED`/`UNREACHABLE`/`DISABLED`), настройки
      в рабочем пространстве, `SecretRef` + `SecretResolver` — токен в домене
      не появляется
- ⬜ `GitRepositoryPort` на JGit без внешнего бинарника; bare-клон кэшируется,
      `fetch` перед каждой операцией
- ⬜ `UC-IXC-003`: коммит на сохранение версии; автор — архитектор (соответствие
      Keycloak → git identity в настройках), сообщение — комментарий
      к сохранению, трейлеры `Archi-Model-Id` и `Archi-Version`
- ⬜ Только fast-forward: force push, rebase и удаление веток запрещены
      (`INV-IXC-009`)
- ⬜ Сдвинувшийся HEAD: сохранение в БД проходит, экспорт помечается как
      расхождение и требует 7b или 7c; чужой коммит не перезаписывается (FR-37)
- ⬜ `git_sha` пишется в `ModelVersion` (`ModelVersionUpdater`)
- ⬜ Один репозиторий на рабочее пространство, каталог на модель

### 7a.3 Ретеншен включается

- ⬜ Очистка снимков включается только для рабочего пространства с настроенным
      и доступным репозиторием (FR-47, `INV-MDL-010`)
- ⬜ Восстановление снимка из Git по `git_sha` прозрачно для пользователя
      (FR-46, `VersionSnapshotReader`)

### 7a.4 Тесты

- ⬜ `GitYamlWriterTest#writingTwiceProducesIdenticalBytes`,
      `#exportIsByteStable`, `#folderIsFieldNotDirectory`
- ⬜ `GitPublisherTest#pushIsAlwaysFastForward`
- ⬜ `GitExportIT#repeatedExportProducesNoDiff`, `#historyIsAppendOnly`
- ⬜ Фикстура `git_roundtrip`: цикл БД → YAML → БД → `.archimate` даёт исходный
      файл (FR-40, [§9.1](../backend.md#91-golden-file-тесты-round-trip--критический-контур))
- ⬜ `SnapshotRetentionIT#purgeIsDisabledWithoutGitBinding`

---

## Этап 7b — импорт состояния по ссылке

**Критерий готовности (§12):** отчёт об изменениях, применение одной транзакцией.

- ⬜ **Спека первой:** `INV-IXC-012` не имеет use case'а — завести `UC-IXC-004`
      и добавить endpoint в контракт `interchange`
- ⬜ Разрешение ссылки: коммит, тег, ветка
- ⬜ Чтение дерева репозитория в `ModelDocument`, сверка с текущим состоянием
- ⬜ Отчёт об изменениях **до** применения
- ⬜ Применение одной транзакцией как новая версия; текущая версия не правится
      на месте, промежуточных состояний в базе не возникает (`INV-IXC-012`)
- ⬜ Отказ не оставляет следов, кроме записи о неудачной попытке
- ⬜ `GitImportTest#applyIsAtomic`, `#appliedStateBecomesNewVersion`,
      `GitImportIT#reportPrecedesApply`

---

## Этап 7c — предложения через ветку и слияние по сущностям

**Критерий готовности (§12):** непересекающиеся правки сливаются сами,
конфликты разрешаются по полям на холсте.

- ⬜ **Спека первой:** `INV-IXC-010` без use case'а — завести `UC-IXC-005`
- ⬜ Трёхстороннее сравнение merge-base / наше / их по стабильным `archiId`
- ⬜ Таблица разрешений [§11.4](../backend.md#114-слияние-по-сущностям-а-не-по-тексту):
      поле изменено в одной ветви — берётся; в обеих по-разному — конфликт
      по полю; удалён/изменён — явный выбор; связи удаляются вместе с элементом
      и попадают в отчёт; геометрия представления берётся целиком с одной стороны;
      вставки в одну папку сохраняются оба набора с сортировкой по `position`
- ⬜ Переименование и перемещение не конфликтуют с правкой других полей —
      ключ идентификатор, а не имя и не путь
- ⬜ Текстовое слияние YAML запрещено процессом, а не соглашением (`INV-IXC-010`)
- ⬜ Предпросмотр результата на холсте: добавленное зелёным, удалённое красным,
      изменённое подсветкой, конфликты списком с выбором
- ⬜ Применение одной транзакцией как новая версия
- ⬜ `EntityMergeTest#disjointEditsMergeWithoutConflict`,
      `#sameFieldEditsProduceFieldLevelConflict`,
      `MergeRequestIT#conflictIsResolvedPerField`

---

## Сквозные задачи

Выполняются не на этапе, а постоянно.

- ⬜ Правка спеки → прогон `tools/check-links.py` и `tools/check-traceability.py`;
      правка требований → `tools/render-requirements-index.py`
      (`--check` в CI ловит ручную правку §2)
- ⬜ Новый якорь обязан указать требование, новое требование — якорь; удалённый
      якорь помечается `[DEPRECATED]`, номер не переиспользуется
- ⬜ Расхождение спеки и кода правится **сначала в спеке**
- ⬜ Каждый реализованный `INV-*` получает тест с `@DisplayName("INV-…: …")`
      и код инварианта в поле `code` ответа `problem+json` и в label `error_code`
- ⬜ `implemented` в [`spec/adr/decisions.yaml`](../../spec/adr/decisions.yaml)
      проставляется по факту, как у `ADR-0001`
- ⬜ Отметки выполнения в этом файле, находки — в «Журнал»
- ⬜ По закрытии этапа — `tools/render-depgraph.py` и обновление картинки
      в [`README.md`](../../README.md): граф зависимостей устаревает молча,
      и заметить это по коду нельзя (CLAUDE.md, «Граф зависимостей»)
- ⬜ Коммит на каждый закрытый таск: код, тест, правка спеки и отметка ✅
      здесь — одним коммитом (CLAUDE.md, «Один таск — один коммит»). Сообщение
      по conventional commits, `feat` и `spec` ссылаются на требование или якорь

---

## Журнал

Расхождения плана с фактом, найденные дефекты и отменённые варианты. Дописывать
по ходу: из кода причина обычно не видна, а регрессия вероятна.

### Расхождения внутри спеки, найденные при составлении плана (2026-09-29)

Нормативен `spec/`, но внутри него есть противоречия — их правка обязана
опередить код соответствующего этапа.

1. **Максимальная длина имени модели: 200 или 500.**
   [`aggregates.yaml#ArchitectureModel`](../../spec/domain/modeling/aggregates.yaml)
   требует `max: 500`, а [`UC-MDL-001`](../../spec/application/modeling/usecases/create-model.md)
   отказывает с `422` на имени длиннее 200. Решить до этапа 2, иначе валидация
   DTO и домена разойдутся.
2. **`UC-IXC-001`, шаг 4 пропускает `PARSED`:** написано
   «сессия переходит `RECEIVED → VALIDATED`», тогда как `INV-IXC-002` требует
   `RECEIVED → PARSED → VALIDATED`. Правится текст use case'а.
3. **`docs/database.md` §4.3 называет Git «этапом 2»**, тогда как §12 и весь
   остальной комплект относят выгрузку к 7a. `docs/` ненормативен, но ссылка
   вводит в заблуждение — поправить при работе над этапом 2.
4. **Имя фикстуры.** §9.1 называет `hamkorbank_as_is.archimate`, в репозитории
   лежит `docs/Hamkorbank_AS_IS_strict.archimate`. Фикстура этапа 1 — копия
   с именем из §9.1; расхождение зафиксировать, а не «исправить» молча
   в обе стороны.
5. **Устаревшие оговорки в `spec/`:** `nfr/*.yaml` и три
   `contracts/*/rest-api.openapi.yaml` утверждают, что каталог use case'ов пуст.
   С 2026-09-28 там одиннадцать сценариев. Снять оговорки при наполнении
   `paths` на этапе 2.
6. **`paths: {}` во всех трёх контрактах** — осознанный скелет: endpoint без
   ссылки на `UC-*` нарушает проверку целостности. Наполнение — часть этапа 2,
   по одному endpoint'у на use case.
7. **§3.3 описывает пакеты одного модуля** (`web`, `service`, `repository`, …) —
   взгляд до `ADR-0001`. Таблица переезда ответственностей по модулям уже
   есть в [`multi-module-pom.md`](multi-module-pom.md), повторять не нужно.
8. **Объявленные пробелы покрытия** (не дефекты плана, а известные дыры):
   FR-29 (ACL модели) без инварианта и use case'а — блокирует §2.6;
   NFR-03 без якоря (проверяет нагрузочный профиль); `INV-IXC-006`, `010`, `012`
   без use case'ов — их сценарии пишутся на этапах 6a, 7c, 7b.
9. **`ADR-0003` не принят.** `direct` выбран как временное состояние;
   `transactional_outbox` требуется к 7a. Решение — задача 7a.0.
10. **Размещение фронтенда решением не закрыто.** `ADR-0001` фиксирует
    17 проектов; отдельный модуль фронтенда — восемнадцатый, то есть изменение
    решения. Нужен `ADR-0016` либо каталог внутри `archi-bootstrap`.
11. **§10.2 обещает jar в `target/`** — фактически `archi-bootstrap/target/`.
    Учесть в `docker-compose.yml` и CI (уже отмечено в `multi-module-pom.md`).

### Отменённые варианты — не предлагать заново

Это принятые решения с обоснованием, а не недоделки:

| Что | Решение | Где обосновано |
|---|---|---|
| Импорт из OEF | Нет, только экспорт | `ADR-0008`, [§3.5](../backend.md#35-экспорт-в-open-exchange-format) |
| Импорт из CSV | Нет, только экспорт | `ADR-0009`, [§5.2](../backend.md#52-экспорт-каталога-в-csv) |
| Экспорт в PDF | Нет, печать из браузера | `ADR-0007`, [§6.10](../backend.md#610-экспорт-представлений) |
| Тёмная тема | Нет, переключателя не будет | `ADR-0011`, [§6.2](../frontend.md#62-токены) |
| Текстовое слияние YAML | Запрещено `.gitattributes` | `INV-IXC-010`, [§11.4](../backend.md#114-слияние-по-сущностям-а-не-по-тексту) |
| ИИ правит модель | Нет, только советует | FR-22, `INV-ADV-004` |
| Фигуры узлов на CSS | Нет, инлайн-SVG | `ADR-0007`, `UI-019` |
| Отдельный режим просмотра для `VIEWER` | Нет, вкладка «Описание» | `ADR-0013` |
| Git как источник правды | Нет, PostgreSQL | `ADR-0005`, [§11.1](../backend.md#111-роль-git-журнал-и-ревью-а-не-распределённое-редактирование) |

### Ход работы

<!-- Дописывать по мере выполнения: дата, этап, что сделано, что разошлось
     с планом, какой дефект найден и чем лечится. -->

- **2026-09-29.** План составлен по спеке разворота 2026-09-28. Этап 0 закрыт
  частично: есть только реактор Maven и проверки спеки.
- **2026-09-29.** Заведено правило коммитов: conventional commits с русским
  описанием, `tools/check-commit-message.py`, хук `.githooks/commit-msg`, гейт
  в CI отдельным чекбоксом этапа 0. Там же правило «один таск — один коммит»
  (CLAUDE.md, раздел «Коммиты»). Пять коммитов до этого правилу не подчиняются
  и переписываться не будут: история — журнал, а не предмет косметики.
  ADR не заводился: это соглашение о процессе, а не архитектурное решение,
  и номер `ADR-0016` остаётся за решением о размещении фронтенда (п. 10 выше).
- **2026-09-29. Дефект: Flyway не запускался.** В `archi-bootstrap` был только
  `org.flywaydb:flyway-core` — библиотека без автоконфигурации. В Spring Boot 4
  автоконфигурации разъехались по отдельным модулям, и `FlywayAutoConfiguration`
  живёт в `org.springframework.boot:spring-boot-flyway`. Приложение стартовало
  штатно, миграции лежали на месте, схема оставалась пустой — отказа не было
  нигде. Лечится добавлением модуля. **Регрессия вероятна** у любой другой
  автоконфигурации, подключённой «по библиотеке»: проверять не наличием
  зависимости, а фактом в логе (`Successfully applied N migration`).
- **2026-09-29. Порт 8080 на машине разработчика занят** сторонними контейнерами.
  Поэтому в `docker-compose.yml` порты вынесены в переменные
  (`ARCHI_APP_PORT`, `ARCHI_DB_PORT`, `ARCHI_KEYCLOAK_PORT`) со значениями
  по умолчанию из [§10.1](../archi-creator.md#101-состав): состав не меняется,
  а соседний проект не мешает поднять этот.
- **2026-09-29. Этап 0 закрыт.** Конфигурация и профили, миграция `V1`,
  ресурс-сервер с маппингом ролей, realm Keycloak, `docker-compose.yml`,
  каркас фронтенда внутри `archi-bootstrap` ([`ADR-0016`](../../spec/adr/0016-frontend-location.md)),
  ArchUnit на изоляцию контекстов и конвейер CI с тремя гейтами. Выход
  проверен вживую, не на бумаге: три контейнера `healthy`, `/actuator/health`
  = UP, токен `administrator` открывает `/actuator/metrics`, `architect`
  и `viewer` получают 403, без токена — 401.
- **2026-09-29. Расхождение с §10.4.** Конвейер заведён не целиком: шагов
  golden-file, интеграционных и Playwright в нём нет. Это не упущение —
  наполнять их нечем до этапов 1, 2 и 4, а зелёная галочка над ненаписанным
  тестом хуже отсутствующей. Места, куда они встанут, названы комментариями
  в `.github/workflows/ci.yml`.
- **2026-09-29. Браузерного входа нет.** §12 требует «пустое приложение
  с логином»; страницу логина отдаёт Keycloak, realm и публичный клиент
  `archi-creator-ui` настроены, но SPA на неё не уводит и токен не хранит —
  клиента OIDC во фронтенде нет. Решено не заводить его раньше реального
  экрана этапа 3: выбор библиотеки и способ хранения токена — решение,
  которое незачем принимать над пустой страницей. Проверка «вход отдаёт JWT
  с ролью» закрыта на уровне API.
- **2026-09-29. Схема `workspace` заведена без строки по умолчанию.**
  Рабочее пространство не создаётся миграцией: `V1` даёт таблицу, и только.
  Кто и когда заводит первое рабочее пространство — вопрос этапа 2 вместе
  с остальными таблицами; сажать сейчас строку с выдуманным `id` значило бы
  принять за этап 2 решение, которое ему же и переделывать.
- **2026-10-08. Этап 1 начат. Формат `archi_id` в спеке был неверен.**
  Спека требовала `^id-[0-9a-f]{24}$`, и это совпадало с эталонной моделью —
  но эталон собран скриптом. Сам Archi генерирует `id-` и 32 hex
  (`UUIDFactory.createID`: `"id-" + UUID.randomUUID()` без дефисов), а модели
  старых версий и импорт из OEF дают иные формы. Узкий шаблон отклонил бы
  любой файл, сохранённый в Archi, — то есть FR-01 на настоящих данных.
  Решение: проверка формата смягчена до «пригодно для атрибута `id`»
  (`^[A-Za-z0-9_][A-Za-z0-9_.-]{0,254}$`), новые идентификаторы — `id-<32 hex>`,
  как у Archi. Правлены `ADR-0002`, `aggregates.yaml` трёх контекстов,
  контракты, глоссарии, `INV-MDL-001`, два use case'а. **Регрессия вероятна:**
  кто-нибудь «уточнит» шаблон обратно по эталонному файлу — на нём тесты
  останутся зелёными, а настоящий файл Archi перестанет открываться.
  Поэтому в тестах читателя есть файл с 32-hex идентификаторами.
- **2026-10-08. Пробел спеки: `Location` и `Grouping` вне фаз.** FR-07/FR-08
  перечисляют слои, а эти два типа ArchiMate относит к «прочим» и ни в одну
  фазу не попадают. Решено в каталоге: фаза не назначена, тип opaque —
  хранится и выгружается, но не редактируется. Фикстура
  `capability_and_location` §9.1 этого и ждёт. Решать, в какую фазу их взять,
  — этапу 6.
- **2026-10-08. Матрица связей взята из Archi, а не набрана руками.**
  Ресурс `archimate-3.2-relationships.xml` — `relationships.xml` редактора
  Archi без правок (MIT, происхождение в шапке файла). Набирать 3 844 клетки
  заново значило бы получить таблицу, расходящуюся с Archi в паре мест,
  и связь, нарисованную в одном инструменте, отвергал бы другой.
  §9.2 просит кейсы «из таблицы спецификации ArchiMate 3.2»; генерировать
  их из того же ресурса — проверять таблицу самой собой. Поэтому 31 тройка
  выписана руками в `relation-matrix-cases.csv`, плюс свойства, которые
  держит язык: ассоциация разрешена всегда, специализация — внутри типа
  и в двух парах-подтипах (Contract ⊂ BusinessObject, Constraint ⊂
  Requirement; первая версия теста о них не знала и упала — поправлен тест,
  не матрица).
- **2026-10-08. Корневых папок девять, а не восемь.** Спека (`INV-MDL-009`,
  `FolderType`, UC-MDL-001, UI-018) перечисляла восемь и пропускала
  `implementation_migration` — а она есть и в Archi, и в эталонной модели
  (`Implementation & Migration`, строка 1666). Модель, созданная по старой
  спеке, открылась бы в Archi с добавленной папкой, и round-trip сломался бы
  на структуре. Тест `FolderTreeTest#eightRootFoldersAlwaysExist` переименован
  в `#rootFoldersAlwaysExist`: число в имени теста — та же ошибка в третьем месте.
  Макеты `docs/mockups/` ненормативны и не правились.
- **2026-10-08. Форма `ModelDocument` и `DocumentNode` изменена в спеке.**
  По спеке у узла были `attributes`, `children` и `raw`, а у документа —
  отдельные списки `folders`, `elements`, `relationships`, `views`.
  Ни то ни другое не держит round-trip: `documentation`, `property` и
  `bounds` — не узлы (у них нет `id`, а INV-IXC-001 требует его у каждого
  узла), и класть их было некуда; порядок между ними и детьми терялся;
  элементы в файле лежат внутри папок, и плоские списки рядом с деревом —
  вторая копия, расходящаяся с первой. Теперь содержимое — один
  упорядоченный список `DocumentContent` (узел | значение | фрагмент),
  срезы спеки вычисляются из дерева. `ArchiId` в interchange свой, а не
  общий с modeling: модули доменов друг от друга не зависят (Maven не
  допускает цикла, а partnership в карте контекстов — в обе стороны).
- **2026-10-08. `targetConnections`: множество проверяется, порядок хранится.**
  В эталоне у 23 узлов порядок `targetConnections` не совпадает с порядком
  соединений в файле — так что «восстанавливать из `sourceConnection`»
  буквально нельзя, порядок потеряется. Читатель хранит атрибут как записан
  и отклоняет файл, где *множество* расходится с соединениями узла
  (`IXC_TARGET_CONNECTIONS_MISMATCH`): такой файл Archi не пишет, а писатель
  воспроизвёл бы рассогласование. Восстановление по соединениям с исходным
  порядком понадобится этапу 2, когда соединения придут из БД.
- **2026-10-08. Что читатель теряет сознательно.** Комментарии и инструкции
  обработки *между узлами* (внутри непрозрачного фрагмента они сохраняются),
  выбор между `<a/>` и `<a></a>` (StAX его не сообщает) и литеральные
  переводы строк в атрибутах — их нормализует сам парсер по спецификации XML.
  Ни одно из этого не меняет смысла документа; Archi комментариев не пишет.
  Текст вне значения (`<element>текст</element>`) не теряется, а отклоняется:
  положить его некуда, и молча выбросить нельзя.
- **2026-10-08. Писатель совпал с эталоном побайтово почти целиком.**
  Первый же прогон на `Hamkorbank_AS_IS_strict.archimate` дал файл, который
  отличается от исходного в двух местах, и оба не смысловые: две пустые папки
  эталон пишет развёрнуто (`<folder …>`, перевод строки, `</folder>`), а писатель —
  `<folder …/>`, как Archi; и в конце эталона нет перевода строки. Эталон собран
  скриптом, не Archi, отсюда расхождение. Хранить в документе «как был записан
  пустой элемент» и «был ли перевод строки в конце» не стали: это форматирование,
  а обещание FR-02 — семантическая идентичность. Поэтому golden-file сравнивает
  через `assertXmlEquivalent`, а не побайтово.
