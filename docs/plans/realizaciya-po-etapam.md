# План реализации Archi Creator по этапам

## Контекст

План производен от нормативной спецификации [`spec/`](../../spec/README.md) и от
порядка работ [§12](../archi-creator.md#12-этапы-работ). Он не вводит новых
требований: каждая задача ссылается на требование (`FR-xx`/`NFR-xx`), якорь
(`INV-*`, `UC-*`, `UI-*`) или решение (`ADR-*`), из которого она следует.
Если задача ни на что не ссылается — это либо пробел спеки (заводить якорь),
либо лишняя работа.

**Состояние на 2026-10-14:** **этапы 0–6, 6a, 7a и 7b закрыты.** Этап 7b дал импорт
состояния модели из Git по ссылке — ветке, тегу или коммиту — новой версией: отчёт
об изменениях по объектам и полям ничего не пишет, применение принимает ровно коммит
отчёта и только при неизменном содержимом модели, одной транзакцией; отказ оставляет
сессию `REJECTED`. Импорт головы ветки привязки снимает расхождение 7a. Дальше — этап 7c.

**Состояние на 2026-10-13 (ночь):** **этапы 0–6, 6a и 7a закрыты.** Этап 7a дал выгрузку
в Git: каждое сохранение версии — коммит в репозитории рабочего пространства, модель
разложена в YAML по файлу на объект, и разложена обратимо — из дерева коммита
собираются те же байты `.archimate`, поэтому очистка снимков по ретеншену наконец
включена: очищенный снимок возвращается из коммита. Доставка — через outbox (ADR-0003
принят), ветка двигается только вперёд, чужая правка модели — расхождение, а не
перезапись. Дальше — этап 7b.

**Состояние на 2026-10-13 (вечер):** **этапы 0–6 и 6a закрыты.** Этап 6a дал выгрузку
в Open Exchange Format: zip из файла, проверенного официальной XSD 3.1, и отчёта
о потерях, полного по построению; переносится всё, что формат выражает, независимо
от фазы метамодели. Файл открывает Archi 5.9. Дальше — ветка 7a.

**Состояние на 2026-10-13:** **этапы 0–6 закрыты.** Этап 6 сделал редактируемыми
Motivation и Strategy: Capability, Goal и ещё двенадцать типов создаются, правятся
и связываются по матрице, у каждого свой силуэт и иконка на канве и в экспорте,
палитра получила два слоя. Модели, загруженные раньше, получают это миграцией `V5`.
Дальше — независимые ветки 6a и 7a.

**Состояние на 2026-10-12 (ночь):** **этапы 0–5 и 5a закрыты.** Этап 5a дал серверный
экспорт представлений в SVG и PNG — одно представление и все архивом, из снимка версии,
с отчётом о потерях; шрифт Inter теперь общий у канвы и сервера, а совпадение экспорта
с канвой держит визуальная сверка на пяти представлениях эталона. Дальше — независимые
ветки 6 и 7a.

**Состояние на 2026-10-12 (вечер):** **этапы 0–5 закрыты.** Этап 5 дал ИИ-помощника:
четыре функции потоком SSE (ревью, подсказка, план, документация), бюджет месяца
с порогами, частоту и размер контекста, аудит каждого обращения, панель в редакторе
и настройки для администратора. Модель помощник не меняет — это держат форма типов
и граница модулей. Дальше — независимые ветки 5a, 6, 7a.

**Состояние на 2026-10-12:** **этапы 0–4 закрыты.** Этап 4 дал умные связи по матрице,
вложенность с подразумеваемой связью, авторазметку (считает сервер, применяет браузер —
`ADR-0019`) и паритет с Archi на канве; Playwright идёт в CI. Дальше — независимые
ветки 5, 5a, 6, 7a.

**Состояние на 2026-10-09:** **этапы 0–3 закрыты.** Этап 3 дал редактор:
дерево с поиском и правкой, канву на React Flow по общей геометрии фигур,
палитру, «Свойства» и «Описание», историю на всю сессию; правки живут в браузере
и уходят на сервер при сохранении (`ADR-0018`). Следующий — этап 4.

**Состояние на 2026-10-08:** **этапы 0, 1 и 2 закрыты.** Модель хранится
в PostgreSQL (ADR-0017: типизированные столбцы плюс остаток XML), сценарии
UC-MDL-001…007 и UC-IXC-001…002 работают через REST, импорт → БД → экспорт
через API даёт исходный файл (`RoundTripApiIT`), версии, блокировки, ACL,
идемпотентность, CSV-каталог, метрики и алерты на месте. Тестов 258: 225
юнит-тестов и 33 интеграционных на Testcontainers (`postgres:16`, `keycloak:26`).
Следующий этап — 3, канва.

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
| 3 ✅ | FR-03, FR-11, FR-12, FR-16…FR-21, FR-30…FR-36, FR-42, NFR-01, NFR-08 | ui |
| 4 ✅ | FR-10, FR-13…FR-16, FR-35 | ui + modeling |
| 5 ✅ | FR-22…FR-27, FR-51…FR-53, NFR-04, NFR-06 | advisor |
| 5a ✅ | FR-41, FR-42 | interchange (export) |
| 6 ✅ | FR-08 | modeling (метамодель фазы 2) |
| 6a ✅ | FR-43, FR-44 | interchange (oef) |
| 7a ✅ | FR-37, FR-40, FR-46, FR-47 | interchange (git) |
| 7b ✅ | FR-38 | interchange (git) |
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
**Закрыт 2026-10-08**, проверка — в разделе «Выход этапа».

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
      вложенность читаются деревом как есть (`INV-MDL-009`). Требовать все
      девять читатель не стал: Archi сам дописывает недостающую корневую папку
      при открытии, так что её отсутствие — не повреждение. Обязательность
      девяти корней — правило агрегата модели, этап 2
- ✅ Порядок узлов среди соседей фиксируется (`DocumentOrder`) — условие
      round-trip ([§3.4](../backend.md#34-кодек-archimate--ключевые-решения), п. 3)
- ✅ `bounds` относительно родителя, у детей `Group` — относительно группы
- ✅ Свойства `property key/value`, `documentation`, `viewpoint`
- ✅ Неизвестные узлы → `RawXmlFragment` с адресом родителя и позицией
      (FR-03, `INV-IXC-001`). Неизвестный *атрибут* хранится в `attributes`
      узла на своём месте — отдельный фрагмент ему не нужен. Флаг
      `supported = false` и колонка `raw_xml` — отображение в таблицы, этап 2
- ✅ `targetConnections`: порядок идентификаторов воспроизводится как есть,
      множество сверяется с `sourceConnection` и при расхождении файл
      отклоняется. *Восстановление* атрибута из соединений понадобится, когда
      соединения придут из БД, — этап 2 (см. журнал)
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
- ✅ Генератор новых `archi_id` в формате `id-<32 hex>`, как в Archi
      ([`ADR-0002`](../../spec/adr/0002-dual-identity.md))

### 1.4 Сессии и отчёты (домен, без хранения)

- ✅ `ImportSession` со state machine `RECEIVED → PARSED → VALIDATED → APPLIED`,
      отказ из любого нетерминального (`INV-IXC-002`)
- ✅ Идемпотентность по `Idempotency-Key` + `sourceHash` (`INV-IXC-003`)
- ✅ `ImportFinding` с `severity`, `code`, `archiId`, `xmlLine`
- ✅ `strictMode`: `ERROR` отклоняет только в строгом режиме (`INV-IXC-007`,
      FR-49, [§8.3](../backend.md#83-строгость-импорта)). Автомат, находки
      и строгость — один агрегат и один таск: порог отказа живёт в переходе
      `validate`, а повреждения отклоняются уже в `parse`
- ✅ `ExportJob`, `LossReport`, `LossEntry`; для `ARCHIMATE` список потерь
      обязан быть пуст (`INV-IXC-006`)

### 1.5 Фикстуры и тесты

Набор из [§9.1](../backend.md#91-golden-file-тесты-round-trip--критический-контур):

- ✅ `hamkorbank_as_is.archimate` — **не копия**: тест читает
      `docs/Hamkorbank_AS_IS_strict.archimate` напрямую (402 элемента, 368 связей,
      12 представлений, вложенные группы, кириллица). `INV-IXC-005` называет
      эталоном именно этот файл, и копия в фикстурах молча разошлась бы с ним
- ✅ Пять синтетических фикстур в `archi-bootstrap/src/test/resources/fixtures/`,
      один параметризованный round-trip на все (`fixtureSurvivesRoundTrip`) и
      проверка того, ради чего заведена каждая. Пункты плана слиты: фикстуры
      пишутся одним приёмом и проверяются одним тестом
  - `capability_and_location` — элементы фаз 2–3, `Location`, `Grouping`, вложенность
    элементов на представлении
  - `nested_containment` — `Group` → `Group` → `DiagramObject`, элемент в элементе,
    вложенные пользовательские папки, точки перегиба
  - `all_relationship_types` — 11 типов связей, `Junction` «и»/«или», `accessType`,
    `directed`, `strength`, связь как конец ассоциации и соединение к соединению
  - `styled_objects` — `fillColor`, `alpha`, `font`, `fontColor`, `lineColor`,
    `lineWidth`, `gradient`, `textAlignment` у узлов, групп, заметок и соединений
  - `unknown_extension` — незнакомые узлы с собственным пространством имён и
    комментарием внутри, незнакомые атрибуты, `profile`/`profiles`, `feature`,
    типы из «будущего» Archi, `SketchModel`, `canvas:CanvasModel`,
    `DiagramModelReference`
- ✅ Хелпер `assertXmlEquivalent`: нормализация форматирования, строгий
      контроль порядка узлов, id и содержимого

Тесты (имена — контракт из таблиц «Маппинг на тесты»):

- ✅ `ArchiReaderTest#unknownNodeIsPreservedAsRawFragment` (`INV-IXC-001`)
- ✅ `ImportSessionStateMachineTest#applyIsAllowedOnlyFromValidated` (`INV-IXC-002`)
- ✅ `ImportIdempotencyTest#sameKeyReturnsExistingSession` (`INV-IXC-003`)
- ✅ `ArchiWriterTest#writingTwiceProducesIdenticalBytes` (`INV-IXC-004`)
- ✅ `ArchiCodecTest#documentSurvivesWriteReadCycle` (`INV-IXC-005`)
- ✅ `RoundTripGoldenFileTest#hamkorbankAsIsSurvivesRoundTrip` (`INV-IXC-005`, NFR-05)
- ✅ `RoundTripGoldenFileTest#opaqueNodesSurvive` (FR-03)
- ✅ `StrictImportTest#errorRejectsOnlyInStrictMode`,
      `#corruptedFileIsRejectedInBothModes` (`INV-IXC-007`)
- ✅ `RelationMatrixTest#forbiddenRelationIsRejectedOnCreate`,
      `#importedViolationIsReportedNotRejected` (`INV-MDL-007`)
- ✅ Параметризованные тесты матрицы по тройкам «источник — цель — тип связи»
      ([§9.2](../backend.md#92-юнит-тесты))
- ✅ Правила вложенности: подразумеваемая связь на каждую пару типов
- ✅ Производительность: файл на 5 000 строк XML ≤ 3 с (NFR-02) — холодное
      чтение синтетического файла в пропорциях эталона занимает около 0,1 с;
      запас на порядок ловит деградацию (DOM, квадратичный обход), а не шум CI

**Выход этапа**

- ✅ Round-trip референсной модели зелёный, регрессия блокирует сборку (NFR-05) —
      проверено порчей писателя: тест краснеет
- ✅ Gate round-trip включён в CI отдельным шагом — «Golden-file round-trip
      (NFR-05)» перед `mvn verify`, без фронтенда

---

## Этап 2 — хранение, API, блокировки, версии

**Критерий готовности (§12):** импорт → БД → экспорт без потерь через API.
**Закрыт 2026-10-08**, проверка — в разделе «Выход этапа» и в журнале.

### 2.0 Проектные решения этапа и правка спеки

Решения, без которых код этапа не пишется; каждое — в спеке, здесь сводка.

- ✅ **Остаток XML** ([`ADR-0017`](../../spec/adr/0017-xml-residue.md)): строка
      с `archi_id` хранит типизированные столбцы по `aggregates.yaml` и `raw_xml` —
      порядок атрибутов, незнакомые атрибуты и узлы без id с позициями, метки мест
      типизированных значений. Формат остатка — interchange, modeling его не
      разбирает. При выгрузке столбец сильнее остатка
- ✅ **Одна разреженная нумерация на родителя:** позиции дочерних строк
      (`sort_order`) и элементов остатка — в одном пространстве с шагом 1000.
      Рёбра лежат в содержимом своего источника, как `sourceConnection` в файле
- ✅ **Правка спеки по фактам файла Archi**, найденным на фикстурах этапа 1:
      конец связи — элемент *или связь* (`ConceptRef`, ассоциация к связи
      в `all_relationship_types`); конец ребра — узел или ребро (`ViewEndpoint`);
      точка перегиба — `startX/startY/endX/endY`, а не `x, y`; размер `-1` —
      «по умолчанию» Archi; вид узла `JUNCTION` ошибочен (Junction — элемент,
      на представлении он `DIAGRAM_OBJECT`), добавлен `OTHER` для ссылок
      на представления, скетчей, холстов; у представления и узла — `archiType`
- ✅ Имя модели — до 500 символов, как у всех имён (журнал, п. 1);
      UC-IXC-001 проходит `PARSED` (журнал, п. 2); §4.3 относит Git к 7a (п. 3)
- ✅ ACL модели: `INV-MDL-011` и `UC-MDL-007` заведены, §2.6 разблокирован
- ✅ **Слои и зависимости.** `interchange-application → modeling-application`:
      импорт пишет модель командой modeling, экспорт читает её запросом modeling,
      а снимок версии modeling получает через свой порт `SnapshotWriter`,
      реализованный в interchange. modeling об interchange не знает — Maven
      не допускает цикла, и направление выбрано по тому, кто переводит язык
- ✅ **Транзакции и события вне Spring:** application-слой вызывает порт
      `UnitOfWork`, реализация — `TransactionTemplate` в `archi-bootstrap`;
      события уходят после коммита через `TransactionSynchronization` (`ADR-0003`,
      `direct`)
- ✅ **Роли проверяются на границе use case'а** по таблице
      `security.authorization` из `spec/nfr/<bc>.yaml`, а не матчерами URL —
      так решено ещё на этапе 0 (`SecurityConfig`, javadoc)

### 2.1 Схема БД (Flyway, `archi-bootstrap`)

Сущности и ограничения — [§4.1](../database.md#41-сущности),
[§4.2](../database.md#42-индексы-и-ограничения).

Миграция `V2__model.sql`. Проверена на `postgres:16` до кода адаптера:
связь вставляется раньше своих элементов, удаление связанного элемента
отклоняется на коммите, удаление модели каскадом чистит всё содержимое.

- ✅ `model`, `model_folder`, `element`, `relationship`, `view`, `view_node`,
      `view_edge`, `model_property`, `element_property`, `relationship_property`,
      `view_property`, `model_version`, `model_lock`, `model_access_entry`,
      `ai_audit_log`
- ✅ `UNIQUE (model_id, archi_id)` на всех сущностях с `archi_id` (`INV-MDL-001`);
      у `view_node` и `view_edge` ради этого повторён `model_id`
- ✅ `INDEX (model_id, archi_type)`, `INDEX (view_id, parent_id, sort_order)`
- ✅ Концы связи — `ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED`
      (`INV-MDL-004`): по смыслу `RESTRICT`, проверка в конце транзакции —
      см. журнал. Конец — элемент или связь, ровно одно из пары (`CHECK`)
- ✅ `sort_order` разреженный, шаг 1000, во всех таблицах с порядком —
      `CHECK > 0` без `DEFAULT`: порядок назначает домен
      (`INV-MDL-005`, [§11.6](../database.md#116-что-нужно-сделать-уже-на-этапе-1))
- ✅ `raw_xml` у каждой строки с `archi_id` и у модели (ADR-0017); у opaque
      элемента и связи — обязателен (`CHECK supported OR raw_xml IS NOT NULL`, FR-03)
- ✅ `model_version.snapshot` — `bytea` gzip, nullable; `git_sha` nullable;
      `content_hash` — ответ на «есть ли изменения» без снимка
- ✅ Таблица ключей идемпотентности (`INV-MDL-003`) и таблица `import_session`
      с находками

### 2.2 Домен (`archi-modeling-domain`)

- ✅ Агрегаты `ArchitectureModel`, `View`, `ModelLock`, `ModelVersion`,
      `Workspace`, плюс `ModelAccessList` (FR-29) и `IdempotencyRecord`
      (`INV-MDL-003`) — оба заведены в спеке на §2.0
- ✅ Сущности `ModelFolder`, `Element`, `Relationship`, `ViewNode`, `ViewEdge`
- ✅ VO: идентификаторы на `UUIDv7` (генерация на стороне приложения),
      `SortOrder`, `Bounds`, `Bendpoint`, `PropertyEntry`, `StyleOverride`,
      `RawXml`, `EditorIdentity`, `ConceptRef`, `ViewEndpoint`, `DiagramType`
- ✅ State machine модели: `ACTIVE → DELETED → PURGED`, прямой
      `ACTIVE → PURGED` запрещён (`INV-MDL-002`)
- ✅ State machine блокировки: `HELD → RELEASED | EXPIRED`; просроченная
      блокировка не даёт прав (`INV-MDL-006`)
- ✅ Инварианты как проверки домена: `INV-MDL-001`, `004`, `005`, `007`,
      `008`, `009`, `010`, `011`; отчёт валидации `ModelValidator` (FR-10)
- ✅ Доменные события `ModelVersionCommitted`, `ModelLockReleased`,
      `ModelDeleted` ([events.yaml](../../spec/domain/modeling/events.yaml)).
      Публикация `direct` после коммита — в application-слое (§2.4)

### 2.3 Персистентность (`archi-*-adapter-persistence`)

- ✅ JPA-сущности и маппинг на домен (домен о JPA не знает — enforcer).
      Сущности плоские, без ассоциаций: ссылки — ключами, граф собирает маппер
- ✅ Чтение дерева модели ([§4.3](../database.md#43-стратегия-хранения)) —
      по запросу на таблицу (модель, папки, элементы со свойствами, связи со
      свойствами, места представлений): число запросов не зависит от размера
      модели. «Одним запросом» §4.3 буквально не выполнено — см. журнал
- ✅ Payload представления по требованию: узлы и рёбра двумя запросами;
      все представления модели — тремя (для снимка версии)
- ✅ Оптимистичная блокировка `version` на модели и представлении (NFR-07):
      любое изменение содержимого поднимает версию строки
      (`OPTIMISTIC_FORCE_INCREMENT`), конфликт — `409` с кодом `INV-MDL-006`
- ✅ Репозитории портов: `ModelRepository` (папки, элементы и связи — внутри
      агрегата, отдельные `ElementRepository`/`RelationshipRepository`/
      `FolderRepository` не понадобились), `ViewRepository`,
      `ModelLockRepository`, `ModelVersionRepository`, `ModelAccessListRepository`,
      `IdempotencyRepository`, `WorkspaceRepository`. `ImportSessionRepository` —
      в адаптере interchange (§2.4)
- ✅ Проводка в `archi-bootstrap` (`bootstrap.wiring`): `UnitOfWork` на
      `TransactionTemplate`, события после коммита, метрики use case'ов
      на Micrometer, первое рабочее пространство при старте на пустой базе
- ✅ Интеграционные тесты на Testcontainers `postgres:16`, failsafe, отдельный
      шаг CI. `ModelPersistenceIT#duplicateArchiIdViolatesUniqueConstraint`
      и круг записи–чтения дерева и представления

### 2.4 Сценарии (`archi-*-application`)

Сценарии modeling — в `archi-modeling-application/.../service`, проверены на портах
в памяти (`ModelingScenariosTest`): блокировка, роли, ACL, идемпотентность,
«нет изменений — нет версии», групповое удаление элемента со связью.

- ✅ `UC-MDL-001` — создать, переименовать, удалить, восстановить, уничтожить
      модель; девять корневых папок сразу при создании (`FolderTreeInitializer`)
      и версия 1 — точка отсчёта истории
- ✅ `UC-MDL-002` — создать элемент, создать представление, разместить элемент
- ✅ `UC-MDL-003` — создать связь (с ребром на представлении по запросу),
      `SuggestRelationTypes` по матрице с порядком «по умолчанию»
- ✅ `UC-MDL-004` — сохранить модель как версию, `LabelVersion`,
      `RollbackToVersion`; порты `SnapshotWriter` и `SnapshotReader` реализует
      interchange. `GitPublisher` — этап 7a, вместо него порт `GitBinding`
      с ответом «нет» для всех: от него зависит только ретеншен
- ✅ `UC-MDL-005` — захват, продление, освобождение и принудительное снятие блокировки
- ✅ `UC-MDL-006` — переместить, переименовать, создать папку, удалить группой
- ✅ `UC-MDL-007` — список доступа к модели (§2.6)
- ✅ Раскладка документа по агрегатам и сборка обратно (ADR-0017) —
      `DocumentDecomposer`, `DocumentAssembler`, формат остатка `NodeResidue`.
      Проверено без базы на эталоне и пяти фикстурах: документ возвращается
      **тем же значением**, с порядком атрибутов, и даёт те же байты писателя
      (`DocumentMappingTest`); правка столбца перекрывает остаток, новый объект
      встаёт в раскладке Archi. Здесь же порты снимка modeling (`ArchimateSnapshots`)
- ✅ `UC-IXC-001` — импорт файла: сессия `RECEIVED → PARSED → VALIDATED → APPLIED`,
      проверка методологии матрицей modeling (`MethodologyCheck`), применение одной
      транзакцией вместе с версией 1, отчёт и у отклонённого. Строгость импорта
      пространства меняет `ADMIN` (`ConfigureImportPolicy` — строка добавлена
      в `spec/nfr/interchange.yaml`)
- ✅ `UC-IXC-002` — экспорт модели в `.archimate` по версии: файл версии — её
      снимок, собранный из базы при сохранении (`DocumentAssembler`); указанная
      или последняя версия, удалённая модель — `409 MODEL_DELETED`, очищенный
      снимок без Git — `410`
- ✅ `LockGuard`: любая запись требует `HELD`-блокировки автора (`INV-MDL-006`).
      Не отдельный порт, а одна точка — `ModelingKernel.writableModel` — с
      правилом в домене (`ModelLock.requireWriteAccess`): порт без второй
      реализации был бы церемонией
- ✅ Идемпотентность команд изменения: тот же ключ и то же тело → первый
      результат, перечитанный заново, другое тело → `409` (`INV-MDL-003`).
      Ключ необязателен; хранится ссылка на результат, а не ответ

### 2.5 REST и контракты (`archi-*-adapter-rest`)

- ✅ Наполнить `paths` в `spec/contracts/{modeling,interchange}/rest-api.openapi.yaml` —
      по одному endpoint'у на экспонированный use case, `x-use-case` и
      `x-operation` (строка таблицы доступа). Контракт advisor остаётся скелетом
      до этапа 5. Оговорки «каталог use case'ов пуст» в `nfr/*.yaml` и
      `events.yaml` сняты (журнал, п. 5–6)
- ✅ Эндпоинты из [§5](../backend.md#5-rest-api): `/models`, `/models/{id}`,
      `/models/import`, `/models/{id}/export`, `/models/{id}/lock`,
      `/models/{id}/versions*`, `/models/{id}/validate`, `/elements*`,
      `/relationships*`, `/views/{id}`, `/views/{id}/layout`,
      `/views/{id}/nodes`, `/view-nodes/{id}`, `/metamodel/*`. Сверх таблицы §5 —
      то, без чего сценарии не исполнить: `POST /models/{id}/versions` (сохранение,
      UC-MDL-004), `/restore` и `/purge` модели, `/folders` и `/tree/*` (UC-MDL-006),
      `POST /models/{id}/views`, `/acl` (UC-MDL-007), `/workspaces` и строгость
      импорта. `auto-layout` — этап 4, экспорт картинок — 5a
- ✅ DTO `ModelTree`, `ViewPayload`, `LayoutPatch`, `ElementPatch`,
      `VersionInfo`, `LockInfo`, `Finding` ([§5.1](../backend.md#51-ключевые-dto)).
      Остаток XML наружу не отдаётся. **Для этапа 3:** подписи групп и текст
      заметок живут пока в остатке — `ViewPayload` их не несёт, канве придётся
      типизировать `name` и `content` узла (поля спеки + столбцы)
- ✅ Problem Details RFC 7807 с полем `code`, `@RestControllerAdvice`
      ([§8.1](../backend.md#81-формат-ответа)); класс отказа домена (`Failure`)
      определяет статус одинаково для обоих контекстов
- ✅ Таблица сценариев ошибок [§8.2](../backend.md#82-основные-сценарии):
      `409` блокировка и конкурентное сохранение, `422` недопустимая связь,
      `409` удаление элемента со связями, `400` битый XML, `422` строгий импорт
- ✅ Отчёт валидации метамодели без ИИ: `GET /models/{id}/validate` → `Finding[]`
      (FR-10 для импортированных нарушений)
- ✅ Экспорт каталога в CSV ([§5.2](../backend.md#52-экспорт-каталога-в-csv),
      FR-45): zip из `elements.csv` и `relations.csv`, UTF-8 **с BOM**,
      разделитель `;`, параметр `sep`, динамические столбцы свойств по частоте
      ключа, фильтр `folder=<id>`, доступно `VIEWER`. Строится из снимка
      зафиксированной версии, как и `.archimate` (`INV-IXC-008`); `folder` —
      `archi_id` папки: внутренний ключ меняется при откате, `archi_id` нет.
      Связи в отфильтрованном каталоге — те, у которых оба конца в поддереве

### 2.6 Доступ

- ✅ Маппинг ролей на операции по таблице `security.authorization`
      в [`spec/nfr/modeling.yaml`](../../spec/nfr/modeling.yaml) (FR-28) —
      `Operation` в application-слое. В таблицу добавлены пять операций, которых
      она не называла: `ListVersions`, `UpdateRelationship`, `CreateView`,
      `ReorganizeTree`, `ManageModelAccess`
- ✅ Чужую блокировку снимает только `ADMIN`; `PurgeModel`, `RestoreModel` —
      только `ADMIN`
- ✅ ACL на уровне модели (FR-29, `INV-MDL-011`, `UC-MDL-007`): список
      доступа сужает роли и не расширяет их, скрытая модель отвечает `404`;
      группы — из claim `groups` токена.
      Спека заведена 2026-10-08 (§2.0), раньше пункт был заблокирован

### 2.7 Версии и ретеншен

- ✅ Версия на каждое сохранение: автор, время, комментарий, монотонный
      `versionNo` (`INV-MDL-010`, FR-06); без изменений версия не создаётся
      (отпечаток снимка совпал с последним)
- ✅ Метка версии (`label`) — релиз архитектуры, снимок не удаляется (FR-48)
- ✅ Правило ретеншена ([§4.4](../database.md#44-хранение-и-очистка-версий),
      FR-46): последние 30 дней, помеченные, последняя за календарный месяц —
      `SnapshotRetention` в домене
- ✅ Фоновая задача очистки написана, но **выключена** до этапа 7a
      (`archi.modeling.retention-enabled: false`), а и включённая не трогает
      пространство без доступного Git: снимок — единственная копия (FR-47).
      `SnapshotRetentionTest` и `SnapshotRetentionIT#purgeIsDisabledWithoutGitBinding`

### 2.8 Наблюдаемость

- ✅ Метрики из `spec/nfr/*.yaml`: `*_usecase_requests_total`,
      `*_usecase_duration_seconds`, `*_usecase_errors_total` с label `error_code`
      = код инварианта, `modeling_lock_wait_seconds`, `modeling_model_elements`,
      `modeling_validation_findings` (gauge — Prometheus не даёт gauge суффикс
      `_total`, имя в спеке с ним), `interchange_import_sessions_total`,
      `interchange_import_findings_total`, `interchange_opaque_objects`,
      `interchange_export_loss_entries_total`. Ожидание блокировки — остаток срока
      чужой блокировки в момент отказа: сервер не ждёт, второй получает `409` сразу
- ✅ Actuator: `/actuator/health`, `/metrics`, `/prometheus`
      ([§8.4](../backend.md#84-логирование-и-наблюдаемость)) — с этапа 0;
      метрики сценариев в Prometheus проверены (`ObservabilityIT`)
- ✅ Структурированные логи JSON (ECS, профиль `prod`) с `traceId`, пользователем
      и id модели в MDC — `RequestCorrelationFilter`; `traceId` берётся из W3C
      `traceparent` и возвращается заголовком `X-Trace-Id`. Маскирование PII —
      в MDC только subject, путь и trace-id, персональных полей в домене нет
      (`pii_fields: []`). Экспорт спанов (OpenTelemetry) — не этап 2
- ✅ Алерты `HighErrorRateSaveModel`, `SlowOpenModelP95`, `ConcurrentSaveConflicts`
      и из interchange — `RoundTripLossDetected`, `SlowImportP95` —
      `deploy/prometheus/alerts.yaml`, выражения дословно из спеки

### 2.9 Тесты этапа

- ✅ `ModelPersistenceIT#duplicateArchiIdViolatesUniqueConstraint`
- ✅ `ModelLifecycleIT#purgeRequiresDeletedState`
- ✅ `IdempotencyIT#sameKeyDifferentBodyReturns409`
- ✅ `ElementDeletionIT#restrictViolationReturns409`
- ✅ `ConcurrentSaveIT#secondWriterGets409` (NFR-07)
- ✅ `ValidationReportIT#importedViolationsAppearInReport`
- ✅ `ModelImportIT#folderTreeSurvivesRoundTrip` — эталон через базу, плюс
      `#fixtureSurvivesDatabaseRoundTrip` на пяти фикстурах §9.1 и
      `#referenceImportsWithinThreeSeconds` (NFR-02)
- ✅ `ImportIT#applyTwiceIsRejected`, `#retryDoesNotCreateSecondModel`,
      `#corruptedFileIsRejectedInBothModes`, плюс строгий режим против матрицы
      и роли на импорте
- ✅ `ExportJobTest#exportPinsSourceVersion` (`INV-IXC-008`) — с этапа 1
- ✅ Ролевой доступ: `VIEWER` → `403` на запись, `ARCHITECT` → `200`
      (`RoleAccessIT`), список доступа — `ModelAclIT#hiddenModelAnswers404`
- ✅ Testcontainers `postgres:16` и `keycloak:26`
      ([§9.3](../backend.md#93-интеграционные-тесты-testcontainers)): Keycloak
      с realm проекта поднимает `KeycloakIT` — токены парольным грантом клиента
      SPA, подпись проверяет настоящий ресурс-сервер, роли — из `realm_access`.
      Остальные тесты REST кладут токен через `spring-security-test`: Keycloak
      на каждый класс — полминуты ни за что
- ✅ Нагрузочный профиль NFR-03: p95 ≤ 300 мс на операциях кроме ИИ и импорта
      (у требования нет якоря намеренно — проверяет профиль) — `LoadProfileIT`
      на эталонной модели. Первый замер (2026-10-08, локально): p95 CreateElement
      20 мс, SaveViewLayout 14 мс, OpenModel 12 мс, OpenView 4 мс — запас на порядок.
      Профиль последовательный и в процессе: ловит деградацию хранения (N+1,
      перезапись агрегата целиком), а не пропускную способность стенда

**Выход этапа**

- ✅ Цикл «импорт файла → сохранение в БД → экспорт через API» даёт исходный
      файл, сравнение `assertXmlEquivalent` зелёное —
      `RoundTripApiIT#importStoreExportThroughApi` на эталонной модели
- ✅ Проверено вживую: собранный jar в профиле `dev` на `postgres:16` — импорт
      эталона curl'ом за 0,7 с, выгрузка семантически равна исходнику и
      побайтово отличается ровно там же, где на этапе 1 (две пустые папки и
      перевод строки в конце эталона); блокировка, элемент, версия 2, отчёт
      валидации, CSV — ответы по контракту

---

## Этап 3 — канва, визуальный язык, дерево

**Критерий готовности (§12):** реальная модель открывается и редактируется.
**Закрыт 2026-10-09**, проверка — в журнале.
**Правила:** `UI-001`, `UI-002`, `UI-006`…`UI-013`, `UI-014`…`UI-018`, `UI-020`, `UI-021`.

### 3.0 Проектные решения этапа и доработки сервера

Решения, без которых фронтенд не пишется; каждое — в спеке, здесь сводка.

- ✅ **Сессия правки живёт в браузере** ([`ADR-0018`](../../spec/adr/0018-local-edit-session.md)):
      правки копятся в документе модели как операции истории из обратимых
      изменений, «Сохранить» переводит сервер от точки сохранения к текущей
      позиции и фиксирует версию. Отмена удаления до сохранения серверу
      не видна — остаток XML не теряется
- ✅ **Идентификаторы новых объектов задаёт клиент:** `id` и `archiId`
      необязательны в командах создания папки, элемента, связи, узла;
      сервер проверяет формат и уникальность. Плюс размещение ребра
      существующей связи `POST /views/{id}/edges` — без него отмена удаления
      узла не вернёт рёбра
- ✅ **Подпись группы и текст заметки — типизированные поля узла** (`label`,
      `content`): канва показывает их, а в остатке XML (`ADR-0017`) модель
      их не видит. Миграция `V3`, раскладчик и сборщик interchange
- ✅ **Размещения элементов в дереве модели:** `ModelTree.placements` —
      какие элементы на каком представлении. Нужно маркерам «не размещён»,
      «×N», фильтрам дерева и списку представлений во «Описании»
- ✅ **Кто вошёл и как входить:** `GET /api/v1/me` (субъект и роли — интерфейс
      по роли, `UI-015`) и открытый `GET /api/v1/ui-config` (адрес Keycloak
      и клиент SPA; в `dev` — без входа, заглушка `ARCHITECT`)

### 3.1 Каркас приложения

- ✅ Структура: канва, палитра, дерево, панель свойств, статус-строка —
      четырёхпанельный вариант A ([§6.5](../frontend.md#65-компоновка-главного-экрана--решение-принято),
      `ADR-0004`)
- ✅ Состояние: Zustand для документа модели, TanStack Query для серверных данных.
      Документ — `model/doc.ts` (обратимые изменения «было → стало»), история —
      `model/history.ts`, перевод изменений в команды сервера — `model/commands.ts`
      (ADR-0018). Блокировка берётся при открытии модели архитектором и продлевается
      каждые 5 минут; вход — `oidc-client-ts` с PKCE, без адреса Keycloak
      (`/ui-config` пуст или 404) — заглушка `dev`
- ✅ Русский интерфейс, строки в ресурсах (NFR-08, `UI-021`) — вынесены сразу,
      а не «потом локализуем»: `i18n/ru.ts`, названия типов и формулировки
      связей — `i18n/notation.ru.ts`. Литералы в JSX ловит `checks/static-rules.test.ts`
- ✅ Макеты как референс: `open docs/mockups/index.html` — дерево, карточка
      элемента и редактирование там собраны на реальных данных

### 3.2 Токены (`UI-010`, FR-20)

- ✅ Генерация CSS-переменных из [`spec/ui/design-tokens.yaml`](../../spec/ui/design-tokens.yaml)
      на сборке — файл спеки единственный источник. Плагин Vite
      (`frontend/build/design-tokens.ts`) отдаёт виртуальные модули
      `virtual:design-tokens.css` и `virtual:design-tokens` (значения объектом —
      для атрибутов SVG). В спеку добавлены токены холста (`canvas.*`): на `--accent`
      ссылался `node_shadow_selected`, а объявлен он не был
- ✅ Те же значения доступны серверному писателю (этап 5a), без второй копии:
      Maven кладёт файл спеки в `classpath:ui/design-tokens.yaml`
- ✅ Статическая проверка: литералов цвета в компонентах канвы нет —
      `src/checks/static-rules.test.ts`, гоняется `npm test` и фазой `test` Maven
- ✅ Тема одна, переключателя нет (`ADR-0011`): графитовый корпус, светлый холст

### 3.3 Общая геометрия фигур (`UI-019`, FR-42)

- ✅ `shapes.json` по контракту [`spec/ui/shapes-contract.yaml`](../../spec/ui/shapes-contract.yaml):
      `outline`, `corner_icon`, `label_box`, `radius_token`, `layer`. Лежит
      в `archi-bootstrap/src/main/resources/ui/` — classpath сервера; фронтенд
      берёт его алиасом `@shared-ui`. Контур — имя примитива (`rect`, `stadium`,
      `circle`, `tab`, `dog-ear`, `stub`), а не путь: путь в нормализованном
      боксе искажал бы скругления при растяжении; смысл примитивов описан в самом
      файле, серверный писатель 5a реализует те же шесть
- ✅ SVG-спрайт иконок, один для канвы и сервера (`ui/icons.svg`)
- ✅ Запись для каждого `ArchiType` фазы 1 обязательна; типы фаз 2 и 3 —
      силуэт-заглушка (`UI-020`, FR-03): запись `fallback`. Полноту против
      реестра типов сервера проверяет `SharedShapesTest`, совпадение иконок
      со спрайтом — он же и `canvas/shapes.test.ts`
- ✅ Фигуры рисуются **инлайн-SVG внутри узла React Flow**, не CSS
      (`ADR-0007`: CSS-силуэт серверный писатель воспроизвести не может)

### 3.4 Канва

- ✅ Рендер представления из `ViewPayload`: узлы, рёбра, вложенность, геометрия
      относительно родителя
- ✅ Отрисовка узла по [§6.3](../frontend.md#63-правила-отрисовки-узла): иконка
      14×14, имя в 1–2 строки с обрезкой на третьей, заливка = токен слоя
- ✅ Приоритет собственного стиля объекта над токеном (`UI-011`, FR-21)
- ✅ Выделение двойной обводкой, не заливкой — иначе теряется код слоя
- ✅ Связи по нотации ([§6.4](../frontend.md#64-связи)): наконечники, пунктир,
      ортогональная маршрутизация, скругление углов, подпись
- ✅ Zoom/pan, сетка, привязка
- ✅ Неподдержанный объект видно и можно двигать, семантически не редактируется
      (`UI-020`, FR-03)
- ✅ Вкладки открытых представлений (`UI-008`, FR-18). Точки перегиба —
      по правилу Archi (смещения от центров концов с весом `(i+1)/(n+1)`,
      якорь «chopbox»); без перегибов — ортогональный маршрут, изломы скруглены
      (`canvas/routing.ts`). Ребро, чей конец — другое ребро, цепляется к середине
      его маршрута. Удаление с холста убирает размещение, а не элемент

### 3.5 Дерево модели (`UI-012`, `UI-013`, FR-30, FR-31)

- ✅ Папки из файла с исходной вложенностью, рекурсивные счётчики по ветвям
- ✅ Три режима группировки: папки / типы / плоский список
- ✅ Живой поиск с подсветкой, авторазвёрткой совпадений и пересчётом
      счётчиков; ⌘F/Ctrl+F, Esc сбрасывает
- ✅ Фильтры: «Все», «На представлении», «Не размещены», «Замечания»;
      комбинируются с поиском
- ✅ Маркеры строки: не размещён, есть находка, число представлений
- ✅ Виртуализация: не более 50 строк в ветви, остальные по «показать ещё»
- ✅ Отклик поиска ≤ 100 мс на 2 000 элементов (NFR-01): `perf/tree-search.bench.ts`
      на эталонной модели, размноженной до 2 010 элементов, — медиана под 1 мс.
      Замер — обычный тест с порогом: `bench` из vitest 5 убран, а порог
      и так полезнее печати — регрессия роняет сборку
- ✅ Двусторонняя синхронизация выделения с холстом и панелью свойств

### 3.6 Правая панель

- ✅ Вкладка «Свойства» (`UI-007`, FR-17): имя, документация, свойства
      key/value, слой, тип, идентификатор; правит объект модели, не представление
- ✅ Вкладка «Описание» (`UI-014`, FR-32, `ADR-0013`): заголовок, документация,
      участие в связях с русскими названиями типов (первые 7 + «показать все N»),
      анализ влияния, свойства, список представлений
- ✅ Анализ влияния по исходящим Serving, Realization, Triggering, Flow, Access:
      прямые, второй уровень, вложенные ([§6.7](../frontend.md#67-вкладка-описание-правая-панель))
- ✅ Навигация кликом по связи: раскрыть ветви, прокрутить, перерисовать карточку
- ✅ Поведение по роли (`UI-015`, FR-33): `VIEWER` — «Описание» по умолчанию,
      «Свойства» только для чтения, палитра и панель инструментов скрыты,
      место отдано дереву. Проверено на эталоне: у IABS 60 связей, при отказе
      напрямую 12 («Карты», «UzASBO», «QR-Online» и ещё 9), вторым уровнем 12,
      вложенных 44 — ровно как в примере §6.7 (`panels/impact.test.ts`).
      Стиль объекта в «Свойствах» пока не правится: правка собственного стиля —
      пункт паритета этапа 4, API для неё ещё нет

### 3.7 Палитра и размещение

- ✅ Палитра типов из `GET /metamodel/elements`, переключается на слой
      выбранного элемента
- ✅ Drag-and-drop с палитры и из дерева на холст (`UI-002`, FR-12):
      с палитры — новый элемент в корне своего слоя и узел, из дерева — только
      узел; уже размещённый элемент не дублируется, а выделяется. Проверено
      вживую до сервера: сохранение создаёт элемент с идентификаторами клиента

### 3.8 История изменений (`UI-017`, FR-35)

- ✅ Стек операций парами `apply`/`revert` со списком затронутых элементов —
      операция хранит не функции, а данные: изменения «было → стало», обратное
      получается перестановкой (`model/history.ts`); данные, а не замыкания,
      нужны, чтобы из той же операции получить команды сервера (ADR-0018)
- ✅ `Ctrl/Cmd+Z`, `Ctrl/Cmd+Shift+Z`, `Ctrl+Y`, кнопки панели инструментов
      с названием конкретной операции в подсказке
- ✅ Панель истории: список, текущая позиция, точка сохранения, переход кликом
- ✅ Пометка «изменён» считается как разница позиции и точки сохранения,
      отмена снимает пометки сама; отмена *за* точку сохранения тоже
      несохранённое изменение
- ✅ Счётчик несохранённых в статус-строке, кнопка «Сохранить» только при
      наличии изменений
- ✅ **Регрессионный тест на дефект макета** ([§6.9](../frontend.md#69-история-изменений)):
      клик внутри дерева не прокручивает строку; прокрутка только когда
      выделение приходит извне. Из кода причина не видна, регрессия вероятна

### 3.9 Редактирование дерева (`UI-016`, `UI-018`, FR-34, FR-36)

- ✅ Переименование по `F2`/двойному клику прямо в строке
- ✅ Перемещение перетаскиванием и «Переместить в папку…» с поиском по полному
      пути; работает для множественного выделения
- ✅ Создание вложенной папки, сразу в режиме переименования
- ✅ Удаление с предварительным показом числа затрагиваемых связей. Перед
      показом подгружаются представления, где размещены удаляемые элементы:
      без их узлов отмена не вернула бы размещения. Удаляются пустые папки;
      непустая — отказ с объяснением, как на сервере (`MDL_FOLDER_NOT_EMPTY`).
      Представления из дерева не удаляются: API для их удаления и создания
      заново пока нет
- ✅ Множественное выделение: `Ctrl/Cmd` — переключение, `Shift` — диапазон;
      правая панель переключается на сводку с групповыми операциями
- ✅ Системные папки формата не удаляются, сообщение объясняет почему
      (`INV-MDL-009`)
- ✅ Контекстное меню по роли: `VIEWER` получает только неизменяющие пункты
- ✅ Перетаскивание меняет папку, но **не** порядок внутри папки — порядок
      значим для round-trip

### 3.10 Тесты этапа

- ✅ `e2e/element-identity.spec.ts` (`UI-001`), `e2e/palette-drop.spec.ts`,
      `e2e/tree-drop.spec.ts` (`UI-002`)
- ✅ `e2e/properties-panel.spec.ts` (`UI-007`), `e2e/view-tabs.spec.ts` (`UI-008`)
- ✅ `e2e/model-tree.spec.ts` (`UI-012`), `e2e/description-tab.spec.ts` (`UI-014`)
- ✅ `e2e/role-viewer.spec.ts`, `e2e/role-architect.spec.ts` (`UI-015`)
- ✅ `e2e/tree-editing.spec.ts` (`UI-016`); сверх плана — `e2e/history.spec.ts`
      (`UI-017`), `e2e/context-menu.spec.ts` (`UI-018`), `e2e/opaque-node.spec.ts`
      (`UI-020`), `e2e/i18n-resources.spec.ts` (`UI-021`): их имена есть в маппинге
      правил, а поведение реализовано на этом этапе
- ✅ `visual/shape-gallery` против эталона Archi (`UI-009`), `visual/tokens.spec.ts`
      (`UI-010`), `visual/style-override.spec.ts` (`UI-011`)
- ✅ `perf/tree-search.bench.ts`, `perf/canvas-pan.bench.ts` на референсной
      модели (`UI-013`, NFR-01: pan/zoom 60 fps, открытие представления ≤ 1 с).
      E2E герметичны: API подменяется в браузере in-memory сервером на снимке
      эталонной модели (`e2e/support/mockApi.ts`, `e2e/fixtures/reference-model.json`,
      снимается скриптом `e2e/fixtures/capture.mjs` с работающего сервера),
      команды синхронизации проверяются как записанные вызовы. Итог: 43 сценария
      Playwright и 52 юнит-теста vitest; замеры — открытие 500 объектов 343 мс,
      медиана кадра панорамирования 16,7 мс, поиск по 2 010 элементам — под 1 мс

---

## Этап 4 — умные связи, вложенность, авторазметка, паритет

**Критерий готовности (§12):** сценарии E2E [§9.4](../frontend.md#94-e2e-playwright) зелёные.
**Закрыт 2026-10-12**, проверка — в журнале.
**Правила:** `UI-003`…`UI-006`, `UI-017` (на канве).

### 4.0 Проектные решения этапа и правка спеки

Решения, без которых интерфейс не пишется; каждое — в спеке, здесь сводка.

- ✅ **Авторазметка считается на сервере, применяется в браузере** (`ADR-0019`).
      `POST /views/{id}/auto-layout` принимает стратегию и граф представления
      в том виде, в каком он сейчас в браузере (с несохранёнными узлами),
      и возвращает предложение геометрии (`LayoutPatch`), базу не меняет.
      Интерфейс кладёт предложение в историю одной операцией (`UI-005`),
      на сервер оно уходит при сохранении через `PUT /views/{id}/layout`.
      Отменённые варианты: «сервер раскладывает и сохраняет сразу» — отмена
      до сохранения разошлась бы с базой (`ADR-0018`); «раскладка в браузере» —
      детерминизм и отсутствие наложений проверяются юнит-тестами сервера (§9.2),
      и тот же алгоритм нужен будущим входам без браузера (ИИ-план, Git)
- ✅ **Вложенность — перенос узла в контейнер:** `PUT /view-nodes/{id}/parent`
      с новым родителем (или корнем) и координатами относительно него; узел
      встаёт в конец содержимого нового родителя. Подразумеваемую связь
      называет метамодель (`NestingRules`): `/metamodel/relations` помечает её
      флагом `nesting`, интерфейс не повторяет правило у себя
- ✅ **Группы и заметки — команды узла:** `POST /views/{id}/nodes` принимает
      `kind: GROUP | NOTE` без `elementId`, с `label`/`content`; `PATCH /view-nodes/{id}` —
      подпись, текст и собственный стиль; `PATCH /view-edges/{id}` — стиль ребра;
      `DELETE /view-edges/{id}` — убрать ребро с представления, связь остаётся
- ✅ **Соединитель — элемент модели,** а не узел без элемента: в Archi
      `Junction` — концепт папки `Other`, на представлении — `DIAGRAM_OBJECT`
      над ним (`ViewNodeKind`). Строка плана этапа «узел без `elementId`» была
      неточна
- ✅ Новый сценарий `UC-MDL-008` «Править представление» (вложенность, группы
      и заметки, стиль, рёбра, авторазметка), операция `AutoLayoutView` в таблице ролей

### 4.1 Сервер

- ✅ Группы и заметки, подпись и текст, стиль узла и ребра, удаление ребра:
      домен `View`, `ViewService`, REST, `ViewEditingIT` (в том числе выгрузка
      в `.archimate`: стиль, группа и заметка пишутся из столбцов, `INV-IXC-005`)
- ✅ Перенос узла в контейнер и флаг `nesting` у допустимых связей
- ✅ Авторазметка: алгоритм в `archi-modeling-domain` (слои ArchiMate сверху
      вниз, упорядочение по барицентрам против пересечений; `layered`, `hierarchy`,
      `compact`), `AutoLayoutService`, endpoint. Юнит-тесты: детерминированность,
      отсутствие наложений, порядок слоёв (§9.2)

### 4.2 Умное рисование связи (`UI-003`, FR-13)

- ✅ Тянем от границы узла к другому узлу или ребру; по отпусканию — список
      типов для пары из `GET /metamodel/relations`, вероятный выбран, `Enter`
      подтверждает, `Esc` отменяет. Недопустимых в списке нет вовсе; пусто —
      связь не рисуется, объясняется почему
- ✅ Связь и ребро — одна операция истории; дубль того же типа между той же
      парой не создаётся, рисуется ребро существующей связи
- ✅ `e2e/smart-relation.spec.ts`: список `ApplicationComponent → BusinessProcess`
      совпадает с матрицей

### 4.3 Вложенность (`UI-004`, FR-14)

- ✅ Отпускание узла над контейнером переносит его внутрь (координаты —
      относительно контейнера), вынос — обратно; дети едут с родителем
- ✅ Элемент в элементе — подразумеваемая связь от родителя к ребёнку
      (composition/assignment по флагу `nesting`), если такой ещё нет; той же
      операцией истории. Вынос связь не удаляет — как в Archi
- ✅ `e2e/nesting.spec.ts`: связь появляется в дереве и во «Описании»

### 4.4 Авторазметка (`UI-005`, FR-15)

- ✅ Кнопка с выбором стратегии; предложение сервера — одна операция истории,
      один `Ctrl+Z` возвращает всё
- ✅ `e2e/auto-layout.spec.ts`: слои сверху вниз, отмена одним шагом

### 4.5 Паритет с Archi (`UI-006`, FR-16)

Тринадцать пунктов, по сценарию на каждый (`e2e/editing-parity.spec.ts`).
Undo/redo и zoom/pan заведены на этапе 3 — здесь они закрываются на канве.

- ✅ Undo/redo на операциях канвы, единый стек с деревом (`UI-017`)
- ✅ Множественный выбор и рамка выделения
- ✅ Zoom/pan на канве, горячие клавиши масштаба
- ✅ Сетка и привязка
- ✅ Группы (`Group`) с детьми в координатах группы, заметки (`Note`)
      и соединители (`Junction`) с палитры; подпись и текст правятся
- ✅ Переопределение стиля отдельного объекта: заливка, цвет и шрифт текста,
      цвет линии (`StyleOverride`, сохраняется при экспорте — `INV-IXC-005`)
- ✅ Копирование и вставка: в другом представлении — те же элементы,
      в том же — копии, как в Archi
- ✅ Выравнивание и направляющие
- ✅ Точки перегиба связей — `ViewEdge.bendpoints`, в формате Archi
- ✅ Удаление ребра с представления (связь остаётся в модели)
- ✅ Навигатор (мини-карта представления)
- ✅ Поиск по модели и «где используется»

### 4.6 Приёмка и конвейер

- ✅ Сценарии §9.4 (`e2e/acceptance.spec.ts`): импорт → открытие → все объекты
      отрисованы; недопустимая связь не предлагается; цепочка undo/redo из пяти
      операций; экспорт → обратный импорт
- ✅ Playwright в CI: работа `e2e` параллельно сборке, Node — из `pom.xml`, визуальный
      эталон под Linux снят в `mcr.microsoft.com/playwright` той же версии
- ✅ Закрытие: README, CLAUDE.md, граф зависимостей, журнал; живая проверка
      `docker compose up` с входом через Keycloak

---

## Этап 5 — ИИ-помощник

**Критерий готовности (§12):** четыре функции работают, аудит пишется.
**Закрыт 2026-10-12**, проверка — в журнале.
**Граница:** помощник **не изменяет модель** (FR-22, `INV-ADV-004`) — любой
результат это текст, ссылки и подсказки.

### 5.0 Проектные решения этапа и правка спеки

Решения, без которых код не пишется; спорные места спеки исправлены до кода.

- ✅ **Модули advisor не зависят от modeling вовсе** — даже на чтение. Порты
      `ModelContextReader`, `MetamodelCatalog` (типы фазы 1 и матрица связей)
      и `WorkspaceDirectory` (пространство по умолчанию) объявлены в
      `archi-advisor-application`, а реализованы мостом в `archi-bootstrap`
      (`bootstrap.advisor`) поверх `ModelQueryService`, `RelationMatrix`
      и `WorkspaceService`. Мост — единственное место, где встречаются оба
      контекста; ArchUnit держит, что он не трогает сервисы записи modeling
      (`INV-ADV-004`). Отменено: «advisor-application зависит от
      modeling-application» — тогда сервисы записи были бы в classpath помощника,
      и границу держала бы только дисциплина
- ✅ **Клиент Anthropic — официальный Java SDK** (`com.anthropic:anthropic-java`)
      в `bootstrap.advisor`, за портом `LlmPort`. Отдельный модуль адаптера
      сломал бы раскладку «четыре слоя на контекст» (`ADR-0001`), а bootstrap
      и так единственный модуль со всеми зависимостями. Адрес API — параметр
      конфигурации: интеграционные тесты подменяют Anthropic на уровне HTTP
      (`HttpServer` JDK), без сети
- ✅ **Структурированный результат — tool use со строгой схемой, без
      принуждения.** Ревью — инструмент `report_finding`, вызов на каждую
      находку: каждый завершённый вызов уходит клиенту кадром потока сразу,
      поток находок, а не пачка в конце. План — инструмент `propose_plan`
      с перечислением типов фазы 1 в схеме. Подсказка и документация — текст
      потоком. `tool_choice: auto` плюс указание в системном промпте: принуждение
      (`any`/`tool`) новые модели отклоняют с `400`, а модель — параметр
      конфигурации
- ✅ **Поток — SSE на `POST`, отказы — до потока.** Проверки (роль, выключатель,
      бюджет, частота, размер контекста) идут синхронно и отвечают обычным
      `problem+json`; поток открывается только после них, поэтому отказ не может
      прийти обрывком (`INV-ADV-002`). Кадры: `token` (текст), `finding`, `plan`,
      `answer`, последним — `meta` (`SessionMeta`). Браузер читает поток `fetch`'ем:
      `EventSource` не умеет `POST` и заголовки
- ✅ **Бюджет**: строка `token_budget` на пару (пространство, месяц), расход
      списывается под блокировкой строки (`SELECT … FOR UPDATE`), иначе две
      параллельные сессии теряют расход (`TokenBudgetIT`). Лимит — столбец
      `workspace.ai_monthly_token_limit`; пусто — значение по умолчанию из
      конфигурации (5 000 000 по `spec/nfr/advisor.yaml`), а не «без лимита»:
      бюджет без верхней границы не ограничивает (комментарий `V1` расходится,
      миграция заморожена — правится здесь и в спеке). Выключатель —
      `workspace.ai_enabled`, по умолчанию `false`: отправка модели банка во
      внешний API включается администратором осознанно
- ✅ **Результат сессии хранится, промпт — нет.** Находки, план и ответ — поля
      агрегата, и повтор с тем же ключом отдаёт их без нового обращения к API
      (`INV-ADV-003`). `ModelContext` и описание пользователя не хранятся: от
      запроса остаётся отпечаток (SHA-256 параметров) — по нему повтор отличается
      от конфликта ключа. Уточнение `storage.retention` в `spec/nfr/advisor.yaml`
- ✅ **Предел контекста и `413`.** Контекст сверх `max_tokens` (120 000) усекается
      по слоям ArchiMate снизу вверх с флагом `truncated`; если после усечения
      в нём меньше `min_coverage` (четверть) элементов области — `413
      CONTEXT_TOO_LARGE` с предложением выбрать представление: ревью четверти
      модели читалось бы как ревью модели. Подсказка — элемент и соседи на два
      шага, при переполнении — на один. Ревью «по слоям с агрегацией» несколькими
      обращениями отложено: усечение с пометкой закрывает `INV-ADV-007`, а цена
      нескольких обращений на одно нажатие — отдельное решение
- ✅ **Роли.** `ReviewModel` доступен `VIEWER` — так в `UC-ADV-001` и `UI-015`
      («экспорт и помощник доступны»); таблица в `spec/nfr/advisor.yaml`
      расходилась и исправлена. `PlanModel` — только `ARCHITECT`/`ADMIN`: план
      фильтрует палитру, которой у `VIEWER` нет
- ✅ **Коды и статусы отказов:** `ADVISOR_DISABLED` — `403`, `BUDGET_EXHAUSTED` —
      `429` с датой сброса, `RATE_LIMITED` — `429` + `Retry-After`,
      `CONTEXT_TOO_LARGE` — `413`, `ADVISOR_UNAVAILABLE` — `503`. Описание `403`
      в OpenAPI («или бюджет исчерпан») расходилось с `UC-ADV-001` и §7.5
- ✅ **Один активный запрос на пользователя:** новая сессия отменяет поток
      предыдущей (сессия `FAILED`, израсходованное списывается — `INV-ADV-006`);
      частота — скользящее окно в памяти процесса (5/мин на пользователя,
      20/мин на пространство). Один экземпляр приложения (§10.1) — общий счётчик
      не нужен
- ✅ **Уведомление администратора** о 80 % и 100 % — событие, запись в журнал
      уровня `WARN` и метрика `advisor_budget_utilization_ratio` с алертом
      `BudgetNearlyExhausted`. Почты в стеке нет; канал уведомлений — мониторинг
- ✅ **Промпты — ресурсы, а не литералы:** `advisor/prompts/*.md` в classpath.
      Это текст на естественном языке, и правило «текст — в ресурсах» к нему
      применимо так же, как к сообщениям; язык ответа задаётся по `Accept-Language`
- ✅ Правка спеки: `ModelContext.views` (структура представлений без геометрии,
      §7.3 — без неё не найти элементы вне представлений), `PlannedRelation.permitted`
      («связь требует внимания», `UC-ADV-002`), пути `/ai/**` и настройки
      помощника в OpenAPI, роли, коды отказов; новый сценарий `UC-ADV-003`
      «Управление помощником и расход» (`ToggleAdvisor`, `SetTokenBudget`,
      расход для FR-53) — без него у настроек не было use case'а

### 5.1 Домен (`archi-advisor-domain`)

- ✅ `AdvisorSession` со state machine `REQUESTED → STREAMING → COMPLETED`,
      `REJECTED` только из `REQUESTED` — отказ принимается до расхода токенов
      (`INV-ADV-002`)
- ✅ `TokenBudget` по паре (`workspaceId`, `period`), `OK → WARNING → EXHAUSTED`;
      расход не уменьшается, возврат только повышением лимита (`INV-ADV-006`)
- ✅ `Finding` с обязательной ссылкой на объект модели (`INV-ADV-008`, FR-23)
- ✅ `AuditEntry` — ровно одна на сессию, включая отклонённые (`INV-ADV-001`,
      FR-27)
- ✅ `ModelContext` как анти-коррупционный слой с обязательным флагом
      `truncated` (`INV-ADV-007`)
- ✅ `ModelPlan`, `AdvisorAnswer`, `ElementRef`, `TokenUsage`, `BudgetPeriod`
- ✅ Результат помощника структурно не может содержать команд изменения модели
      (`INV-ADV-004`)

### 5.2 Сценарии и адаптеры

- ✅ `UC-ADV-001` — ревью модели, поток `Finding`
- ✅ `UC-ADV-002` — `ExplainElement`, `PlanModel`, `GenerateDocumentation`
- ✅ Сценарии в application-слое: `AdvisorService` (четыре функции одним порядком:
      роль → повтор ключа → отказы до API → поток → списание), `AdvisorAdminService`
      (`UC-ADV-003`), окно частоты и «один поток на автора» в памяти
- ✅ Порты `ModelContextReader`, `MetamodelCatalog`, `WorkspaceDirectory`, `LlmPort`,
      `PromptCatalog`, `TokenBudgetRepository`, `AdvisorSessionRepository`, `AuditLog`,
      `AdvisorSettingsRepository`, свой `UnitOfWork`
- ✅ Хранение: миграция `V4__advisor.sql` (`advisor_session`, `token_budget`,
      исход и ссылка на сессию в `ai_audit_log`), JPA-адаптеры; бюджет месяца —
      вставка «если нет» и `SELECT … FOR UPDATE`
- ✅ Построение контекста ([§7.3](../backend.md#73-контекст-запроса)): компактный
      JSON; при превышении лимита — подграф (для подсказки элемент и соседи
      на два шага, для ревью усечение по слоям с пометкой)
- ✅ Клиент Anthropic API, модель `claude-sonnet-5` параметром конфигурации
- ✅ Потоковая отдача SSE, первые токены ≤ 3 с (NFR-04, поле `firstTokenAt`) —
      `SseEmitter`, а не `StreamingResponseBody` (журнал); `AdvisorStreamingIT`
      меряет поток на настоящем Tomcat
- ✅ Структурированные результаты через tool use со схемой — `Finding[]`
      и `ModelPlan` приходят валидными без парсинга свободного текста; находка
      с целью вне контекста отбрасывается, тип плана вне фазы 1 — тоже, допустимость
      связи плана решает матрица
- ✅ Ключ Anthropic только на сервере, в браузер не попадает (NFR-06,
      `INV-ADV-009`)
- ✅ Эндпоинты `/ai/review`, `/ai/explain`, `/ai/plan`, `/ai/document`; `/ai/status`,
      `/ai/settings`, `/ai/usage` (`UC-ADV-003`)
- ✅ Мост к modeling на чтение (`bootstrap.advisor`): `ModelingContextReader`,
      `ArchimateMetamodelCatalog`, `ModelingWorkspaceDirectory`; `AdvisorArchitectureTest`
      держит, что ни модули advisor, ни мост не видят сервисов записи modeling
- ✅ Алерты `SlowFirstTokenP95`, `BudgetNearlyExhausted`, `HighRejectionRate`
      в `deploy/prometheus/alerts.yaml`

### 5.3 Лимиты и видимость ([§7.5](../backend.md#75-лимиты-расходов))

- ✅ Месячный бюджет `ai_monthly_token_limit`: 80 % — предупреждение
      администратору, 100 % — `429` с датой сброса (FR-51, `INV-ADV-005`)
- ✅ Ограничение частоты на пользователя, `429` + `Retry-After` (FR-52)
- ✅ Верхняя граница контекста: сужение до подграфа, иначе `413`
      с предложением выбрать представление (`INV-ADV-007`)
- ✅ Не более одного активного запроса на пользователя: повторное нажатие
      отменяет предыдущий поток
- ✅ `workspace.ai_enabled = false` убирает панель и закрывает `/ai/**` (`403`)
- ✅ Расход администратору в разрезе месяца, пользователя и функции (FR-53):
      `/ai/usage` и раздел «Помощник» на экране моделей у `ADMIN` — выключатель,
      лимит, расход по пользователям и функциям (отдельного экрана настроек
      пространства в продукте пока нет)
- ✅ События `AdvisorSessionCompleted`, `TokenBudgetThresholdReached`,
      `TokenBudgetExhausted`; уведомление о бюджете — `WARN` в журнал и метрика

### 5.4 Интерфейс помощника

- ✅ Панель помощника с потоковым выводом: под «Свойствами», поток читается `fetch`'ем
      (`EventSource` не умеет `POST`), новое обращение отменяет предыдущее; панели нет,
      если `/ai/status` говорит «выключен» или не отвечает
- ✅ Переход к элементу из находки: выделение на канве и в дереве
- ✅ Фильтрация палитры по типам из `ModelPlan` (FR-25)
- ✅ Кнопка «Собрать документацию» в карточке элемента (FR-26)
- ✅ Исчерпанный бюджет и недоступность Anthropic (`503`) не ломают редактор

### 5.5 Тесты

- ✅ Тесты по таблице «Маппинг на тесты» в
      [`advisor/invariants.md`](../../spec/domain/advisor/invariants.md) —
      `INV-ADV-001`…`INV-ADV-009`
- ✅ `/ai/**` при `ai_enabled = false` → `403`; Anthropic мокируется на уровне
      HTTP ([§9.3](../backend.md#93-интеграционные-тесты-testcontainers)) —
      `FakeAnthropic` на `HttpServer` JDK, SDK и разбор потока настоящие
- ✅ Построение контекста: размер, усечение, выбор подграфа ([§9.2](../backend.md#92-юнит-тесты))
- ✅ `e2e/advisor.spec.ts`: ревью выдаёт находки, переход к элементу выделяет его на канве
      и в дереве; план фильтрует палитру; исчерпанный бюджет не ломает редактор;
      выключенный помощник — панели нет; документация из карточки со ссылками
- ✅ Закрытие: README, CLAUDE.md, граф зависимостей, журнал; живая проверка jar
      на Tomcat с подменным Anthropic (без настоящего ключа — его нет)

---

## Этап 5a — серверный экспорт представлений

**Критерий готовности (§12):** экспорт совпадает с канвой в пределах допуска
визуальных тестов. **Закрыт 2026-10-12**, проверка — в журнале.

### 5a.0 Проектные решения этапа и правка спеки

- ✅ **Источник — снимок зафиксированной версии, а не таблицы.** §6.10 рисует
      «`ViewPayload` (БД) → `SvgWriter`», но выгрузка прибита к версии
      (`INV-IXC-008`): картинка строится из `.archimate` версии тем же читателем,
      что каталог CSV. Представление в запросе — UUID из API; его `archiId` берётся
      из modeling, а содержимое — из снимка. Представления нет в версии — `404`
      `IXC_VIEW_NOT_IN_VERSION`
- ✅ **`SvgWriter` — в `archi-interchange-application`, чистая Java.** Геометрия
      (`shapes.json`), токены (`design-tokens.yaml`) и спрайт (`icons.svg`) приходят
      значением `Notation`, которое собирает bootstrap из classpath, — в application
      нет ни Jackson, ни YAML. Метрики и контуры глифов — порт `Typesetter`,
      растеризация — порт `Rasterizer` (§6.10: ради возможной замены на `resvg`).
      Реализации на AWT, Batik и fontbox — в `archi-bootstrap` (`bootstrap.export`):
      отдельный модуль адаптера сломал бы раскладку `ADR-0001`, как и у клиента
      Anthropic на этапе 5
- ✅ **Шрифт — Inter, а не Archivo.** §6.10 называет Archivo и IBM Plex Mono,
      а нормативные токены (`spec/ui/design-tokens.yaml`) — Inter; прав `spec/`,
      §6.10 правится. Inter до сих пор не грузился вовсе: канва рисовала системным
      шрифтом, и сверка с сервером была бы невозможна. Статические TTF и WOFF2
      Inter 4.1 (OFL) лежат одним каталогом `ui/fonts/` в classpath: сервер читает
      TTF, канва подключает WOFF2 того же выпуска
- ✅ **PNG растеризуется из SVG с подписями в кривых.** Тот же писатель, режим
      `outline`: Batik не зависит от шрифтов системы образа, текст в PNG — те же
      контуры, что в SVG `outline=true`. В текстовом SVG шрифт вшит подмножеством
      (fontbox `TTFSubsetter`, только использованные глифы) с запасным стеком
- ✅ **Только SVG 1.1 без CSS.** Наконечники связей и ореол подписи — явной
      геометрией, а не `marker` с `auto-start-reverse`, `paint-order` и
      CSS-переменными: их не понимают Batik, Inkscape и Word. Иконки — копией
      символа на месте, `currentColor` и вырез подставлены значениями
- ✅ **Перенос подписи** — жадный по словам по метрикам Inter с кернингом; слово
      шире строки рвётся по символам; имя — две строки и многоточие, ярлык группы —
      одна строка капителью, заметка — без предела, обрезка по рамке. Порядок
      отрисовки — как у канвы: по уровням вложенности, на уровне сначала рёбра,
      потом узлы
- ✅ **Параметры и пределы.** `fmt` = `svg`|`png`, `scale` ∈ {1, 2, 4} (только PNG),
      `outline`, `background` = `paper`|`transparent`, `version`. Поля — шаг сетки
      (20) вокруг содержимого. PNG стороной больше 16 000 px (4 000 × 4 000 при
      `scale=4`) — `422 IXC_IMAGE_TOO_LARGE` с наибольшей допустимой плотностью;
      у SVG предела нет. Недопустимое значение параметра — `422 IXC_INVALID_EXPORT_OPTION`
- ✅ **Отчёт о потерях SVG/PNG** перечисляет то, что картинка передаёт не так,
      как модель: объект неподдержанного типа нарисован заглушкой, шрифт объекта
      заменён на Inter, подпись усечена, изображение узла не рисуется. Одиночная
      выгрузка — число записей в `X-Archi-Loss-Count`; пакет — `loss-report.json`
      в архиве
- ✅ **Пакет** — zip из файлов `<слаг>.<8 hex>.<ext>`, как имена в Git (§11.2):
      слаг транслитерируется в латиницу, 8 hex — из `archiId`. Функция слага —
      `Slug` в `archi-interchange-domain`, её же возьмёт этап 7a
- ✅ Правка спеки: `UC-IXC-004` «Экспорт представлений в картинку» (`ExportViewImage`,
      `ExportAllViewImages`), пути `/views/{id}/export` и `/models/{id}/views/export`
      в OpenAPI, коды отказов, §6.10 (шрифт, источник, растр из кривых)

### 5a.1 Шрифт

- ✅ Inter 4.1 (TTF и WOFF2, четыре начертания, лицензия OFL) в `ui/fonts/`;
      `@font-face` во фронтенде, визуальные эталоны пересняты (`-darwin`, `-linux`).
      Запасное семейство собственного шрифта объекта на канве — Inter, как у сервера

### 5a.2 Писатель SVG (`archi-interchange-application`)

- ✅ `Notation` и порт `Typesetter`; маршрут связи, точки перегиба и подпись —
      перенос `routing.ts` канвы
- ✅ `SvgWriter`: фигуры по `shapes.json` и токенам, иконки, связи с
      наконечниками, подписи, собственный стиль объекта (FR-21), отчёт о потерях
- ✅ `Slug` (`archi-interchange-domain`)

### 5a.3 Шрифты и растр (`archi-bootstrap`, `bootstrap.export`)

- ✅ Загрузка `Notation` из classpath; `AwtTypesetter` на Inter; подмножество шрифта
      для текстового SVG; `BatikRasterizer` → PNG @1x/@2x/@4x

### 5a.4 Сценарии и REST

- ✅ `ExportViewImage` и `ExportAllViewImages` в `ExportService`; `ExportJob` прибит
      к версии (`INV-IXC-008`), отчёт о потерях есть всегда (`INV-IXC-006`)
- ✅ `GET /views/{id}/export`, `GET /models/{id}/views/export`; доступно `VIEWER` (FR-41)
- ✅ `ExportJobTest#renderedJobAlwaysHasLossReport` (есть с этапа 1),
      `BatchViewExportIT#allImagesComeFromOneVersion`, `ViewImageExportIT`. Сервис картинок —
      отдельный `ViewImageService`: у него свои порты (нотация, шрифт, растр) и мост
      к modeling на чтение (`ModelQueryService.openView` — `archiId` представления)

### 5a.5 Интерфейс

- ✅ Меню «Экспорт»: модель `.archimate`, открытое представление SVG и PNG (×1, ×2),
      все представления архивом; при несохранённых правках меню предупреждает, что
      выгружается сохранённая версия. Имя файла — из ответа сервера. `e2e/view-export.spec.ts`

### 5a.6 Сверка канвы и экспорта (`UI-019`, FR-42)

- ✅ Серверный PNG пяти представлений эталона (01, 04, 05, 06, 10 — группы, вложенность,
      изломы, заглушки) — в `frontend/visual/export-parity/` с рамкой в `.json`;
      совпадение с писателем держит `ExportParityFixturesTest` (пересъёмка —
      `-Dexport.parity.update=true`, команда в javadoc теста)
- ✅ `visual/export-parity.spec.ts`: холст открывается на 100 % с рамкой эталона в углу,
      снимок сравнивается с серверным PNG в браузере — после размытия 3×3 доля разных
      пикселей ≤ 0,2 % и в худшем квадрате 24×24 ≤ 12 %. Шум сглаживания — до 6 %
      в квадрате, одна неверная подпись — от 23 %: проверено намеренной порчей
- ✅ Сверка нашла и исправила три расхождения писателя с браузером (журнал):
      многоточие строки, половина межстрочного зазора, словарные переносы канвы

### 5a.7 Закрытие

- ✅ README, CLAUDE.md, граф зависимостей, журнал; живая проверка jar в образе

---

## Этап 6 — фаза 2 метамодели: Motivation и Strategy

**Критерий готовности (§12):** `Capability` редактируется, а не только сохраняется.
**Закрыт 2026-10-13**, проверка — в журнале.

### 6.0 Проектные решения этапа и правка спеки

- ✅ **Фаза 2 включается в каталоге, а не новым кодом.** Хранение одинаково у поддержанного
      и opaque-объекта: типизированные столбцы плюс остаток (`ADR-0017`), флаг `supported`
      решает только, можно ли править. Матрица Archi уже содержит пары всех 14 типов
      Motivation и Strategy — `INV-MDL-007` для них действует без правок таблицы
- ✅ **`Location` и `Grouping` остаются вне фаз.** FR-08 их не называет, в ArchiMate 3.2
      это составные элементы вне слоёв; решение о них — отдельным требованием, не здесь.
      `PHYSICAL` и `IMPLEMENTATION` остаются фазой 3 и обрабатываются по FR-03
- ✅ **Флаг `supported` в базе — снимок каталога на момент импорта**, поэтому смена фазы —
      миграция данных `V5`: уже загруженные `Capability` иначе остались бы opaque навсегда.
      Отвергнуто: вычислять флаг при чтении — адаптер хранения узнал бы каталог,
      а сохранённая строка расходилась бы с тем, что отдаёт API
- ✅ **Силуэты — прямоугольная нотация ArchiMate с угловой иконкой**, как у фазы 1.
      Motivation — прямоугольник со срезанными углами (новый примитив `bevel`), включая
      `Meaning` и `Value` (облако и эллипс — их иконки). Strategy: `Resource` — структура
      (`node_radius`), `Capability`, `ValueStream`, `CourseOfAction` — поведение
      (`node_radius_round`). Примитив `bevel` — в канве и в `Outlines` сервера одним контуром
- ✅ Правка спеки: `UC-MDL-002` (создаётся тип фаз 1–2, отказ — фаза 3 и вне фаз),
      `shapes-contract.yaml` (полнота — фазы 1 и 2), `UI-020` (проверка на `Location`),
      `UC-ADV-002` (в план попадают поддержанные типы), OpenAPI, §9.1 о фикстуре

### 6.1 Метамодель (`archi-modeling-domain`)

- ✅ `ConceptDefinition.supported()` — фазы 1 и 2; `ArchitectureModel.addElement` создаёт
      `Capability`, отказывает `WorkPackage` (`TYPE_NOT_EDITABLE`)
- ✅ Тесты: `ArchiTypeRegistryTest` (поддержаны шесть слоёв, фаза 3 и `Location` — нет),
      тройки фазы 2 в `relation-matrix-cases.csv` (выписаны из гл. 6–7 ArchiMate 3.2;
      что матрица знает все типы каталога, держал `catalogAndMatrixAgree` и раньше),
      `ArchitectureModelTest` (Capability создаётся, правится и связывается, WorkPackage — нет).
      Заглушку в `SvgWriterTest` изображает `WorkPackage` вместо `Goal`. Флаг opaque
      валидатор берёт из строки, а не из каталога — его проверяет `PhaseTwoUpgradeIT` (6.2)

### 6.2 Хранение (`archi-bootstrap`, Flyway)

- ✅ `V5__phase_two_supported.sql`: `supported = true` у элементов 14 типов фазы 2.
      Остаток не трогается: импорт пишет его одинаково для поддержанного и opaque-элемента
- ✅ `PhaseTwoIT` (одним классом вместо двух): Capability переименовывается — выгрузка
      эквивалентна исходному файлу с новым именем; новая Capability создаётся, связь
      ApplicationComponent → Capability по матрице проходит, обратное назначение — `422`;
      `WorkPackage` и `Location` — `MDL_TYPE_NOT_EDITABLE`; миграция на строках «как до
      этапа 6» поднимает флаг фазе 2, не трогает фазу 3 и остаток, после неё Capability правится.
      Миграция в тесте исполняется текстом файла на импортированной модели, а не прогоном
      Flyway с `target=4`: состояние «до» воспроизводится одним `update`, а строки
      модели целиком руками собирать не нужно

### 6.3 Нотация (`shapes.json`, `icons.svg`, канва и `SvgWriter`)

- ✅ Примитив `bevel` (срез 8×8) в `shapes.ts` и `Outlines.java`; 14 записей `shapes.json`,
      14 иконок спрайта (у `Stakeholder` — цилиндр роли, как в ArchiMate)
- ✅ `SharedShapesTest` — запись обязательна для поддержанных типов (фазы 1 и 2);
      `shapes.test.ts` и `OutlinesTest` сверяют путь `bevel` с одной и той же строкой;
      заглушка в `shapes.test.ts` — `WorkPackage`. Галерея фигур показывает фазу 2
      своими силуэтами, заглушки — типы фазы 3; эталоны `-darwin` и `-linux` пересняты
      (Linux — в `mcr.microsoft.com/playwright:v1.64.0-noble` на копии клона без
      `node_modules`). Серверный PNG представления 11 глазами: Capability — оранжевый
      скруглённый прямоугольник с иконкой вместо пунктирной заглушки. Сверку экспорта
      не задевает: на её пяти представлениях типов фазы 2 нет

### 6.4 Интерфейс

- ✅ Палитра: шесть слоёв в порядке Archi («Стратегия», «Бизнес», «Приложения»,
      «Технологии», «Мотивация», «Прочее»), переключатель переносится на вторую строку.
      Дерево, «Свойства», канва и создание элемента работают по флагу `supported`
      от сервера и правок не потребовали
- ✅ Фикстура E2E (`reference-model.json`): у 69 Capability и 14 типов каталога —
      `supported: true`, 69 находок `MDL_OPAQUE_OBJECT` по Capability сняты, две по
      `Location` остались. Правка скриптом поверх снимка (пересборка JSON байт в байт
      проверена); сверка со снимком живого сервера — в 6.5
- ✅ `e2e/opaque-node.spec.ts` — на `Location` представления 10; `e2e/phase-two.spec.ts`:
      Capability переименовывается в «Свойствах» и уходит `PATCH` при сохранении; слои
      «Мотивация» и «Стратегия» в палитре, новая Capability ставится на представление
- ✅ Сценарий прокрутки дерева упал на новой фикстуре и вскрыл дефект
      переименования — исправлен отдельным коммитом, в журнале

### 6.5 Закрытие

- ✅ README, CLAUDE.md, граф зависимостей (не изменился), журнал; полный `./mvnw verify`
      и Playwright; живая проверка миграции на базе, заполненной jar'ом с `main`

---

## Этап 6a — экспорт в Open Exchange Format

**Критерий готовности (§12):** выгруженный файл валиден по XSD и открывается
сторонним инструментом; отчёт о потерях полон. **Закрыт 2026-10-13**, проверка — в журнале.
**Импорта из OEF нет** (`ADR-0008`) — не предлагать.

### 6a.0 Проектные решения этапа и правка спеки

- ✅ **Схема — официальная XSD 3.1** (`archimate3_Diagram.xsd` с включёнными
      `View` и `Model`, пространство имён `http://www.opengroup.org/xsd/archimate/3.0/`),
      скачана с opengroup.org и совпадает побайтово с копией в Archi 5.9. `xml.xsd`
      W3C лежит рядом и подставляется резолвером: в работе сервер в сеть за схемой
      не ходит
- ✅ **Источник — снимок зафиксированной версии**, как у CSV и картинок
      (`INV-IXC-008`): писатель читает `.archimate` версии тем же читателем
- ✅ **Ответ — zip из файла `.xml` и `loss-report.json`**, число записей отчёта —
      в `X-Archi-Loss-Count`. Отдельный `.xml` без отчёта нарушил бы FR-44 («каждая
      выгрузка сопровождается отчётом»): заголовок несёт число, но не содержание.
      Архив детерминирован, как у CSV
- ✅ **Переносится всё, что OEF умеет выразить, независимо от фазы.** `Location`,
      `Grouping` и типы фазы 3 в редакторе opaque, но в OEF у них есть свои типы —
      выбросить их значило бы потерять то, что формат выражает. Не переносится
      то, чего в OEF нет: остаток `raw_xml` (фрагменты XML, которых не знает
      читатель), представления не-ArchiMate (`SketchModel`, `canvas:*`), объекты
      представлений без аналога, атрибуты без аналога. Всё это — в отчёте
- ✅ **Отчёт полон по построению**: у каждого вида объекта — закрытый список
      атрибутов и значений, которые писатель переносит; всё прочее попадает
      в отчёт, а не молча отбрасывается. Новый атрибут Archi окажется в отчёте сам
- ✅ **Геометрия — абсолютная.** В `.archimate` `bounds` относительны родителю,
      в OEF — от начала представления; размер `-1` подставляется по типу из
      `shapes.json`, как на канве. Точки перегиба переводятся из смещений от центров
      в абсолютные той же формулой, что у канвы и Archi, — картинка та же, потери нет.
      OEF не допускает отрицательных координат: представление с ними сдвигается
      целиком, сдвиг — запись отчёта
- ✅ **Идентификаторы — буквально.** `xs:ID` требует NCName; идентификатор, который
      им не является (старые модели), заменяется на `id-<hex>` от него самого,
      замена — запись отчёта. Ключи свойств — `propertyDefinition` с `propid-N`
      в порядке первого появления
- ✅ **Невалидный по XSD результат не отдаётся** (FR-43): `500`
      `IXC_OEF_SCHEMA_VIOLATION`, выгрузка — `FAILED`, текст нарушения — в журнал
      сервера, а не клиенту
- ✅ Правка спеки: `UC-IXC-005` «Экспорт в Open Exchange Format» (`ExportOpenExchange`),
      `fmt=oef` в OpenAPI, вид объекта отчёта `folder` и `model` у `LossEntry`,
      таблица соответствий §3.5

### 6a.1 Схема и проверка

- ✅ XSD в ресурсах `archi-interchange-application`, `OefSchema` — проверка
      по ним без сети; тест: валидный образец проходит, нарушение ловится
      (`OefSchemaTest`). Адрес схемы, которого нет среди ресурсов, — ошибка загрузки,
      а не запрос в сеть: резолвер отдаёт только свои четыре файла

### 6a.2 Писатель (`archi-interchange-application`, пакет `oef`)

- ✅ `OefWriter` по таблице соответствий [§3.5](../backend.md#35-экспорт-в-open-exchange-format):
      папки → `organizations`/`item`, свойства → `propertyDefinitions` +
      `propertyDefinitionRef`, `name`/`documentation` с `xml:lang="ru"`,
      геометрия → `view`/`node`/`connection`, стиль → `style`. Размеры по умолчанию —
      из `Notation` (та же, что у SVG); перевод точек перегиба —
      `Routing.absoluteBendpoints`, открытый для этого из пакета `rendering`
- ✅ Отчёт о потерях (FR-44, `INV-IXC-006`): остаток `raw_xml` не переносится вовсе,
      атрибуты стиля без аналога, тип корневой папки в OEF не выражается. Узел без
      аналога не выгружается, а его вложенные поднимаются к ближайшему выгруженному
      предку — координаты абсолютные, картинка не меняется. `LossEntry` получил виды
      `model` и `folder`; `XmlEscaping` открыт для писателя OEF
- ✅ `OefWriterTest#rawFragmentsAreListedAsLosses` и остальные юнит-тесты писателя;
      эталон `docs/Hamkorbank_AS_IS_strict.archimate` и все фикстуры проходят XSD.
      В эталоне теряется только тип девяти корневых папок
- ✅ **Предварительная проверка Archi 5.9** (`Archi -application com.archimatetool.commandline.app
      --xmlexchange.import <файл> --saveModel <out>`): все шесть выгрузок — эталон и
      пять фикстур — Archi проверил по XSD и импортировал. Эталон после круга
      «наш OEF → Archi»: типы, свойства (620), документация (467), папки (50) и все
      `bounds` совпадают, точки перегиба легли в те же абсолютные точки.
      **Расхождение, которое не потеря:** Archi при импорте сам дорисовал 61 линию —
      все между вложенными объектами (связь, выраженная вложенностью, у Archi при
      импорте становится линией). В нашем файле этих линий нет и быть не должно:
      `NestingRelationship` в OEF — тоже линия с идентификатором, а в модели её нет.
      Идентификаторы папок Archi при импорте не сохраняет — это его выбор, в файле
      они есть

### 6a.3 Сценарий и REST

- ✅ `ExportService.openExchange`: роль, снимок, писатель, проверка, `ExportJob`
      с отчётом; `fmt=oef` больше не `IXC_FORMAT_NOT_AVAILABLE`. Схема и нотация —
      бины, схема компилируется один раз при старте. `X-Archi-Loss-Count` теперь
      у всех выгрузок модели (у `.archimate` — `0`): контроллер не ветвится по формату
- ✅ Невалидный результат — `ExportDefectException` → `500 IXC_OEF_SCHEMA_VIOLATION`
      в `InterchangeProblemHandler`; подробность нарушения — в журнал, клиенту — текст
      по ключу, `ExportJob` — `FAILED`. `Failure` modeling не расширялся: дефект
      сервера — не класс отказа сценария. `OefSchemaViolationIT` подменяет схему
      на отвергающую всё (`OefSchema.of`)
- ✅ Фикстуры `oef_export_*.xml` и `oef_export_*.losses.txt` для четырёх фикстур:
      валидность по XSD, совпадение с писателем, отчёт с ожидаемыми позициями
      (`OefExportFixturesTest`, пересъёмка `-Doef.fixtures.update=true`)
- ✅ `OefExportIT#lossReportListsRawFragments`, `#repeatedExportIsIdenticalAndLocalized`,
      `#referenceModelExportsThroughApi`. Сборка JSON отчёта — общая с пакетом картинок
      (`LossReportJson`)

### 6a.4 Интерфейс

- ✅ Пункт «Модель — Open Exchange (OEF), архив» в меню «Экспорт»; если сервер
      насчитал потери (`X-Archi-Loss-Count`), сообщение называет их число и где
      искать подробности. `e2e/view-export.spec.ts`

### 6a.5 Проверка сторонним инструментом и закрытие

- ✅ Ручная проверка: файл открывается сторонним инструментом. Живой прогон:
      jar с профилем `dev` на своей PostgreSQL, импорт эталона через API,
      `GET /models/{id}/export?fmt=oef` — `X-Archi-Loss-Count: 9`, архив из
      `hamkorbank-it-landshaft-as-is-v1.xml` и `loss-report.json` с причинами
      по-русски; Archi 5.9 файл проверил по XSD и импортировал, типы, свойства,
      документация и все `bounds` совпали с эталоном (плюс те же 61 линия
      вложенности, что дорисовывает сам Archi)
- ✅ **Ошибка, найденная живым прогоном:** имя `.xml` в архиве было именем модели
      по-русски, и системный `unzip` macOS отказался его создавать («Illegal byte
      sequence»): флаг UTF-8 в zip он не читает, как и старый Проводник Windows.
      Лечится слагом в латинице (`FileSlug.slug`, как у пакета картинок); имя самого
      архива остаётся человеческим — его передаёт `Content-Disposition` с `filename*`.
      Тест на это — `OefExportIT#lossReportListsRawFragments` (имя записи в архиве)
- ✅ README, CLAUDE.md, граф зависимостей (не изменился), журнал; полный `./mvnw verify`
      и Playwright

---

## Этап 7a — экспорт в Git

**Критерий готовности (§12):** повторный экспорт без правок даёт побайтово тот
же результат; цикл БД → Git → БД проходит без потерь.

### Решения этапа (2026-10-13)

Приняты при открытии этапа, до кода; обоснования — здесь, в спеке — следствия.

1. **Источник выгрузки — снимок версии, а не таблицы.** Публикуется `.archimate`
   зафиксированной версии (`INV-IXC-008`), разобранный в `ModelDocument`. YAML —
   **обратимое** кодирование документа: `GitYamlReader` собирает из дерева тот же
   документ, и писатель `.archimate` даёт те же байты, что снимок (отпечаток
   совпадает). На этом стоит восстановление очищенного снимка (FR-46): проверка
   — `sha256` восстановленного равен `content_hash` версии, иначе отказ.
2. **Порядок атрибутов и содержимого — часть формата.** Писатель `.archimate`
   выводит атрибуты в порядке документа, поэтому YAML хранит порядок: ключи
   `id`, `type`, `name` и `attributes` (в порядке файла) покрывают обычный случай
   Archi (`xsi:type name id …`); иной порядок — служебный список `attribute_order`,
   иное чередование содержимого — `content_order`. В модели из Archi их нет.
3. **«Сортировка свойств» из §11.2 — фиксированный порядок, а не алфавит.**
   Порядок `property` в Archi значим и входит в round-trip (FR-02); алфавитная
   сортировка ломала бы цикл БД → Git → БД. Детерминированность держит то, что
   порядок берётся из документа, а не из обхода хеш-таблицы.
4. **`position` наследуется из предыдущего коммита.** Снимок несёт плотный порядок
   документа, разреженного `sort_order` в нём нет. Поэтому позиции нового дерева
   выводятся из позиций того же объекта в HEAD: сохраняется наибольшая
   возрастающая подпоследовательность, новые встают в промежутки, и только когда
   места нет — родитель перенумеровывается шагом 1000. Вставка одного элемента
   даёт diff в одном файле; повтор без изменений — те же позиции.
5. **Каталог модели — `models/<слаг>.<8 hex>/`**, где 8 hex — от `ModelId`.
   Найден в HEAD — остаётся прежним и при переименовании модели: переименование
   меняет строку в `model.yaml`, а не двигает все файлы.
6. **Outbox — таблица `outbox_event` той же транзакцией, что версия**
   (`ADR-0003`, вариант 2). Событие `ModelVersionCommitted` записывается
   в outbox при сохранении; фоновый обработчик публикует версии по порядку внутри
   модели, at-least-once. Повтор после падения между push и записью `git_sha`
   находит коммит по трейлерам `Archi-Model-Id`/`Archi-Version` и не дублирует его.
   `git_sha` и закрытие записи outbox — одна транзакция; `GitSnapshotCommitted`
   после неё — уведомление, его потеря ничего не ломает.
7. **Расхождение — только по каталогу модели.** Чужой коммит в ветке, не трогающий
   `models/<каталог>/` (README, другая модель), не расхождение: наш коммит ложится
   сверху fast-forward'ом и ничего не перезаписывает. Коммит, тронувший каталог
   модели, или переписанная история (наш последний коммит больше не предок HEAD) —
   расхождение: запись outbox `DIVERGED`, последующие версии модели ждут за ней
   (до 7b/7c), база не страдает.
8. **`GitBinding` — своя таблица `git_binding`** (V6), а не столбцы `workspace`:
   агрегат interchange со своим состоянием (`status`, `last_commit_sha`,
   `last_checked_at`). Пустые `workspace.git_*` из V1 удаляются той же миграцией.
   Соответствие «субъект Keycloak → автор Git» — таблица `git_author`; без строки
   автор — субъект и адрес в домене из настройки.
9. **Адаптер JGit — в `archi-bootstrap`** (`bootstrap.git`), как Batik:
   восемнадцатый модуль менял бы `ADR-0001`. Порт `GitRepositoryPort` —
   в interchange-application. Чтение YAML — SnakeYAML с резолвером «всё строка»;
   запись — собственная, ради побайтового контроля.

### 7a.0 Блокирующее решение

- ✅ Закрыть [`ADR-0003`](../../spec/adr/0003-event-publishing.md): `direct`
      для Git-выгрузки не годится — окно потери события ломает правило очистки
      снимков (FR-47). Нужен `transactional_outbox`. Решение принимается
      **здесь**, чтобы не заводить outbox за шесть этапов до первого потребителя

### 7a.1 Формат (`INV-IXC-011`, `ADR-0006`)

- ✅ Раскладка [§11.2](../backend.md#112-формат-разложенный-yaml):
      `model.yaml`, `folders.yaml`, файл на элемент, на связь, на представление
- ✅ Имя файла `<слаг>.<8 hex от id>.yaml`, слаг транслитерируется в латиницу
- ✅ Принадлежность папке — **поле** `folder`, а не каталог; каталоги
      `elements/<слой>/` только чтобы не держать 400 файлов в одной директории
- ✅ Порядок — разреженное `position`, шаг 1000
- ✅ Неподдержанные сущности — поле `raw_xml` блочным скаляром (иначе цикл
      БД → Git → БД теряет данные)
- ✅ Комментарий с именами концов в заголовке файла связи
- ✅ `format_version` в `model.yaml`
- ✅ Детерминированный писатель: фиксированный порядок ключей, сортировка
      свойств, LF, без YAML-якорей и переносов по ширине (`INV-IXC-004`)
- ✅ Обратная сборка: `GitYamlReader` даёт тот же документ и те же байты
      `.archimate` на эталоне и всех фикстурах (`GitYamlWriterTest#documentSurvivesYaml`);
      эталоны деревьев — `fixtures/git_roundtrip/<фикстура>/`
- ✅ `.gitattributes`: для `models/**` текстовое слияние отключено (`INV-IXC-010`)
- ✅ Сгенерированный `.archimate` в репозиторий **не** кладётся; вместо него
      CI-задача, собирающая его артефактом на теге (`deploy/git/gitlab-ci.yml.example`:
      трейлеры помеченного коммита → `GET /models/{id}/export?version=N`)

### 7a.2 Механика

- ✅ Агрегат `GitBinding` (`CONFIGURED`/`UNREACHABLE`/`DISABLED`), настройки
      в рабочем пространстве, `SecretRef` + `SecretResolver` — токен в домене
      не появляется
- ✅ `GitRepositoryPort` на JGit без внешнего бинарника; bare-клон кэшируется,
      `fetch` перед каждой операцией
- ✅ `UC-IXC-003`: коммит на сохранение версии; автор — архитектор (соответствие
      Keycloak → git identity в настройках), сообщение — комментарий
      к сохранению, трейлеры `Archi-Model-Id` и `Archi-Version`
- ✅ Только fast-forward: force push, rebase и удаление веток запрещены
      (`INV-IXC-009`)
- ✅ Сдвинувшийся HEAD: сохранение в БД проходит, экспорт помечается как
      расхождение и требует 7b или 7c; чужой коммит не перезаписывается (FR-37)
- ✅ `git_sha` пишется в `ModelVersion` (`ModelVersionUpdater`)
- ✅ Один репозиторий на рабочее пространство, каталог на модель
- ✅ Outbox: `outbox_event` (V7) пишется той же транзакцией, что версия
      (`OutboxEventPublisher`); обработчик — `GitPublishingService.publishPending`
      по расписанию `archi.git.poll-interval`; `POST /models/{id}/git/push` —
      очередь модели сейчас, в том числе после разбора расхождения
- ✅ `.gitattributes`: правило `models/** -merge` ставится, не затирая чужих строк

### 7a.3 Ретеншен включается

- ✅ Очистка снимков включается только для рабочего пространства с настроенным
      и доступным репозиторием (FR-47, `INV-MDL-010`): `archi.modeling.retention-enabled`
      теперь `true`, доступность — живой `fetch` с записью исхода в привязку
      (`GitPublishingService.reachable`); `GitRetentionIT`
- ✅ Восстановление снимка из Git по `git_sha` прозрачно для пользователя
      (FR-46): порт modeling `SnapshotArchive` (в плане — `VersionSnapshotReader`),
      каталог модели из коммита собирается обратно и сверяется с `content_hash`
      версии; не совпал — `409`, репозиторий недоступен — `503`, вернуть неоткуда —
      `410`, как раньше; `GitExportIT#purgedSnapshotIsRestoredFromGit`

### 7a.4 Тесты

- ✅ `GitYamlWriterTest#writingTwiceProducesIdenticalBytes`,
      `#exportIsByteStable`, `#folderIsFieldNotDirectory`
- ✅ `GitPublisherTest#pushIsAlwaysFastForward` (и ещё семь сценариев: расхождение,
      переписанная история, повтор по трейлерам, недоступный репозиторий)
- ✅ `GitExportIT#repeatedExportProducesNoDiff`, `#historyIsAppendOnly`
- ✅ Фикстура `git_roundtrip`: цикл БД → YAML → БД → `.archimate` даёт исходный
      файл (FR-40, [§9.1](../backend.md#91-golden-file-тесты-round-trip--критический-контур)) —
      эталоны деревьев `fixtures/git_roundtrip/` и `GitRoundTripIT#databaseToGitToDatabaseIsLossless`
      на эталонной модели: из дерева коммита — те же байты, что выгрузка версии
- ✅ `SnapshotRetentionIT#purgeIsDisabledWithoutGitBinding` (остался, теперь без
      привязки, а не «до этапа 7a») и `GitRetentionIT` с настоящим репозиторием

### 7a.5 Порядок коммитов

1. ✅ `spec`: ADR-0003 принят, `events.yaml`, `UC-IXC-003`, контракт и роли
2. ✅ `feat(interchange)`: писатель и читатель YAML, позиции, эталон (7a.1)
3. ✅ `feat(git)`: `GitBinding`, V6, настройка привязки через API (`GitBindingIT`)
4. ✅ `feat(git)`: outbox, публикация на JGit, `git_sha`, расхождение (7a.2)
5. ✅ `feat(modeling)`: ретеншен с Git и восстановление снимка (7a.3) — вместе
   с `GitExportIT` и `GitRetentionIT`, которые проверяют и механику 7a.2
6. ✅ `test`: `GitRoundTripIT` — критерий этапа (7a.4)
7. ✅ `docs`: CI-задача `.archimate` на теге, README, CLAUDE.md, граф, журнал

---

## Этап 7b — импорт состояния по ссылке

**Критерий готовности (§12):** отчёт об изменениях, применение одной транзакцией.

### Решения этапа (2026-10-14)

Приняты при открытии этапа, до кода; обоснования — здесь, в спеке — следствия.

1. **Импорт по ссылке — сессия импорта с целевой моделью.** Спека это уже предусмотрела:
   у `ImportSession` есть `targetModelId`, `ImportApplied` порождает и `PullModelFromGit`,
   у `import_session` — столбец `target_model_id`. Применённая попытка — `APPLIED`,
   отклонённая — `REJECTED` с находками: это и есть «запись о неудачной попытке»
   (`INV-IXC-012`). Миграции не нужно.
2. **Два вызова: отчёт и применение.** `POST /models/{id}/git/pull/preview {ref}` строит
   отчёт и ничего не пишет. `POST /models/{id}/git/pull {ref, commitSha, baseHash, comment}`
   применяет. Подтверждение привязано к отчёту двумя значениями из него: применяется
   **ровно коммит отчёта** (ветка могла уехать между вызовами), и только если содержимое
   модели **то же, что при построении отчёта** (`baseHash`). Иначе — `409` с кодом
   `INV-IXC-012`: применилось бы не то, что архитектор видел. Без этих полей применить
   нельзя — значит, нельзя и без отчёта.
3. **Сравнение — с текущим содержимым базы**, собранным в `.archimate`, а не с последней
   версией: заменяется именно оно. Обычно они совпадают — сохранение собирает версию тем же
   путём; расходятся, если команды API изменили базу без сохранения.
4. **Отчёт — по объектам с идентификатором**: модель, папка, элемент, связь, представление;
   добавлен, удалён, изменён — с перечнем полей (`name`, `type`, `documentation`,
   `properties`, `folder`, `source`, `target`, …). Узлы и рёбра представления сворачиваются
   в поле `diagram` своего представления — по одному узлу отчёт нечитаем. Перестановка
   соседей — поле `order` родителя, а не изменение каждого сдвинутого. Отчёт — значение
   домена interchange (`ModelChanges`): им же воспользуется предпросмотр слияния 7c.
5. **Ссылка разрешается как в `git rev-parse`:** полное имя `refs/…`, тег, ветка, затем
   идентификатор коммита (полный или сокращённый, от 7 знаков). Тег раньше ветки — как
   у Git. Перед разрешением в кэш забираются все ветки и теги. В отчёте — вид ссылки
   (`BRANCH`/`TAG`/`COMMIT`) и полный идентификатор коммита.
6. **Каталог модели в коммите — по суффиксу `.<8 hex от ModelId>`**, как при восстановлении
   снимка. Нет каталога — `404` `IXC_GIT_MODEL_NOT_IN_REF`. Импортируется состояние **этой**
   модели; модель из чужого каталога — не этот сценарий.
7. **Репозиторий — только привязки пространства**: адрес и токен из `GitBinding`. Нет
   рабочей привязки — `409` `IXC_GIT_NOT_BOUND`. Импорт с произвольного адреса означал бы
   токены вне привязки.
8. **Применение — как откат к версии** (`VersionService.rollback`): снимок раскладывается
   в агрегаты, `replaceContent`, фиксация версии. Требуется блокировка автора, как у любой
   правки содержимого; роль проверяет interchange (`PullModelFromGit`), как у импорта файла.
   Комментарий версии — переданный, иначе «Импорт из Git: ссылка (7 знаков)». Одна
   транзакция: замена содержимого, версия, запись outbox, закрытие сессии. Отказ внутри
   неё — откат целиком и отдельной транзакцией сессия `REJECTED` с находкой.
9. **Строгость — как у импорта файла.** Проверка методологии та же (`MethodologyCheck`);
   `ERROR` в строгом режиме — отказ `422` `IXC_STRICT_IMPORT_REJECTED`, нечитаемое дерево —
   `422` `IXC_CORRUPT_DOCUMENT` (не `400`: запрос клиента в порядке, испорчено содержимое
   репозитория). Отчёт показывает находки заранее и признак `applicable`.
10. **Импорт головы ветки привязки разрешает расхождение.** Если применён коммит, который
    при применении — голова ветки привязки, то открытые записи outbox модели с меньшими
    номерами закрываются `SKIPPED` (их публикация откатила бы ветку), а голова становится
    последним известным коммитом привязки. Публикация учится двум правилам: чужая правка
    каталога **не** расхождение, если она уже содержится в последнем опубликованном коммите
    модели; и если каталог в голове ветки собирается в содержимое версии, версия получает
    этот коммит без нового. Содержимое не изменилось вовсе — новой версии нет, последняя
    получает `git_sha` головы сразу.
11. **Экрана нет — только API**, как у привязки в 7a. Экран Git (привязка, состояние
    выгрузки, отчёт импорта) — отдельной задачей интерфейса.

### 7b.1 Спека

- ✅ `UC-IXC-006` «Импорт состояния модели из Git по ссылке» (`004` — картинки, `005` — OEF),
      `FR-38` и покрытие `INV-IXC-012`, контракт: два пути, схемы отчёта и результата,
      коды `IXC_GIT_REF_NOT_FOUND`, `IXC_GIT_MODEL_NOT_IN_REF`; §11.3 и `UC-IXC-003` —
      как разрешается расхождение

### 7b.2 Отчёт об изменениях

- ✅ `ModelChanges.between(до, после)` в домене interchange: объекты по `archiId`,
      поля, свёртка представлений, порядок; `ModelChangesTest`

### 7b.3 Разрешение ссылки

- ✅ `GitRepositoryPort.resolve`: ветка, тег, коммит, сокращённый коммит; JGit забирает
      ветки и теги в кэш; `GitRefResolutionTest` на настоящем JGit

### 7b.4 Импорт: отчёт и применение

- ✅ Порт `ModelStates` (текущее содержимое, замена новой версией) и мост в bootstrap
      поверх modeling; `ModelImportService.current`/`replace`
- ✅ `GitImportService.preview`/`apply`: сессия с целевой моделью, проверка методологии,
      привязка подтверждения к отчёту, отказ — сессия `REJECTED`
- ✅ REST: `POST /models/{id}/git/pull/preview`, `POST /models/{id}/git/pull`
- ✅ `GitImportTest#applyIsAtomic`, `#appliedStateBecomesNewVersion`, `#staleReportIsRejected`
      (и ещё восемь: ветка ушла вперёд, блокировка, нечитаемое дерево, отказы, очередь);
      `GitImportIT#reportPrecedesApply`, `#failedApplyLeavesOnlyRejectedSession`, `#refusals`

### 7b.5 Расхождение разрешается импортом

- ✅ Закрытие старых записей outbox и голова привязки при импорте головы ветки;
      два правила публикации; `GitImportIT#importOfHeadResolvesDivergence`,
      `GitPublisherTest#foreignChangeAlreadyPublishedIsNotDivergence`, `#headCarryingVersionIsAdopted`;
      закрытие очереди — в коммите 7b.4 (`GitImportTest#importOfHeadSupersedesQueue`)

### 7b.6 Закрытие

- ✅ README, CLAUDE.md, граф зависимостей, журнал; полный `./mvnw verify`

---

## Этап 7c — предложения через ветку и слияние по сущностям

**Критерий готовности (§12):** непересекающиеся правки сливаются сами,
конфликты разрешаются по полям на холсте.

### Решения этапа (2026-10-14)

Приняты при открытии этапа, до кода; обоснования — здесь, в спеке — следствия.

1. **Предложение — ссылка в репозитории привязки**: ветка merge request'а, тег или голова
   ветки привязки при расхождении. Разрешается так же, как в 7b. Сценарий — `UC-IXC-007`,
   операция `MergeModelBranch` (в таблице ролей она уже есть с этапа 0).
2. **Три состояния.** «Наше» — текущее содержимое модели в базе (как в 7b: заменяется
   именно оно). «Их» — каталог модели в коммите ссылки. «База» — каталог модели
   в merge-base коммита последней опубликованной версии модели (`git_sha`) и коммита
   ссылки. Нет опубликованной версии, общего предка или каталога в нём — `409`
   `IXC_GIT_NO_MERGE_BASE`: сливать не с чем, нужен импорт по ссылке (7b).
3. **Слияние — значение домена interchange** (`EntityMerge`) над тремя `ModelDocument`.
   Объект — модель, папка, элемент, связь, представление, ключ — `archiId`. Поля: каждый
   атрибут отдельно (`name`, `type`, …), `documentation`, `properties` (список целиком —
   порядок значим), `raw_xml`, `folder` (родитель), у представления — `diagram` (узлы
   и рёбра целиком: попиксельное слияние бессмысленно). Поле изменено в одной ветви —
   берётся; в обеих одинаково — берётся; по-разному — конфликт по полю. Удалён в одной,
   изменён в другой — конфликт объекта «удалить / оставить с правками».
4. **Порядок детей** не конфликтует: основа — порядок той стороны, что его меняла (иначе
   наш), добавленное другой стороной встаёт после всех соседей, стоявших перед ним
   в её порядке (а не только после ближайшего: дописанное в конец остаётся в конце). «Оба набора сохраняются» — без перенумерации: `position` пишет писатель
   YAML при публикации, в документе порядок плотный.
5. **Каскад после разрешения:** связь без конца, узел представления без элемента или связи,
   ребро без концов удаляются; `targetConnections` чистится от удалённых рёбер. Каскад —
   отдельный список отчёта, не конфликт. Цикл папок (две ветви переложили папки друг
   в друга) — перенос «их» откатывается, это тоже запись каскада.
6. **Конфликт адресуется строкой `<archiId>:<поле>`** (`:*` — удаление). Отчёт принимает
   выборы (`OURS`/`THEIRS`) и пересчитывает результат — так интерфейс показывает итог
   своего выбора. Применение без выбора по каждому конфликту — `422`
   `IXC_MERGE_CONFLICTS_UNRESOLVED`; выбор по конфликту, которого нет, — `422`
   `IXC_INVALID_REQUEST` (отчёт устарел).
7. **Применение — как в 7b**: несёт `commitSha`, `mergeBaseSha`, `baseHash` из отчёта;
   содержимое изменилось — `409 INV-IXC-012`. Сессия импорта с целевой моделью, проверка
   методологии результата, `ModelStates.replace`, одна транзакция; отказ — сессия
   `REJECTED`. Комментарий по умолчанию — «Слияние из Git: ссылка (7 знаков)».
8. **Слитый коммит принимается моделью.** Последний слитый коммит хранится по модели
   (`git_model_merge`, миграция `V8`). Публикация: чужая правка каталога, входящая
   в слитый коммит, — не расхождение; слитый коммит, которого нет в голове ветки, —
   второй родитель нашего коммита. Так merge request в GitLab закрывается сам: его ветка
   становится предком ветки привязки, а текстового слияния не было.
9. **Слияние головы ветки привязки разрешает расхождение**, как импорт в 7b: если очередь
   модели стоит на расхождении, открытые записи outbox старше новой версии закрываются
   `SKIPPED` (новая их содержит — база накопительна), голова — последний коммит привязки.
   Без расхождения очередь не трогается: история версий в Git не теряет коммитов. Результат совпал с нашим
   (выбрано всё «наше») — версии нет, но слитый коммит записан: публикация следующей
   версии ляжет поверх головы, а не встанет в расхождение.
10. **Холст.** Отчёт несёт отметки объектов результата относительно нашего (`ADDED`,
    `REMOVED`, `CHANGED`) и по представлениям — добавленные узлы с абсолютной геометрией.
    Канва в режиме слияния красит узлы и рёбра по `archiId` элемента и связи, добавленные
    узлы рисует контуром; справа — панель слияния: сводка, конфликты с выбором по полю,
    каскад, «Применить». Применение требует блокировки и сохранённой сессии правки.
11. **Текстовое слияние запрещено процессом:** правило `models/** -merge` в `.gitattributes`
    ставит публикация с 7a. Слияние приложения не читает маркеров `<<<<<<<` — читатель YAML
    их не пропускает (находка `IXC_CORRUPT_DOCUMENT`).

### 7c.1 Спека

- ✅ `UC-IXC-007` «Слияние предложения из ветки по сущностям», покрытие `INV-IXC-010`,
      `FR-39`; контракт: два пути, схемы отчёта, конфликтов, каскада и отметок, коды
      `IXC_GIT_NO_MERGE_BASE`, `IXC_MERGE_CONFLICTS_UNRESOLVED`; §11.4, `UC-IXC-003`,
      `git_model_merge` в модели данных

### 7c.2 Слияние по сущностям

- ✅ `EntityMerge.merge(база, наше, их, выборы)` в домене interchange: поля, удаление
      против правки, порядок, каскад, циклы папок; `EntityMergeTest#disjointEditsMergeWithoutConflict`,
      `#sameFieldEditsProduceFieldLevelConflict` и остальные строки таблицы §11.4

### 7c.3 Git: общий предок и второй родитель

- ✅ `GitRepositoryPort.mergeBase`, второй родитель коммита в `push`; JGit; `GitMergeParentsTest`

### 7c.4 Сценарий слияния

- ✅ Порт `ModelMerges` и JPA (`V8__git_model_merge.sql`), `GitMergeService.preview`/`apply`;
      `GitMergeTest` на фейках. Общее с импортом по ссылке вынесено в `GitAttempts`
      (привязка, сеть, каталог в коммите, сессия, запись отказа), фейки портов —
      в `GitFakes`; у фейка репозитория появились родители коммитов

### 7c.5 Публикация после слияния

- ⬜ Чужая правка в слитом коммите — не расхождение; второй родитель; закрытие очереди
      при слиянии головы; `GitPublisherTest`

### 7c.6 REST

- ⬜ `POST /models/{id}/git/merge/preview`, `POST /models/{id}/git/merge`;
      `MergeRequestIT#conflictIsResolvedPerField`, `#disjointProposalIsMergedAndPublished`

### 7c.7 Холст и панель слияния

- ⬜ Пункт меню, панель слияния с выбором по полю, отметки на канве, контуры добавленных
      узлов; `e2e/merge.spec.ts`

### 7c.8 Закрытие

- ⬜ README, CLAUDE.md, граф зависимостей, журнал; полный `./mvnw verify`

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
- **2026-10-08. UC-IXC-001 противоречил `INV-IXC-007`.** Use case писал
  нарушения матрицы в отчёт уровнем `WARNING`, а инвариант отклоняет в строгом
  режиме только `ERROR` — строгий импорт не отклонил бы ничего. Исправлен UC:
  нарушение матрицы — `ERROR`, незнакомый тип — `INFO`. Правило «`ERROR` в мягком
  режиме не отказ» остаётся за инвариантом.
- **2026-10-08. Golden-file тесты — в `archi-bootstrap`, как в таблице
  «Маппинг на тесты» `INV-IXC-*`.** Кодек лежит в `archi-interchange-domain`,
  и его юнит-тесты там же, а `RoundTripGoldenFileTest` и
  `assertXmlEquivalent` — в `archi-bootstrap/src/test`: на этапе 2 тот же
  тест пойдёт через БД (импорт → таблицы → экспорт), и переносить его
  не придётся. Хелпер проверен собственным тестом (`XmlEquivalenceTest`):
  сравнение, которое прощает лишнее, сделало бы golden-file зелёным над
  сломанным round-trip.
- **2026-10-08. Первая версия `assertXmlEquivalent` считала значимыми пробелы
  в пустой папке.** Эталон пишет две пустые папки развёрнуто, и хелпер, для
  которого «у элемента без детей текст значим целиком», упал на
  `<folder …>⏎  </folder>` против `<folder …/>`. Правило поправлено: пробельный
  текст — форматирование везде, непробельный сравнивается дословно, с краевыми
  пробелами. Цена: `<documentation> </documentation>` и `<documentation/>`
  хелпер не различает — кодек это различие хранит, но golden-file его не
  проверяет. Что тест вообще способен упасть, проверено порчей писателя
  (выбрасывать `targetConnections`) — красный.
- **2026-10-08. Идентификаторы фикстур — 32 hex, как у Archi.** Эталон держит
  24-hex, и без фикстур с форматом самого Archi формат `archi_id`
  проверялся бы только юнит-тестом `ArchiId`. Генератор (одноразовый, в
  репозиторий не положен) подставлял `id-` + MD5 от символического имени:
  файлы детерминированы, а читаются как настоящие. Первый прогон генератора
  испортил метку `${name}` в `feature` — подстановка `{имя}` задела `${name}`;
  поймано глазами до коммита, фикстура перегенерирована.
- **2026-10-08. Этап 1 закрыт.** Эталон и пять фикстур проходят round-trip,
  второй цикл даёт тот же документ и те же байты, шаг CI «Golden-file
  round-trip (NFR-05)» стоит перед `mvn verify`. По ходу в спеке найдены и
  исправлены три дефекта, каждый из которых сломал бы round-trip на настоящих
  данных: формат `archi_id` (24 hex вместо любого допустимого для Archi),
  число корневых папок (восемь вместо девяти) и уровень находки матрицы
  в UC-IXC-001 (`WARNING`, при котором строгий импорт ничего не отклонял).
  Форма `ModelDocument` в спеке приведена к той, что держит порядок.
  **Что этап оставляет этапу 2:** применение документа к модели и обратная
  сборка (`ModelDocumentAssembler`), отображение незнакомых атрибутов
  в `raw_xml`, восстановление `targetConnections` из соединений в БД,
  проверку матрицы при импорте (`RelationMatrix.check` готов, но вызывать
  его из interchange нельзя — доменные модули друг от друга не зависят;
  это работа application-слоя) и обязательность девяти корневых папок
  в агрегате модели.
- **2026-10-08. Граф зависимостей по закрытии этапа 1 перерисован — без
  изменений.** `tools/render-depgraph.py` дал побайтово те же `.puml` и `.png`:
  этап не добавил ни одной зависимости. Кодек и матрица обходятся JDK (StAX),
  тесты — JUnit, который уже был. Картинка в README актуальна.
- **2026-10-08. Этап 2 начат с правки спеки.** Раскладка `.archimate` по таблицам
  потребовала решения, которого в спеке не было: куда деть всё, что не описано
  полями агрегатов, — `alpha`, `lineWidth`, `feature`, `profile`, свойства
  папок, узлы расширений и порядок всего этого. Принят
  [`ADR-0017`](../../spec/adr/0017-xml-residue.md): типизированные столбцы плюс
  непрозрачный остаток XML с позициями. Отклонены столбец на каждый атрибут
  (гонка за Archi), объект целиком строкой (нет редактирования) и исходный файл
  рядом с таблицами (две правды). Те же фикстуры показали пять расхождений
  спеки с форматом Archi — связь концом связи, ребро концом ребра, точки
  перегиба `startX…endY`, размер `-1`, ошибочный вид `JUNCTION`; спека
  исправлена до кода. Закрыты пункты 1–3 и 8 журнала: имя модели — 500 символов,
  UC-IXC-001 проходит `PARSED`, §4.3 относит Git к 7a, у FR-29 появились
  `INV-MDL-011` и `UC-MDL-007`. Решения записаны в §2.0.
- **2026-10-08. Домен modeling: изменения копит агрегат, пишет хранилище.**
  Модель на 400 элементов не переписывается целиком ради одного
  переименования: `TrackedMap` помнит добавленное, изменённое и удалённое
  с момента загрузки, репозиторий пишет только это. Представления — отдельный
  агрегат, модель знает о них `ViewRef` (папка и место в нумерации): без этого
  нельзя ни отказать в удалении непустой папки, ни дать новому объекту позицию
  в папке, где лежат и представления.
  Два решения, которых в спеке не было, приняты в коде и записаны здесь:
  **непустая папка не удаляется** (`MDL_FOLDER_NOT_EMPTY`, `409`) — Archi
  удаляет содержимое рекурсивно, но это тот же каскад, от которого
  `INV-MDL-004` отказывается для связей; и **элемент кладётся только в поддерево
  корня своего слоя** — иначе Archi при открытии переложит его сам и round-trip
  сломается на структуре (`INV-MDL-009`, «перекладка объектов»). Импорт этим
  не ограничен: что пришло в файле, то и хранится.
  Холст Archi пишет объекты в пространстве `canvas:`, а `ArchiType` знает лишь
  `archimate:` — для представлений, узлов и рёбер заведён `DiagramType`, правка
  внесена в `aggregates.yaml`. **Уникальность `archi_id` между таблицами база
  не держит** (у каждой таблицы свой `UNIQUE (model_id, archi_id)`): агрегат
  модели проверяет папки, элементы, связи и представления, агрегат представления —
  свои узлы и рёбра; столкновение узла с элементом исключает генератор
  (128 случайных бит), а импорт — читатель, отклоняющий дубль на разборе.
- **2026-10-08. Концы связи — `NO ACTION DEFERRABLE`, а не `RESTRICT`.**
  План (§4.2) требовал `ON DELETE RESTRICT`. У `RESTRICT` в PostgreSQL нет
  отложенной проверки, и на нём не работали бы две вещи: импорт, где связь
  ссылается на связь, вставленную позже (ассоциация к связи), и физическое
  удаление модели каскадом, где элемент и связь уходят одним оператором.
  `NO ACTION DEFERRABLE INITIALLY DEFERRED` даёт тот же отказ при удалении
  связанного элемента — проверено вживую, — только в конце транзакции.
  Отказ пользователю всё равно приходит раньше, из домена (`INV-MDL-004`, `409`);
  база — страховка, и её ошибка на коммите тоже отображается в `409`.
- **2026-10-08. Снимок версии — из базы, и он же источник выгрузки.**
  `UC-IXC-002` выгружает зафиксированную версию (`INV-IXC-008`), значит экспорт
  отдаёт снимок, а не собирает файл из текущих таблиц. Чтобы снимок проверял
  хранение, а не копировал вход, `VersionService.commit` перечитывает модель и
  представления из репозитория и только потом отдаёт их писателю. «Изменений нет —
  версия не создаётся» решается сравнением SHA-256 нового снимка с последним
  (`content_hash`): отдельный счётчик правок пришлось бы держать согласованным
  с каждой командой, а отпечаток согласован по построению.
- **2026-10-08. Остаток XML — сам XML, и значения в нём — текстом, а не атрибутом.**
  Формат остатка закрытый, поэтому записан тем же StAX, что и `.archimate`, без
  JSON-библиотеки в домене. Первая версия клала значения незнакомых атрибутов
  в атрибуты записи — и перевод строки внутри значения (`labelExpression` у `feature`
  его содержит) парсер нормализовал бы в пробел: тихая порча на втором круге.
  Значения легли текстом элемента, возврат каретки — ссылкой на символ
  (`NodeResidueTest`). Умолчания Archi учтены отдельно: EMF не пишет атрибут
  со значением по умолчанию (`x="0"`, `width="-1"`), поэтому сборщик пишет
  размеченные в остатке атрибуты всегда, а прочие — только отличные от умолчания.
  **Регрессия вероятна** у любого, кто «упростит» сборку до записи всех четырёх
  координат: эталон останется зелёным, а файл Archi с узлом по умолчанию получит
  лишние атрибуты.
- **2026-10-08. «Дерево одним запросом» (§4.3) — запросом на таблицу.** Буквально
  один запрос — `UNION` разнотипных строк или JSON-агрегат в базе — вернул бы
  разбор строк в Java вручную и отказ от JPA ровно там, где он полезен. Взято
  так: модель, папки, элементы со свойствами (`join fetch`), связи со свойствами,
  места представлений — пять запросов, их число от размера модели не зависит,
  N+1 нет. Смысл требования — не обходить ассоциации по одной — соблюдён.
- **2026-10-08. Failsafe и перепакованный jar.** Первый прогон `*IT` не нашёл
  `@SpringBootConfiguration`: после `spring-boot:repackage` классы лежат в
  `BOOT-INF/` внутри jar, а failsafe по умолчанию берёт именно jar. Лечится
  `classesDirectory = target/classes` в конфигурации failsafe модуля
  `archi-bootstrap`.
- **2026-10-08. `-parameters` у компилятора.** Первые тесты REST упали на
  «Name for argument of type UUID not specified»: Spring связывает `@PathVariable`
  по имени параметра, а имя есть в байткоде только с флагом `-parameters`.
  Его включает `spring-boot-starter-parent`, которого проект не наследует
  (ADR-0001: родитель свой). Флаг добавлен в `maven-compiler-plugin` родителя.
  Ловушка: инкрементальная сборка не перекомпилирует классы после смены флага —
  нужен `clean`.
- **2026-10-08. Граф зависимостей по закрытии этапа 2 перерисован.** Новое
  ребро между контекстами — `interchange-application → modeling-application`
  (§2.0) — и тестовые зависимости `archi-bootstrap` (Testcontainers 2.0.5,
  `spring-security-test`, MockMvc). Дублей и конфликтов версий нет. Скрипт
  `tools/render-depgraph.py` для цели `graph` модуля ищет SNAPSHOT-зависимости
  в `~/.m2`: перед ним нужен `./mvnw install -DskipTests`, иначе отказ
  разрешения зависимостей.
- **2026-10-08. Этап 2 закрыт.** Критерий §12 выполнен и проверен дважды —
  тестом через API и вживую. Пункты 1–3, 5, 6, 8 журнала разногласий спеки
  закрыты; п. 9 (`ADR-0003`) по-прежнему открыт — `direct` работает, outbox нужен
  к 7a. **Что этап оставляет следующим:** подписи групп и текст заметок живут
  в остатке XML и в `ViewPayload` не попадают — канва этапа 3 типизирует `name`
  и `content` узла; браузерный вход (OIDC-клиент SPA) — этап 3; авторазметка —
  этап 4; экспорт картинок — 5a; OEF — 6a (сейчас `422 IXC_FORMAT_NOT_AVAILABLE`);
  спаны трассировки не экспортируются — корреляция логов идёт по `traceparent`.
- **2026-10-09. Этап 3: доработки сервера сделаны отдельным потоком.** Четыре
  пункта раздела 3.0 (идентификаторы от клиента и ребро существующей связи,
  `label`/`content` узла с миграцией `V3`, `placements` в дереве, `/me` и
  `/ui-config`). Расхождения с заданием, которые важны фронтенду: повторное
  размещение уже размещённого элемента отвечает `200` с **существующим** узлом
  и его идентификаторами — интерфейс поэтому не допускает дублей сам и не шлёт
  такой команды; уникальность `id` проверяется по всей базе (это первичный
  ключ); узлы, импортированные до `V3`, отдают `label` пустым, пока модель
  не переимпортирована — текст остаётся в остатке XML, выгрузка не меняется.
- **2026-10-09. Дефект: бесконечный цикл обновлений на холсте.** Первая
  версия канвы применяла изменения выделения от React Flow и одновременно
  выводила выделение узлов из документа. Перетаскивание *невыделенного* узла
  запускало пинг-понг двух источников и роняло React («Maximum update depth»).
  Лечится единственным источником: выделение меняют только явные события
  (клик по узлу, ребру, полю, начало перетаскивания, конец рамки), изменения
  `select` от React Flow не применяются, кроме как во время рамки.
  **Регрессия вероятна** у любого, кто «упростит» выделение до `onSelectionChange`.
- **2026-10-09. Подпись узла шириной в фигуру минус иконка.** Первая область
  подписи отступала справа на 36 px, и в узлах шириной 90 px с эталона имя
  резалось после пяти букв. Отступы сужены до 22 px справа и 8 px слева,
  иконка сдвинута на 7 px от края.
- **2026-10-09. История — данные, а не замыкания.** План (§6.9) описывал операцию
  парой функций `apply`/`revert`. Взято иначе: операция — список изменений
  «было → стало» по сущностям, обратное — перестановка сторон. Из той же записи
  выводятся и правка документа, и команды сервера, а у изменения, которое сервер
  делает сам (узлы удалённого элемента), есть пометка `implicit` в одну сторону:
  удаление уходит одной командой, а отмена возвращает узлы и рёбра явно.
- **2026-10-09. `bench` из vitest 5 убран.** Замер поиска по дереву написан
  обычным тестом с порогом (`perf/tree-search.bench.ts`, медиана 20 прогонов), замер
  канвы — сценарием Playwright (`perf/canvas-pan.bench.ts`: fps в node не измерить).
  Имена файлов — как в маппинге правил; vitest исключает файл канвы, Playwright
  берёт только его.
- **2026-10-09. «Против эталона Archi» — пока против собственного эталона.**
  Снимков отрисовки самого Archi в репозитории нет, и снять их в CI нельзя (Archi —
  настольное приложение). `visual/shape-gallery` сравнивает галерею силуэтов с
  утверждённым снимком в репозитории: изменение силуэта или иконки видно
  разницей пикселей и требует осознанного обновления эталона. Сверка с Archi
  глазами — по галерее `?gallery` рядом с макетом «классический стиль».
  Допуск снимка — 0,1 %: при 1 % проходили замена шрифта и заливка соединения.
- **2026-10-09. Дефект: полоса захвата ребра перехватывала клик по узлу.** Рёбра
  стояли на `zIndex 1000`, и невидимая полоса шириной 12 px над узлом забирала
  клик, если связь проходила через фигуру. Ребру теперь назначается уровень
  глубже лежащего конца: над контейнерами, но не над узлами своего уровня.
- **2026-10-09. Дефект: «Сохранить» исчезала на время отправки.** Видимость кнопки
  зависела от «можно править», а на время синхронизации правка закрыта — кнопка
  пропадала, и надписи «Сохранение…» не было видно никогда. Нашёлся по мигающему
  E2E, который принимал исчезновение кнопки за конец сохранения.
- **2026-10-09. Атрибуция React Flow не скрывается.** Скрывать её лицензия
  разрешает только по подписке Pro; ссылка перенесена в левый нижний угол холста.
- **2026-10-09. Вход через Keycloak в браузере вживую не проверен.** Порты 8080
  и 5173, единственные в `redirectUris` клиента SPA, на машине разработки заняты
  соседним проектом. Сервер проверен `KeycloakIT` и `SessionIT`, клиент — сборкой;
  первый прогон `docker compose up` с входом — задача ближайшей проверки этапа 4.
- **2026-10-09. Этап 3 закрыт.** Критерий §12 «реальная модель открывается и
  редактируется» выполнен на эталоне вживую: открыто представление 01, узел
  перемещён, отменён и повторён, элемент создан с палитры, существующий размещён
  из дерева, элемент переименован, перенесён в новую папку, удалён — и всё
  сохранено на сервер версиями 2–4. **Что этап оставляет следующим:** рисование
  связей, вложенность и авторазметка (4); правка собственного стиля, групп и
  заметок (паритет, 4); удаление и создание представлений из дерева (API нет);
  Playwright в CI (4); живой прогон входа через Keycloak. Полный `./mvnw verify`
  зелёный: 237 юнит-тестов сервера, 43 интеграционных, 52 юнит-теста фронтенда
  фазой `test`; Playwright — 43 сценария. Граф зависимостей перерисован и
  не изменился.
- **2026-10-12. Этап 4: решения и правка спеки до кода.** Авторазметка считается
  на сервере, а применяется в браузере (`ADR-0019`): `POST /views/{id}/auto-layout`
  принимает граф представления из документа браузера и отвечает предложением
  геометрии, ничего не записывая. Вариант «сервер раскладывает и сохраняет» отменён:
  отмена до сохранения разошлась бы с базой молча. Команды правки представления
  собраны в новый сценарий `UC-MDL-008`; подразумеваемую связь вложения называет
  метамодель флагом `nesting` в `/metamodel/relations`, интерфейс правило не повторяет.
- **2026-10-12. Расхождение плана: соединитель — элемент, а не узел без элемента.**
  План этапа называл `Junction` «узлом без `elementId`». В Archi соединитель — концепт
  папки `Other`, на представлении — `DIAGRAM_OBJECT` над ним; так и сделано, размер
  15×15, имени нет.
- **2026-10-12. Дефект: цель связи под указателем — ребро вместо узла.** Первая версия
  искала цель протягивания первым попавшимся элементом под указателем, и невидимая
  полоса захвата ребра, проходящего через фигуру, превращала связь с элементом в связь
  со связью (в списке оставалась одна ассоциация). Узел теперь сильнее ребра.
  **Регрессия вероятна** у любого поиска цели через `elementsFromPoint`.
- **2026-10-12. Дефект: полосы протягивания связи не появлялись.** Узел узнавал, можно
  ли править, из модульной переменной, которую канва выставляла в эффекте, — уже после
  первой отрисовки узлов, и `memo` их не перерисовывал. Флаг теперь в данных узла.
- **2026-10-12. Дефект: рамка React Flow вокруг выделенного перехватывала клики.**
  После выделения рамкой React Flow накрывает выделенное прямоугольником, и Ctrl+клик
  по узлу внутри не доходил до узла. Прямоугольник гасится по концу рамки.
- **2026-10-12. Направляющие — только без привязки к сетке.** Две привязки сразу спорили
  бы, куда поставить узел; с сеткой выравнивает сетка, без неё узел по отпусканию встаёт
  точно по направляющей к краю или середине соседа.
- **2026-10-12. Двойной щелчок по точке перегиба ловится вручную.** `preventDefault`
  на `pointerdown` ручки, без которого холст начинал панорамирование, гасит браузерный
  `dblclick`; два нажатия без движения за 400 мс считаются двойным щелчком.
- **2026-10-12. Мигающие E2E: автопрокрутка у края холста.** Узел у края при
  перетаскивании включает автопрокрутку React Flow, и смещение перестаёт совпадать
  с путём указателя. Сценарии, где важно точное смещение, берут узел у середины.
  Тело multipart с файлом Playwright в перехвате не отдаёт — сценарий импорта
  проверяет имя файла в запросе, а побайтное совпадение — у скачанной выгрузки.
- **2026-10-12. Визуальный эталон под Linux.** Снимок `visual/shape-gallery` есть
  в двух вариантах — `-darwin` с машины разработчика и `-linux` из образа
  `mcr.microsoft.com/playwright` той же версии, что `@playwright/test`: шрифты
  и сглаживание у систем разные, и один эталон на обе не годится.
- **2026-10-12. Дефект: ребро к вложенному узлу перехватывало клики по соседям.**
  Нашёлся живым прогоном на настоящем сервере, а не E2E. Канва ставит ребру уровень
  по глубине концов (правка этапа 3), а React Flow в режиме по умолчанию прибавляет
  к нему ещё и уровень вложенного конца: ребро к узлу глубины 1 вставало на уровень 2,
  над соседями этого узла, и его полоса захвата забирала клик по фигуре под линией.
  Лечится `zIndexMode="manual"`: порядок слоёв задаёт только глубина. **Регрессия
  вероятна** при обновлении React Flow или «упрощении» пропсов холста — её ловит
  `e2e/nesting.spec.ts` (тест проверен на старом коде: падает 2 против 1).
- **2026-10-12. Этап 4 закрыт.** Критерий §12 — сценарии §9.4 — выполнен:
  `e2e/acceptance.spec.ts` зелёный вместе со сценариями правил `UI-003`…`UI-006`.
  Сверх E2E — живой прогон в браузере против настоящего сервера: `docker compose`
  на свободных портах (8080/8081/5432/5173 по-прежнему заняты соседним проектом;
  его контейнеры не трогались, адрес возврата 18080 разрешён только в запущенном
  Keycloak командой `kcadm`, realm в репозитории не менялся), вход через Keycloak
  пользователем `architect` — **вход в браузере проверен вживую впервые**, импорт
  эталона, связь протягиванием (8 допустимых типов), группа, вложение, стиль,
  авторазметка, «Сохранить» — версия 2, в выгрузке группа и `fillColor`. Прогон нашёл
  дефект уровня рёбер (выше). Полный `./mvnw verify` зелёный: 260 юнит-тестов
  сервера, 48 интеграционных, 62 юнит-теста фронтенда; Playwright — 67 сценариев,
  в Linux-образе Playwright тоже. Граф зависимостей перерисован и не изменился.
  **Что этап оставляет следующим:** создание и удаление представлений из дерева
  (API нет); соединения без связи модели (заметка → элемент) не создаются, хотя
  импортированные хранятся и рисуются; тип соединителя «или» не правится.
- **2026-10-12. Первый прогон Playwright в CI упал на двух сценариях.** Визуальный
  эталон, снятый в образе `mcr.microsoft.com/playwright`, разошёлся на 2 % с голым
  `ubuntu-latest`: шрифты раннера другие. Работа `e2e` теперь идёт внутри того же
  образа — эталон, CI и проверка под Docker совпадают. Замер открытия представления
  на 500 объектов дал на раннере 1,0–1,5 с при цели в секунду: раннер меряет себя,
  а не код, поэтому в CI порог 2 с — он ловит регрессию в разы, не шум; цель NFR-01
  проверяется на машине разработчика.
- **2026-10-12. Этап 5 начат: сервер помощника.** Решения этапа — раздел 5.0 плана.
  Расхождения спеки, исправленные до кода: роли ревью (таблица в `spec/nfr/advisor.yaml`
  против `UC-ADV-001` и `UI-015`), статус исчерпанного бюджета (`403` в OpenAPI против
  `429` в §7.5), «пустой лимит = без ограничения» в комментарии замороженной `V1`
  против лимита по умолчанию в `spec/nfr/advisor.yaml`; у настроек не было use case'а —
  заведён `UC-ADV-003`.
- **2026-10-12. Расхождение плана: выключатель по умолчанию — `false`.** Агрегат
  `TokenBudget` в спеке держит `advisorEnabled` по умолчанию `true`, а столбец
  `workspace.ai_enabled` с этапа 0 — `false`. Оставлен `false`: отправка модели банка
  во внешний API включается администратором осознанно (`UC-ADV-003`). Выключатель
  и лимит хранятся в `workspace`, бюджет месяца — только расход.
- **2026-10-12. Отложено: предохранитель (circuit breaker) на Anthropic API.**
  `spec/nfr/advisor.yaml` описывает его вместе с алертом `UpstreamCircuitOpen`;
  в стеке нет resilience4j, а повторы до первого токена и честный отказ (`503`)
  уже даёт SDK. Алерт не заведён — без предохранителя ему нечего показывать.
- **2026-10-12. Ловушка тестов: окно частоты общее на контекст Spring.** Интеграционные
  тесты помощника делят один контекст и одного автора, и окно «5 в минуту» роняло
  чужие тесты с `429`. Базовый класс расширяет окно через `@TestPropertySource`,
  частоту проверяет отдельный класс со своим окном. `@DynamicPropertySource` для
  этого не годится: он сильнее `@TestPropertySource` наследника и переопределить его
  нельзя.
- **2026-10-12. Дефект: поток помощника уходил одним куском в конце.** Нашёлся живым
  прогоном собранного jar на Tomcat с подменным Anthropic, а не тестами: MockMvc
  собирает ответ целиком и разницы не видит. Spring 7 передаёт `StreamingResponseBody`
  обёртку `StreamUtils.NonFlushingOutputStream`, чей `flush()` ничего не делает, —
  заголовки и все кадры приходили клиенту по концу ответа модели, и первые токены
  ждали весь ответ. Лечится `SseEmitter`: каждый кадр уходит сразу. **Регрессия
  вероятна** при «упрощении» обратно на `StreamingResponseBody` и при любом новом
  потоковом ответе — ловит `AdvisorStreamingIT` (настоящий порт, профиль `dev`,
  подменный API с паузами между кадрами; на старом коде падает).
- **2026-10-12. Живой прогон помощника.** Собранный jar на свежем `postgres:16`
  (порт 55432 — штатные заняты соседним проектом), профиль `dev`, подменный
  Anthropic на `ThreadingHTTPServer` Python с паузами между кадрами: `V4` применилась,
  выключенный помощник — `403 ADVISOR_DISABLED` с записью аудита, после включения
  ревью идёт потоком (заголовки через 0,45 с, фрагменты — в темпе модели), находка
  указывает на элемент, бюджет месяца — сумма расходов сессий, ключа в журнале нет.
  Панель в браузере против настоящего сервера: находка пришла, «Перейти к элементу»
  выделила его в дереве. С настоящим Anthropic API не проверялось — ключа нет.
- **2026-10-12. Конфликт версий в графе после клиента Anthropic.** Перерисовка графа
  показала красное ребро: `org.jetbrains:annotations` 13.0 от Kotlin stdlib против
  17.0.0 от `duct-tape` Testcontainers. Метаданные компиляции, на работу не влияют;
  версия закреплена в `dependencyManagement` родителя — граф снова без конфликтов.
- **2026-10-12. Этап 5 закрыт.** Критерий §12 — четыре функции работают, аудит
  пишется — выполнен: ревью, подсказка, план и документация проходят интеграционными
  тестами через настоящий SDK против подменного API и живым прогоном jar; запись
  аудита — на каждое обращение, включая отклонённые. Полный `./mvnw verify` зелёный:
  314 юнит-тестов сервера, 65 интеграционных, 65 юнит-тестов фронтенда; Playwright —
  73 сценария. Граф зависимостей перерисован. Миграции `V3` и `V4` — в `FROZEN`
  проверки комментариев: обе применены. **Что этап оставляет следующим:** проверка
  с настоящим Anthropic API (ключа не было); предохранитель на внешний API; ревью
  большой модели несколькими обращениями «по слоям»; отдельный экран настроек
  пространства — пока раздел «Помощник» живёт на экране моделей у `ADMIN`.


- **2026-10-12. Этап 5a: шрифт — Inter, а не Archivo.** §6.10 называл Archivo и IBM Plex Mono,
  нормативные токены — Inter; прав `spec/`, §6.10 исправлен. Выяснилось, что Inter
  на канве вообще не грузился: `@font-face` не было, и холст рисовал системным шрифтом —
  на macOS одним, в Linux другим. Теперь Inter 4.1 лежит в `ui/fonts/` (TTF для сервера,
  WOFF2 для канвы, один выпуск), эталоны галереи пересняты.
- **2026-10-12. Расхождение с планом: картинка — из снимка версии, а не из таблиц.**
  §6.10 рисовал «`ViewPayload` (БД) → `SvgWriter`», но выгрузка прибита к версии
  (`INV-IXC-008`), а пакет не должен смешивать состояния. Писатель читает `.archimate`
  версии тем же читателем, что каталог CSV; `archiId` представления берётся из modeling.
- **2026-10-12. PNG — из SVG в кривых.** Batik получает файл без текста: не зависит от шрифтов
  образа, и текст в PNG — те же контуры, что в `outline=true`. В текстовом SVG шрифт вшит
  подмножеством (fontbox) без GSUB/GPOS — их ссылки на глифы после перенумерации были бы
  битыми; поэтому получатель текстового SVG видит строки без кернинга, на долю пикселя
  уже расчёта. Для точного совпадения — `outline=true` или PNG.
- **2026-10-12. Дефекты писателя, найденные сверкой с канвой.** Регрессия вероятна при любой
  «упрощающей» правке раскладки подписи — ловит `visual/export-parity.spec.ts`:
  1. **Многоточие.** Chrome при `line-clamp` выравнивает строку *без* многоточия и ставит
     его следом — у центрированной подписи оно выходит за край и обрезается
     («комитета..»). Писатель центрировал строку вместе с «…» — сдвиг на полсимвола.
  2. **Половина межстрочного зазора** у браузера округляется вниз до пикселя, а высота
     строки квантуется до 1/64 px (16,890625 вместо 16,9). Без этого однострочные
     подписи стояли на 0,6 px ниже, ярлыки групп — на 1 px.
  3. **Словарные переносы канвы** (`hyphens: auto`) зависят от платформы: в Chrome
     macOS есть русский словарь («Технологиче-ский»), в Linux — нет. Сервер словарь
     повторить не может; канва переведена на `hyphens: manual` — слово шире строки
     рвётся по символам и там, и там.
- **2026-10-12. Сверка идёт с дробными ширинами глифов.** Headless Chromium в Linux по умолчанию
  хинтует шрифт и округляет ширины до пикселя: строки шире, «юр» уходит на вторую строку,
  расхождение 0,6–1,4 % против 0,03 % на macOS. Браузеры Windows и macOS считают дробные
  ширины, как сервер, поэтому сверка запускает Chromium с `--font-render-hinting=none`.
  Пользователь Chrome в Linux увидит на канве переносы, отличные от экспорта, — честная
  зона расхождения, как и предупреждал §6.10.
- **2026-10-12. Известная разница: подзаголовок заглушки.** Канва подписывает объект
  неподдержанного типа русским названием типа и «только перемещение», сервер — английским
  именем типа и «тип не поддержан»: русских названий типов у сервера нет (они в i18n
  фронтенда). Такие объекты и так перечислены в отчёте о потерях; в сверке это пятно
  до 6 % квадрата у вида 10 — ниже порога.
- **2026-10-12. Риск: память на PNG предельного размера.** Предел §6.10 — 16 000 px
  по стороне, то есть до 1 ГиБ под растр ARGB. Предел соблюдён буквально; если упрёмся
  в память контейнера, сужать предел — правкой спеки, а не кода. Пакет PNG отклоняется
  целиком, если хоть одно представление больше предела, — с `viewArchiId` в ответе.
- **2026-10-12. Ловушка: Playwright в образе по рабочему каталогу.** Запуск
  `mcr.microsoft.com/playwright` с bind-mount рабочего клона, где лежат `target/`
  и `archi-bootstrap/.node/`, идёт втрое дольше (3 мин против 40 с) и роняет по таймауту
  до 14 случайных сценариев правки: Vite в контейнере следит за всем деревом через
  файловую систему Docker Desktop. На чистом checkout (как в CI) — 81 из 81 за 43 с,
  проверено на `git worktree`. Это свойство локального запуска, а не кода.
- **2026-10-12. Живая проверка экспорта в образе.** Собранный jar в `eclipse-temurin:25-jre`
  (образ из `Dockerfile`) на свежем `postgres:16`, профиль `dev`: импорт эталона, SVG
  текстом и кривыми, PNG ×2, прозрачный фон, архив всех двенадцати представлений в PNG ×2
  за 3,8 с (цель `ExportAllViewImages` — p50 4 с), отказ `scale=3` — `422` на языке
  `Accept-Language`. Шрифты образа не нужны: Inter приходит в jar, `fontconfig` в образе
  есть. Серверная половина сверки на Linux-JVM совпала с эталонами, снятыми на macOS.
- **2026-10-12. Конфликт версий в графе после Batik.** `commons-io` 2.17.0 от
  `xmlgraphics-commons` против 2.20.0 от `commons-compress` (Testcontainers); закреплена
  2.20.0 в `dependencyManagement` родителя — граф снова без конфликтов.
- **2026-10-12. Этап 5a закрыт.** Критерий §12 — экспорт совпадает с канвой в пределах
  допуска визуальных тестов — выполнен: `visual/export-parity.spec.ts` зелёный на macOS
  и в образе Playwright CI, `ExportParityFixturesTest` держит эталоны сервера. Полный
  `./mvnw verify` зелёный: 337 юнит-тестов сервера, 69 интеграционных, 65 юнит-тестов
  фронтенда; Playwright — 81 сценарий. Граф зависимостей перерисован. **Что этап оставляет
  следующим:** русские названия типов на сервере (подзаголовок заглушки в экспорте —
  по-английски); кернинг во вшитом подмножестве шрифта (нужны GSUB/GPOS с перенумерацией);
  предел памяти растра, если 16 000 px по стороне окажется дорог для контейнера.
- **2026-10-13. Дефект: переименование в дереве прокручивало список.** Двойной клик
  по имени строки у нижнего края открывал поле с `autoFocus`, и браузер прокручивал его
  «в зону видимости» — дерево дёргалось на 9 px под курсором, ровно то, от чего защищает
  регрессия §6.9. Сценарий `tree-editing.spec.ts` этого не видел: под курсором оказывалась
  opaque-`Capability`, у которой двойной клик ничего не открывает. Этап 6 сделал её
  поддержанной, и сценарий упал. Лечится фокусом с `preventScroll`, а не правкой сценария
- **2026-10-13. Живая проверка этапа 6.** Jar с `main` (до этапа) на свежем `postgres:16`,
  профиль `dev`: импорт эталона — схема `V4`, 69 Capability с `supported = false`. Тот же
  том, jar этапа 6: Flyway применил `V5`, Capability поддержаны, `Location` — нет; выгрузка
  версии 1 побайтово та же, что до миграции; Capability переименована через API, версия
  сохранена, имя в файле. Снимок `capture.mjs` с этого сервера совпал с правленой
  скриптом фикстурой E2E по флагам элементов и связей, каталогу типов и находкам.
- **2026-10-13. Этап 6 закрыт.** Критерий §12 — Capability редактируется, а не только
  сохраняется — выполнен: `PhaseTwoIT` (правка, создание, связь по матрице, выгрузка без
  потерь, миграция) и `e2e/phase-two.spec.ts` (правка в «Свойствах», создание с палитры).
  Полный `./mvnw verify` зелёный: 356 юнит-тестов сервера, 72 интеграционных, 67 юнит-тестов
  фронтенда; Playwright — 83 сценария. Граф зависимостей не изменился. **Что этап оставляет
  следующим:** `Location` и `Grouping` вне фаз — их поддержка требует отдельного решения
  (FR-08 их не называет); альтернативные фигуры Archi (облако `Meaning`, эллипс `Value`,
  параллелограмм `Requirement`) не рисуются — канва держится прямоугольной нотации
  с иконкой, как и для фазы 1; фаза 3.
- **2026-10-13. Решение: OEF переносит типы, неподдержанные в редакторе.** Таблица §3.5
  говорила «`raw_xml` неподдержанных сущностей не переносится», и буквальное чтение
  выбросило бы `Location` (в эталоне их два) — хотя у OEF есть для него тип. Opaque — свойство
  редактора, а не формата; не переносится только то, что OEF выразить не может. Спека
  уточнена (`UC-IXC-005`, §3.5, `ADR-0008`). **Отменено:** выгружать только поддержанные типы.
- **2026-10-13. Решение: ответ OEF — zip, а не голый `.xml`.** FR-44 требует, чтобы отчёт
  сопровождал каждую выгрузку; заголовок может нести число, но не содержание. **Отменено:**
  `.xml` в теле и отчёт отдельным запросом — отчёт потерялся бы у первого же скрипта.
- **2026-10-13. Наблюдение: Archi при импорте OEF дорисовывает линии вложенности.** На эталоне
  Archi добавил 61 линию — все между вложенными объектами — и не сохранил идентификаторы
  папок. Это поведение импортёра Archi, а не потеря нашего файла: в модели этих линий нет.
- **2026-10-13. Этап 6a закрыт.** Критерий §12 выполнен: эталон и все фикстуры проходят
  официальную XSD (`OefWriterTest`, `OefExportFixturesTest`), выгрузку из живого API Archi 5.9
  проверил по XSD и импортировал с совпадением типов, свойств, документации и геометрии;
  отчёт полон по построению и держится эталонами `oef_export_*.losses.txt`. Полный
  `./mvnw verify` зелёный: 372 юнит-теста сервера, 76 интеграционных, 67 юнит-тестов
  фронтенда; Playwright — 84 сценария. Граф зависимостей не изменился. **Что этап оставляет
  следующим:** имя и документация пишутся одним языком (`xml:lang="ru"`), хотя модель
  бывает и на другом; `metadata` (Dublin Core) не заполняется; точки зрения Archi без
  аналога в перечне OEF пишутся строкой как есть (схема это допускает).
- **2026-10-13. Решение: Git-выгрузка — обратимое кодирование снимка версии.** Публикуется
  `.archimate` зафиксированной версии (`INV-IXC-008`), а YAML хранит всё, что нужно для тех же
  байт: порядок атрибутов и содержимого. Обычный порядок Archi восстанавливается без подсказок,
  иной — служебными `attribute_order`/`content_order` (на эталоне их нет ни в одном из 784
  файлов). У узлов представления `name` лежит в `attributes`: Archi пишет его там где придётся,
  и первая версия формата давала `attribute_order` у каждой группы. **Отменено:** публиковать
  из таблиц — это текущее состояние, а не версия, и из него нельзя восстановить снимок.
- **2026-10-13. Решение: «сортировка свойств» (§11.2) — порядок документа.** Алфавит ломал бы
  round-trip: порядок `property` в Archi значим. **Отменено:** сортировать свойства по ключу.
- **2026-10-13. Решение: `position` наследуется из HEAD.** Разреженного `sort_order` в снимке
  нет; позиция объекта берётся из его файла в последнем коммите (наибольшая возрастающая
  подпоследовательность сохраняется, новые — в промежутки). Вставка в начало папки даёт
  `position: 500` и один новый файл (`GitYamlWriterTest#insertionKeepsNeighbourPositions`).
  **Отменено:** позиция = индекс × 1000 — вставка перенумеровывала бы всю папку.
- **2026-10-13. Решение: расхождение — по каталогу модели.** Чужой коммит вне
  `models/<каталог>/` (README, другая модель) не мешает: наш ложится сверху fast-forward'ом.
  Расхождение — чужой последний коммит каталога или переписанная история. **Отменено:**
  любой сдвиг HEAD — одна правка README остановила бы выгрузку всех моделей до 7c.
- **2026-10-13. Решение: ADR-0003 принят — outbox для `ModelVersionCommitted`.** Звено цепочки
  не `GitSnapshotCommitted`, как писал ADR, а переход «версия → коммит»; `git_sha` пишется одной
  транзакцией с закрытием записи outbox, повтор находит коммит по трейлерам. **Отменено:**
  очередь как «версии без `git_sha`» — при подключении привязки она выгрузила бы всю историю
  разом, а версии вне Git (модель без привязки) висели бы в ней вечно.
- **2026-10-13. Решение: `GitBinding` — своя таблица (V6), `workspace.git_*` удалены.** Столбцы
  из V1 были пусты; у привязки своё состояние и свой хозяин — interchange. Для «репозиторий
  недоступен» заведён класс отказа `Failure.UNAVAILABLE` (`503`) в обоих обработчиках.
- **2026-10-13. Решение: JGit — в `archi-bootstrap`**, как Batik. **Отменено:** модуль
  `archi-interchange-adapter-git` — восемнадцатый проект, правка `ADR-0001` ради одного класса.
- **2026-10-13. Ошибка в тесте: «переписанная история» её не переписывала.** JGit отказывается
  удалять текущую ветку bare-репозитория (`REJECTED_CURRENT_BRANCH`), и `delete()` молча ничего
  не делал: тест публиковал поверх обычного чужого коммита и падал на ожидании `DIVERGED`.
  Лечится честным переписыванием — force push сироты из отдельного репозитория
  (`GitPublisherTest#rewrittenHistoryIsDivergence`). Регрессия вероятна в любом тесте, который
  «удаляет ветку» через `RefUpdate.delete()` без проверки результата.
- **2026-10-13. Этап 7a закрыт.** Критерий §12 выполнен: повторная запись без правок даёт те же
  байты (`GitYamlWriterTest#writingTwiceProducesIdenticalBytes`, эталоны `git_roundtrip`),
  возврат модели к прежнему содержимому даёт то же дерево коммита
  (`GitExportIT#repeatedExportProducesNoDiff`), цикл БД → Git → БД на эталоне без потерь
  (`GitRoundTripIT`). Ретеншен включён по умолчанию и чистит только при доступном репозитории;
  очищенный снимок выгружается из коммита побайтово (`GitRetentionIT`). Полный `./mvnw verify`
  зелёный: 401 юнит-тест сервера, 87 интеграционных; фронтенд не менялся (67 юнит-тестов,
  84 сценария Playwright). Граф зависимостей: JGit 7.8.0 в bootstrap, SnakeYAML в
  interchange-application. **Что этап оставляет следующим:** привязка и состояние выгрузки —
  только API, экрана нет; расхождение разбирается руками (после правки — `POST …/git/push`)
  до 7b/7c; обработчик outbox один на процесс (несколько экземпляров корректны, но дублируют
  работу); версии, сохранённые до настройки привязки, в Git не попадают (`SKIPPED`);
  восстановление снимка берёт коммит из ветки — после переписанной истории старый коммит
  достаётся только из кэша сервера.
- **2026-10-14. Ошибка в тесте: клон пустого репозитория коммитит в `master`.** У пустого
  bare-репозитория нет `HEAD`, который клон мог бы унаследовать, и JGit берёт свою ветку
  по умолчанию — `master`, а не `main` из `setInitialBranch` удалённого. Тест разрешения
  ссылок искал `main` и не находил. Лечится ссылкой `HEAD` на `refs/heads/main` в клоне
  до первого коммита (`GitRefResolutionTest`); `GitPublisherTest` не задет — он клонирует
  непустой репозиторий.
- **2026-10-14. Полный идентификатор коммита разрешается и без объекта.**
  `ObjectReader.resolve` для 40 знаков отдаёт сам идентификатор, не проверяя, что объект
  есть; `parseCommit` потом бросает `MissingObjectException`, а общий обработчик превращал
  её в «репозиторий недоступен». Отсутствующий объект — «ссылка не найдена».
- **2026-10-14. Решение: замена без изменений базу не трогает.** `VersionService.rollback`
  раскладывает снимок заново, и все строки получают новые ключи — даже когда содержимое то же.
  Для импорта по ссылке это лишнее: `ModelImportService.replace` сначала сравнивает снимок
  с текущим содержимым и при совпадении возвращает последнюю версию без записи. Откат
  к версии ведёт себя по-прежнему — его общая часть с импортом вынесена в `VersionService.replace`.
- **2026-10-14. Решение: находки в подробностях отказа переводятся адаптером.** Отказ
  применения `422` несёт находки сессии в `details`; без перевода Jackson отдал бы их записью
  `Message` (ключ и аргументы). `InterchangeProblemHandler` пропускает подробности через
  `FindingMapper` — тот же, что формирует находки ответа импорта файла.
- **2026-10-14. Этап 7b закрыт.** Критерий §12 выполнен: отчёт строится до применения и базу
  не трогает, применение — одна транзакция, создающая новую версию (`GitImportIT#reportPrecedesApply`,
  `GitImportTest#applyIsAtomic`, `#appliedStateBecomesNewVersion`); отказ при записи оставляет
  только сессию `REJECTED` (`GitImportIT#failedApplyLeavesOnlyRejectedSession`). Расхождение
  снимается импортом головы ветки (`GitImportIT#importOfHeadResolvesDivergence`). Полный
  `./mvnw verify` зелёный: 427 юнит-тестов сервера, 91 интеграционный; фронтенд не менялся
  (67 юнит-тестов vitest). Граф зависимостей перерисован — не изменился байт в байт: новых
  библиотек этап не принёс. **Что этап оставляет следующим:** экрана нет — отчёт и применение
  только через API; импортируется состояние лишь каталога этой модели; применение заменяет
  содержимое целиком, и внутренние ключи строк меняются, как при откате; разошедшиеся правки
  с обеих сторон не сливаются — для этого этап 7c.
