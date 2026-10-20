# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Язык

Репозиторий русскоязычный: спецификация, планы, README, интерфейс продукта и коммиты —
на русском. Пиши документацию и общайся на русском. Английский остаётся в коде:
идентификаторы, `archi_type`, коды ошибок (`RELATION_NOT_PERMITTED`).

## Состояние репозитория

**Этапы 0–6, 6a и 7a–7c закрыты (этапы 4, 5 и 5a — 2026-10-12, этапы 6, 6a и 7a — 2026-10-13,
этапы 7b и 7c — 2026-10-14). Все этапы §12 пройдены; дальше — пробелы из «Ещё не заведено».**
Собирается multi-module Maven по
[`ADR-0001`](spec/adr/0001-maven-multi-module.md): 17 проектов в реакторе — родитель,
три агрегатора контекстов, двенадцать модулей-слоёв, `archi-bootstrap` с классом запуска
и fat jar. Код есть во всех трёх контекстах во всех четырёх слоях.

```
archi-<bc>/archi-<bc>-domain              только java-uuid-generator (+ junit в test)
archi-<bc>/archi-<bc>-application         -> domain (interchange — ещё и modeling-application)
archi-<bc>/archi-<bc>-adapter-rest        -> application + Spring Web
archi-<bc>/archi-<bc>-adapter-persistence -> application + Spring Data JPA
archi-bootstrap                           все 12 + Spring Boot, здесь jar
```

Пакеты — `uz.salvadore.hamkorbank.archi.<bc>.<layer>`, класс запуска —
`uz.salvadore.hamkorbank.archi.ArchiCreatorApplication`.

**Границу домена держит сборка, а не договорённость.** В шести модулях (`domain`
и `application` трёх контекстов) правило `maven-enforcer-plugin` запрещает
`org.springframework*`, `jakarta.persistence`, `jakarta.transaction`, `org.hibernate*`
в любом scope, включая транзитивные. Нужен Spring — значит, код не в том модуле.
Конфигурация правила одна, в `pluginManagement` родителя.

**Приложение запускается.** `docker compose up -d` поднимает `app`, `postgres:16`
и `keycloak:26` ([§10.1](docs/archi-creator.md#101-состав)), `/actuator/health` = UP,
вход через Keycloak отдаёт JWT с ролью `VIEWER`/`ARCHITECT`/`ADMIN` (FR-28).
Секреты — из `.env` (образец `.env.example`), в репозиторий не попадают (NFR-06).

Что появилось на этапе 0 и где лежит:

| Что | Где |
|---|---|
| Конфигурация и профили `dev`/`prod` | `archi-bootstrap/src/main/resources/application*.yaml` |
| Схема БД | `archi-bootstrap/src/main/resources/db/migration/V1__baseline.sql` — одна таблица `workspace` |
| Безопасность: ресурс-сервер, роли из `realm_access`, заглушка `dev` | `archi-bootstrap/.../bootstrap/security/` |
| Realm Keycloak с тремя ролями и клиентом SPA | `deploy/keycloak/archi-realm.json` |
| Развёртывание | `docker-compose.yml`, `Dockerfile` (упаковывает готовый jar, сам не собирает) |
| Каркас фронтенда | `archi-bootstrap/src/main/frontend/` — React 19 + TS + Vite ([`ADR-0016`](spec/adr/0016-frontend-location.md)) |
| Конвейер и гейты | `.github/workflows/ci.yml` |

Что появилось на этапе 1 и где лежит:

| Что | Где |
|---|---|
| Каталог типов ArchiMate 3.2 со слоями и фазами, `ArchiType` | `archi-modeling-domain/.../modeling/domain/metamodel/` |
| Матрица допустимых связей — таблица Archi без правок | `archi-modeling-domain/src/main/resources/.../metamodel/archimate-3.2-relationships.xml` |
| Документ `.archimate` как значение (`ModelDocument`, `DocumentNode`, `RawXmlFragment`) | `archi-interchange-domain/.../interchange/domain/document/` |
| Читатель на StAX и детерминированный писатель | `archi-interchange-domain/.../interchange/domain/codec/` |
| Сессия импорта, идемпотентность, задание выгрузки | `.../interchange/domain/importing/`, `.../exporting/` |
| Golden-file round-trip, `assertXmlEquivalent`, фикстуры §9.1 | `archi-bootstrap/src/test/.../bootstrap/roundtrip/`, `archi-bootstrap/src/test/resources/fixtures/` |

Эталон round-trip читается прямо из `docs/Hamkorbank_AS_IS_strict.archimate`, копии
в фикстурах нет. Писатель пишет в раскладке Archi и совпадает с эталоном побайтово
везде, кроме двух пустых папок и перевода строки в конце — эталон собран скриптом.

Что появилось на этапе 2 и где лежит:

| Что | Где |
|---|---|
| Схема модели, версий, блокировок, ACL, сессий импорта | `archi-bootstrap/.../db/migration/V2__model.sql` |
| Агрегаты `ArchitectureModel`, `View`, `ModelLock`, `ModelVersion`, ACL | `archi-modeling-domain/.../modeling/domain/` |
| Сценарии UC-MDL-001…007, порты, таблица ролей `Operation` | `archi-modeling-application/.../application/` |
| Раскладка документа по строкам и сборка (ADR-0017), импорт и экспорт, CSV | `archi-interchange-application/.../application/` |
| JPA-адаптеры | `archi-*-adapter-persistence` |
| REST и `problem+json` | `archi-*-adapter-rest`, контракты — `spec/contracts/*/rest-api.openapi.yaml` |
| Проводка, метрики, корреляция логов | `archi-bootstrap/.../bootstrap/wiring/`, `.../bootstrap/web/` |
| Алерты Prometheus | `deploy/prometheus/alerts.yaml` |
| Интеграционные тесты (`*IT`, Testcontainers) | `archi-bootstrap/src/test/.../bootstrap/{api,interchange,persistence,security,version}/` |

**Хранение — типизированные столбцы плюс остаток XML** (`raw_xml`,
[`ADR-0017`](spec/adr/0017-xml-residue.md)): всё, что не легло в поля агрегатов, лежит
в остатке с позициями, формат остатка — interchange, modeling его не разбирает. При
выгрузке столбец сильнее остатка. Править `raw_xml` в обход interchange нельзя.
Выгрузка отдаёт **снимок зафиксированной версии** (INV-IXC-008), собранный из базы при
сохранении; правки без сохранения в файл не попадают.

**DTO — в пакете `dto`, не внутри контроллера.** Формы запросов и ответов лежат
в `…adapter.rest.dto` (в `archi-bootstrap` — `…bootstrap.<часть>.dto`); вложенный тип
в `@RestController` или `@RestControllerAdvice` роняет сборку
(`RestLayerConventionsTest`, ArchUnit).

Application-слои без Spring: транзакция — порт `UnitOfWork`
([`ADR-0024`](spec/adr/0024-unit-of-work.md), `@Transactional` роняет `TransactionBoundaryTest`),
события — после коммита, роль проверяется на границе use case'а по таблице
`spec/nfr/<bc>.yaml`, а не URL.

**Application-слой modeling — входные порты, handler'ы и DTO.** Адаптеры, мосты `bootstrap`
и другие контексты зовут сценарии только через интерфейсы `application/port/in/`
(`…ApplicationService`, реализация `…Impl` в `service` лишь делегирует). Сценарий — handler
`application/handler/{Entity}{Action}CommandHandler`/`…QueryHandler`: роль, метрика, транзакция
через `ModelingKernel` (он и `RequestedIds` видны только пакету `handler`). Команды, запросы
и результаты — record'ы `application/dto/command|query|result` из простых значений; агрегат
наружу не выходит, перевод — `application/mapper/`. Вход других контекстов (выгрузка, Git,
импорт) — `ModelExchangeApplicationService`: роль у этих операций проверяет вызывающий.
Держит `ApplicationLayerTest` (ArchUnit), исключений нет. Содержимое модели между modeling и раскладкой
документа ходит строками из простых значений (`dto/ModelRows`): агрегаты из строк и обратно собирает
`mapper/ModelRowsMapper`, а раскладка спрашивает правила формата у `mapper/ModelRowRules` — так остаток
XML тот же, а правило одно. Advisor переводится по этому образцу на своём этапе ревью.

**Application-слой interchange — по тому же образцу.** Входные порты `port/in/` — импорт файла,
настройка импорта, выгрузка модели, картинки представлений; handler'ы — в `handler`, общее у них —
`InterchangeKernel` (роль до любой работы, метрика, транзакция, часы, ключи; виден только пакету).
Исход импорта (`APPLIED`, `REPLAYED`, `REJECTED_CORRUPT`, `REJECTED_STRICT`) с кодом и текстом отказа
решает handler, контроллер лишь переводит его в статус. Сборочные части выгрузки — писатели CSV, OEF,
SVG, детерминированный zip, отчёт о потерях — в `exporting`, `oef`, `rendering`. Адаптеру из application
interchange доступны только `port.in`, `dto` и выходные порты (`ApplicationLayerTest`); сервисы Git ещё
не переведены и перечислены там поимённо — их очередь на этапе ревью Git.

Модули доменов друг от друга **не зависят**: Maven не допускает цикла, а partnership
в карте контекстов двусторонний. Поэтому у interchange свой `ArchiId`, а проверка
матрицы при импорте (`RelationMatrix.check`) соединяется с сессией в application-слое
на этапе 2.

Что появилось на этапе 3 и где лежит:

| Что | Где |
|---|---|
| Редактор: дерево, канва, палитра, свойства и описание, история | `archi-bootstrap/src/main/frontend/src/` (`tree/`, `canvas/`, `panels/`, `history/`) |
| Документ модели в браузере, обратимые изменения, синхронизация при сохранении ([`ADR-0018`](spec/adr/0018-local-edit-session.md)) | `frontend/src/model/` |
| Геометрия фигур и спрайт иконок — общие с будущим серверным SVG | `archi-bootstrap/src/main/resources/ui/shapes.json`, `icons.svg` |
| Токены в CSS из спеки на сборке | `frontend/build/design-tokens.ts`; в classpath — `ui/design-tokens.yaml` |
| Строки интерфейса и русские названия типов | `frontend/src/i18n/` |
| Вход: OIDC с PKCE, `/me`, `/ui-config` | `frontend/src/auth/`, `archi-bootstrap/.../bootstrap/security/SessionController.java` |
| Идентификаторы от клиента, ребро связи, `label`/`content` узла, `placements` | modeling и interchange, миграция `V3__view_node_text.sql` |
| E2E, визуальные проверки, замеры | `frontend/e2e/`, `frontend/visual/`, `frontend/perf/` |

Что появилось на этапе 4 и где лежит:

| Что | Где |
|---|---|
| Правка представления: группа и заметка, подпись и текст, стиль узла и ребра, удаление ребра, вложение узла ([`UC-MDL-008`](spec/application/modeling/usecases/edit-view.md)) | `View`, `ViewApplicationService`, `ViewController`; `ViewEditingIT` |
| Авторазметка — предложение геометрии, база не меняется ([`ADR-0019`](spec/adr/0019-auto-layout-proposal.md)) | `archi-modeling-domain/.../modeling/domain/view/layout/` |
| Флаг связи вложения в `/metamodel/relations` | `RelationTypeSuggestQueryHandler` по `NestingRules` |
| Умные связи, вложенность, авторазметка, паритет с Archi на канве | `frontend/src/canvas/` (`CanvasView`, `CanvasTools`, `RelationPicker`, `guides.ts`), `frontend/src/model/ops.ts` |
| Собственный стиль объекта в «Свойствах» | `frontend/src/panels/StyleEditor.tsx` |
| E2E этапа и приёмка | `frontend/e2e/{smart-relation,nesting,auto-layout,editing-parity,acceptance}.spec.ts`; работа `e2e` в CI |

Подменный сервер E2E (`e2e/support/mockApi.ts`) читает ту же матрицу связей, что сервер
(`e2e/support/relationMatrix.ts`), а авторазметку отвечает упрощённо — алгоритм сервера
проверяют его юнит-тесты. Визуальный эталон — в двух вариантах, `-darwin` и `-linux`
(снят в `mcr.microsoft.com/playwright` той же версии, что `@playwright/test`).

**Правки живут в браузере до «Сохранить»** ([`ADR-0018`](spec/adr/0018-local-edit-session.md)):
сохранение переводит сервер от точки сохранения к текущей позиции истории командами
API и фиксирует версию. Идентификаторы новых объектов (`id`, `archiId`) задаёт клиент.
Команды API по-прежнему меняют базу сразу — это свойство сервера, а не интерфейса.

Что появилось на этапе 5 и где лежит:

| Что | Где |
|---|---|
| Домен помощника: сессия, бюджет месяца, контекст модели с усечением | `archi-advisor-domain/.../advisor/domain/` (`session/`, `budget/`, `context/`) |
| Сценарии: обращение к четырём функциям, управление и расход ([`UC-ADV-001`…`003`](spec/application/advisor/usecases/)) | `archi-advisor-application/.../application/service/` |
| Хранение: сессии, бюджеты, исход в аудите | `archi-advisor-adapter-persistence`, миграция `V4__advisor.sql` |
| REST `/ai/**`: поток SSE на `SseEmitter`, отказы до потока | `archi-advisor-adapter-rest` |
| Клиент Anthropic (Java SDK), мост к modeling на чтение, промпты, метрики | `archi-bootstrap/.../bootstrap/advisor/`, `src/main/resources/advisor/prompts/` |
| Панель помощника, фильтр палитры по плану, настройки для `ADMIN` | `frontend/src/advisor/` |
| Тесты: подменный Anthropic на уровне HTTP, поток на настоящем Tomcat | `archi-bootstrap/src/test/.../bootstrap/advisor/` (`FakeAnthropic`, `AdvisorStreamingIT`); `frontend/e2e/advisor.spec.ts` |

**Помощник не изменяет модель — и держит это граница модулей.** Модули advisor
не зависят от modeling вовсе; порт чтения `ModelContextReader` и каталог метамодели
реализует мост в `bootstrap.advisor` поверх `ModelQueryApplicationService` и `RelationMatrix`.
`AdvisorArchitectureTest` (ArchUnit) роняет сборку, если мост тронет сервис записи.
Отказы (роль, выключатель, бюджет, частота, размер контекста) принимаются до вызова
API и до открытия потока; расход списывается в транзакции сессии даже при обрыве.
Выключатель — `workspace.ai_enabled`, по умолчанию выключен; без ключа
`ANTHROPIC_API_KEY` помощник отвечает `503`, остальное приложение работает.
Поток отдаёт `SseEmitter`: `StreamingResponseBody` в Spring 7 не сбрасывает кадры.

Что появилось на этапе 5a и где лежит:

| Что | Где |
|---|---|
| Писатель SVG по визуальному языку канвы: силуэты, токены, иконки, маршрут связей, перенос подписи, отчёт о потерях | `archi-interchange-application/.../application/rendering/` (`SvgWriter`, `LabelLayout`, `Routing`) |
| Сценарии `ExportViewImage`, `ExportAllViewImages` ([`UC-IXC-004`](spec/application/interchange/usecases/export-view-image.md)), порты `Typesetter`, `Rasterizer` | `ViewImageExportQueryHandler`, `AllViewImagesExportQueryHandler`; `.../application/port/` |
| REST: `GET /views/{id}/export`, `GET /models/{id}/views/export` | `InterchangeController` |
| Нотация из classpath, шрифт Inter на AWT, подмножество глифов (fontbox), растр (Batik) | `archi-bootstrap/.../bootstrap/export/` |
| Шрифт Inter 4.1 (TTF — сервер, WOFF2 — канва, OFL) | `archi-bootstrap/src/main/resources/ui/fonts/` |
| Имена файлов пакета `<слаг>.<8 hex>` (их же возьмёт Git) | `archi-interchange-domain/.../exporting/FileSlug` |
| Меню «Экспорт» в верхней панели | `frontend/src/app/TopBar.tsx`; `e2e/view-export.spec.ts` |
| Сверка канвы и экспорта | `frontend/visual/export-parity.spec.ts`, эталоны сервера — `frontend/visual/export-parity/`, их актуальность — `ExportParityFixturesTest` |

**Картинка строится из снимка версии, а не из таблиц** — как `.archimate` и CSV: правка
после сохранения в неё не попадает, пакет не смешивает версии. **PNG растеризуется из
SVG с подписями в кривых**: Batik не зависит от шрифтов образа. Писатель печатает SVG 1.1
без CSS (наконечники, ореол подписи, иконки — явной геометрией), чтобы файл одинаково
читали браузер, Batik, Inkscape и Word. **Раскладка подписи повторяет браузер** до
мелочей — половина межстрочного зазора вниз до пикселя, высота строки до 1/64 px,
многоточие после выровненной строки; правка в `LabelLayout` или `SvgWriter` без
прогона сверки почти наверняка её сломает. Изменил писатель — пересними эталоны:
`./mvnw -P '!frontend' -pl archi-bootstrap -am test -Dtest=ExportParityFixturesTest
-Dexport.parity.update=true -Dsurefire.failIfNoSpecifiedTests=false`, затем
`npx playwright test visual/export-parity.spec.ts`. Сверка запускает Chromium
с `--font-render-hinting=none`: в Linux без него ширины глифов целые, и переносы
расходятся и с macOS/Windows, и с сервером.

Что появилось на этапе 6 и где лежит:

| Что | Где |
|---|---|
| Фаза 2 поддержана: Motivation и Strategy создаются и правятся | `MetamodelPhase`, `ConceptDefinition.supported()`; `PhaseTwoIT` |
| Флаг `supported` у моделей, загруженных раньше | миграция `V5__phase_two_supported.sql` |
| Силуэты и иконки 14 типов, примитив `bevel` (срезанные углы мотивации) | `ui/shapes.json`, `ui/icons.svg`; `canvas/shapes.ts` и `rendering/Outlines.java` |
| Слои «Стратегия» и «Мотивация» в палитре | `frontend/src/palette/Palette.tsx`; `e2e/phase-two.spec.ts` |

**Поддержан ли тип, решает каталог, а хранит строка.** Флаг `supported` пишется при
импорте и в базе не пересчитывается: перевод типов в поддержанные — это правка
`MetamodelPhase` **и** миграция данных, иначе загруженные раньше объекты останутся
opaque. Остаток XML у поддержанного и opaque-элемента одинаков, поэтому миграции
достаточно перевернуть флаг. Фаза 3, `Location` и `Grouping` по-прежнему opaque;
проверка UI-020 идёт на `Location`.

Что появилось на этапе 6a и где лежит:

| Что | Где |
|---|---|
| Официальная XSD OEF 3.1 и проверка по ней без сети | `archi-interchange-application/src/main/resources/.../application/oef/*.xsd`, `OefSchema` |
| Писатель OEF с отчётом о потерях | `archi-interchange-application/.../application/oef/OefWriter` |
| Сценарий `ExportOpenExchange` ([`UC-IXC-005`](spec/application/interchange/usecases/export-open-exchange.md)), `GET /models/{id}/export?fmt=oef` | `ModelExportQueryHandler`; `500 IXC_OEF_SCHEMA_VIOLATION` — `ExportDefectException` |
| Эталоны выгрузки и их отчёты | `archi-bootstrap/src/test/resources/fixtures/oef_export_*.{xml,losses.txt}`, `OefExportFixturesTest` |
| Пункт меню «Экспорт» | `frontend/src/app/TopBar.tsx`; `e2e/view-export.spec.ts` |

**OEF переносит всё, что умеет выразить, — независимо от фазы.** `Location` и `Grouping`
в редакторе opaque, но в OEF у них свои типы, и они выгружаются. Не переносится то,
чего в формате нет: остаток XML, представления не-ArchiMate, оформление без аналога.
**Отчёт полон по построению:** у каждого вида объекта в `OefWriter` закрытый список
переносимых атрибутов, всё прочее — запись отчёта; новый атрибут Archi попадёт в отчёт
сам, а не пропадёт молча. Геометрия в OEF абсолютная (у нас — относительно родителя),
точки перегиба — формулой канвы (`Routing.absoluteBendpoints`). Ответ — zip из файла
и `loss-report.json`; имя файла в архиве — слаг в латинице, иначе системный `unzip`
macOS и старый Проводник его не создают. Изменил писатель — пересними эталоны:
`./mvnw -P '!frontend' -pl archi-bootstrap -am test -Dtest=OefExportFixturesTest
-Doef.fixtures.update=true -Dsurefire.failIfNoSpecifiedTests=false` и проверь дифф.
Сторонний инструмент под рукой — Archi: `Archi -application com.archimatetool.commandline.app
-consoleLog -nosplash --xmlexchange.import <абсолютный путь>.xml --saveModel <out>.archimate`.

Что появилось на этапе 7a и где лежит:

| Что | Где |
|---|---|
| Разложенный YAML: писатель, обратная сборка, наследуемые позиции, эталоны | `archi-interchange-application/.../application/git/` (`GitYamlWriter`, `GitYamlReader`, `PositionPlan`); `fixtures/git_roundtrip/` |
| Привязка пространства к репозиторию: агрегат, `SecretRef`, авторы | `archi-interchange-domain/.../domain/git/`, миграция `V6__git_binding.sql`, `GitBindingService` |
| Публикация версии коммитом ([`UC-IXC-003`](spec/application/interchange/usecases/commit-version-to-git.md)), расхождение, повтор по трейлерам | `GitPublishingService`; порты `GitRepositoryPort`, `GitOutbox`, `PublishedVersions`, `SecretResolver` |
| Outbox зафиксированных версий ([`ADR-0003`](spec/adr/0003-event-publishing.md)) | `outbox_event` (`V7__outbox.sql`), `JpaGitOutbox`, `bootstrap/wiring/OutboxEventPublisher` |
| JGit, секреты из окружения, мост к версиям, проход очереди | `archi-bootstrap/.../bootstrap/git/`, `wiring/GitConfiguration`; `archi.git.*` |
| REST: `PUT/GET/DELETE /workspaces/{id}/git-binding`, `POST /models/{id}/git/push` | `GitController` |
| Ретеншен с Git, восстановление очищенного снимка из коммита | `SnapshotPurgeCommandHandler`, `VersionContents` + порт `SnapshotArchive`; `GitSnapshotArchive` |
| CI-задача `.archimate` на теге в репозитории выгрузки | `deploy/git/gitlab-ci.yml.example` |
| Тесты | `GitYamlWriterTest`, `GitPublisherTest` (JGit без Docker); `GitExportIT`, `GitRetentionIT`, `GitRoundTripIT`, `GitBindingIT` |

**YAML — обратимое кодирование снимка версии, а не таблиц.** Из дерева коммита
собирается тот же документ и те же байты `.archimate`; на этом стоит восстановление
очищенного снимка, и оно сверяется с `content_hash` версии. Порядок атрибутов
и содержимого — часть формата (`attribute_order`, `content_order`, если не как в Archi);
«сортировка свойств» — порядок документа, не алфавит. **Позиции наследуются из HEAD**:
снимок не несёт разреженного `sort_order`, поэтому вставка встаёт в промежуток,
а соседи сохраняют числа. Изменил писатель — пересними эталоны:
`./mvnw -P '!frontend' -pl archi-interchange/archi-interchange-application -am test
-Dtest=GitYamlWriterTest -Dgit.fixtures.update=true -Dsurefire.failIfNoSpecifiedTests=false`.

**Версия попадает в Git через outbox, а не событием в памяти.** `ModelVersionCommitted`
пишется в `outbox_event` той же транзакцией, что версия; `git_sha` — вместе с закрытием
записи. Ветка двигается только вперёд (push с ожидаемой головой, без принуждения).
Расхождение — чужой коммит в каталоге модели или переписанная история; чужое вне
каталога ложится под наш коммит. Токена нет ни в базе, ни в домене — `env:ИМЯ`.
Репозиторий по `file://` привязывается только при `archi.git.allow-local-repositories=true`
(включено в интеграционных тестах), иначе администратор привязал бы любой каталог сервера.
Каталог модели — `models/<слаг>.<8 hex от ModelId>/`, при переименовании не двигается.

Что появилось на этапе 7b и где лежит:

| Что | Где |
|---|---|
| Отчёт об изменениях по объектам и полям | `archi-interchange-domain/.../domain/git/ModelChanges`; `ModelChangesTest` |
| Ссылка: ветка, тег, коммит (`GitRef`, `ResolvedRef`), разрешение на JGit | `GitRepositoryPort.resolve`, `JGitRepository`; `GitRefResolutionTest` |
| Сценарий импорта по ссылке ([`UC-IXC-006`](spec/application/interchange/usecases/pull-model-from-git.md)): отчёт и применение | `GitImportService`; порт `ModelStates`, мост `bootstrap/git/ModelingModelStates` |
| Замена содержимого модели новой версией (общая часть с откатом) | `ModelContentGetQueryHandler`/`ModelContentReplaceCommandHandler`, `VersionContents.replace` |
| REST: `POST /models/{id}/git/pull/preview`, `POST /models/{id}/git/pull` | `GitController`, `GitDtoMapper`, `FindingMapper` |
| Расхождение снимается импортом головы ветки | `GitImportService.adopt`, `GitOutbox.supersede`; правила `accepted`/`carries` в `GitPublishingService` |
| Тесты | `GitImportTest` (фейки с откатом), `GitImportIT`, новые случаи `GitPublisherTest` |

**Импорт по ссылке — два вызова, и второй принимает только то, что показал первый.**
Отчёт ничего не пишет; применение несёт `commitSha` и `baseHash` из отчёта: читается ровно
этот коммит (ветка могла уехать), и только если текущее содержимое модели то же —
иначе `409 INV-IXC-012`. Попытка — сессия импорта с `targetModelId`: `APPLIED` или
`REJECTED` с находкой; отказ при записи откатывает транзакцию, а сессия `REJECTED`
пишется после отката отдельной. Нужна блокировка модели, роль — `PullModelFromGit`.
**Импорт головы ветки привязки снимает расхождение**: открытые записи outbox модели
со старшими номерами закрываются `SKIPPED`, голова становится `last_commit_sha`, а
публикация признаёт версию по содержимому каталога и дальше не считает расхождением
чужую правку, уже вошедшую в последний опубликованный коммит модели.

Что появилось на этапе 7c и где лежит:

| Что | Где |
|---|---|
| Слияние трёх состояний по объектам и полям: конфликты, порядок, каскад | `archi-interchange-domain/.../domain/git/EntityMerge`; `EntityMergeTest` |
| Сценарий слияния ([`UC-IXC-007`](spec/application/interchange/usecases/merge-model-branch.md)): отчёт с выборами и применение | `GitMergeService`; общее с импортом по ссылке — `GitAttempts` |
| Слитый коммит модели | порт `ModelMerges`, `JpaModelMerges`, миграция `V8__git_model_merge.sql` |
| Общий предок, слитый коммит вторым родителем | `GitRepositoryPort.mergeBase`, `CommitRequest.merged`; `GitMergeParentsTest` |
| Публикация признаёт слитый коммит | `GitPublishingService.accepted`; `GitPublisherTest#mergedForeignChangeIsNotDivergence` |
| REST: `POST /models/{id}/git/merge/preview`, `POST /models/{id}/git/merge` | `GitController`, `GitDtoMapper`; `MergeRequestIT` |
| Панель слияния, отметки и контуры на холсте ([`UI-022`](spec/ui/rules.md#ui-022--слияние-видно-на-холсте-до-применения)) | `frontend/src/merge/`, `ArchiNode`/`ArchiEdge` (`data-merge`); `e2e/merge.spec.ts` |
| Тесты | `GitMergeTest` (фейки — `GitFakes`, общие с `GitImportTest`), `MergeRequestIT` |

**Слияние — по объектам и полям, а не по строкам.** «Наше» — текущее содержимое модели
в базе, «их» — каталог модели в коммите ссылки, база — каталог в общем предке этого коммита
и коммита последней опубликованной версии (нет её — `409 IXC_GIT_NO_MERGE_BASE`). Поля —
каждый атрибут, `documentation`, `properties` списком, `raw_xml`, папка, у представления —
диаграмма целиком. Конфликт адресуется `<archiId>:<поле>` (`:*` — удаление против правки);
отчёт принимает выборы и пересчитывает итог, без выбора показывает «наше». Порядок детей
не конфликтует; связь без конца, узел без элемента, ребро без концов уходят каскадом —
отдельным списком отчёта. Применение — как в 7b: оба коммита и отпечаток из отчёта,
одна транзакция, конфликт без выбора — `422 IXC_MERGE_CONFLICTS_UNRESOLVED`.
**Слитый коммит записывается за моделью** (`git_model_merge`): его правки публикация
не считает расхождением, а если ветки привязки в нём нет — он второй родитель нашего
коммита, и merge request в GitLab закрывается сам. Слияние разошедшейся головы ветки
закрывает старые записи очереди, как импорт в 7b; без расхождения очередь не трогается.
Контуры добавляемых узлов — узлы React Flow вне документа: их изменения размеров
в состояние канвы не идут, иначе ResizeObserver зацикливается.

Тестов на сервере: 621 юнит-тест (`./mvnw test`) и 100 интеграционных `*IT` на
Testcontainers `postgres:16` и `keycloak:26` (failsafe, `./mvnw verify`; нужен Docker).
Во фронтенде: 67 юнит-тестов vitest (`npm test`, гоняется фазой `test` Maven)
и 87 сценариев Playwright (`npm run e2e`, API подменяется — сервер не нужен; в CI —
работа `e2e`, два потока).

Ещё не заведено: метамодель фазы 3, экран привязки к Git, состояния выгрузки и отчёта
импорта по ссылке в интерфейсе (есть только API; слияние в интерфейсе есть);
создание и удаление представлений из дерева (API нет); предохранитель (circuit breaker)
на Anthropic API.

Содержательная часть проекта — **спецификация**. Прежде чем писать код, читай её:
почти каждое решение уже принято и обосновано, и переизобретать его не нужно.

**Нормативный источник — [`spec/`](spec/README.md)** (разворот 2026-09-28). Код, тесты
и диаграммы производны от него: расхождение спеки и кода правится сначала в спеке.
`docs/` — обоснования, история решений и макеты, ненормативно: отвечает «почему»,
а не «что». При расхождении прав `spec/`.

## Команды

```bash
./mvnw clean package               # 17 модулей + фронтенд; jar в archi-bootstrap/target/
./mvnw clean package -P '!frontend' # то же без Node: правка не задевает интерфейс
./mvnw verify                      # плюс интеграционные *IT на Testcontainers (Docker)
./mvnw -P '!frontend' -pl archi-bootstrap -am verify -DskipUnitTests -Dit.test=RoundTripApiIT
                                   # один интеграционный; -DskipITs — без них
./mvnw test -Dtest=ClassName#method # один тест
./mvnw -P '!frontend' -pl archi-bootstrap -am test -Dtest=RoundTripGoldenFileTest \
       -Dsurefire.failIfNoSpecifiedTests=false   # round-trip, как шаг CI (NFR-05)
./mvnw -pl archi-modeling/archi-modeling-domain dependency:tree  # чистота модуля

cp .env.example .env               # заполнить пароли, один раз на клон
docker compose build && docker compose up -d   # app + postgres:16 + keycloak:26
docker compose logs -f app         # логи приложения (в prod они в JSON)
docker compose down                # остановить; -v чтобы снести и данные БД

cd archi-bootstrap/src/main/frontend && npm run dev   # Vite на :5173, прокси на :8080
npm test                           # (в frontend/) vitest: история, дерево, геометрия, статика
npm run e2e                        # (в frontend/) Playwright; один раз: npx playwright install chromium

open docs/mockups/index.html       # макеты интерфейса, статика без сборки
tools/check-links.py               # ссылки между файлами: существование файла и якоря
tools/check-traceability.py        # требования ↔ якоря спеки: покрытие и обрывы
tools/render-requirements-index.py # перегенерировать §2 из реестра (--check для CI)
tools/check-commit-message.py      # сообщение коммита по правилу «Коммиты» (см. ниже)
tools/render-depgraph.py           # граф зависимостей в PlantUML и PNG (см. ниже)
tools/check-comment-refs.py        # комментарии в коде без ссылок на спеку (см. ниже)
tools/check-string-literals.py     # строки кода: текст в i18n, без якорей (см. ниже)
tools/check-nfr-config.py          # числа application.yaml совпадают со spec/nfr
tools/check-coverage.py            # пороги покрытия по отчёту JaCoCo (после ./mvnw verify)
tools/check-qualified-names.py     # тип — простым именем через импорт, а не полным
git config core.hooksPath .githooks # включить хук commit-msg, один раз на клон
```

Десять скриптов на Python 3.9+ без зависимостей, каждый возвращает ненулевой код при
первом нарушении. Правишь спеку — прогони первые два; правишь требования — третий;
четвёртый вызывается хуком сам; пятый — по закрытии этапа; шестой — после правки
комментариев в коде; седьмой — после правки сообщений и строк в коде; восьмой —
после правки `spec/nfr/` или чисел в `application.yaml`; девятый — после полного
`./mvnw verify` (в CI — шаг после сборки); десятый — после правки кода Java.

### Граф зависимостей

**По закрытии каждого этапа перерисовывать граф и обновлять картинку в README.**

```bash
tools/render-depgraph.py           # оба графа: .puml и .png в docs/dependencies/
tools/render-depgraph.py --puml    # без рендера, если нет ни plantuml, ни docker
```

Рисует [depgraph-maven-plugin](https://github.com/ferstl/depgraph-maven-plugin)
(версия в `pom.xml` родителя, к жизненному циклу не привязан). Графа два:

| Файл | Цель плагина | Что показывает |
|---|---|---|
| `docs/dependencies/reactor.*` | `aggregate` | 17 проектов реактора и всё, что они тянут; версии на узлах |
| `docs/dependencies/archi-bootstrap.*` | `graph` + `showDuplicates`, `showConflicts` | то же дерево плюс дубли и конфликты версий |

Два, а не один, потому что `showDuplicates` и `showConflicts` понимает только
цель `graph`: у `aggregate` таких параметров нет вовсе, и попытка задать их
в `pom.xml` роняет сборку — `Cannot find 'showDuplicates'`. Поэтому флаги
живут в скрипте, рядом с той целью, которой принадлежат.

`.puml` коммитятся вместе с `.png`: изменение зависимостей читается в диффе
по тексту, а по PNG не читается никак.

Требуется JDK 25 (`maven.compiler.source/target=25`). Maven приходит обёрткой:
`./mvnw` качает Maven 3.9.16 сам, версия зафиксирована в
`.mvn/wrapper/maven-wrapper.properties`. Обёртка скриптовая (`only-script`),
jar'а в репозитории нет.

Сборка идёт по [§10.2](docs/archi-creator.md#102-сборка): `frontend-maven-plugin`
делает `npm ci && npm run build` в `src/main/frontend`, результат ложится
в `target/classes/static`, `spring-boot:repackage` запекает всё в
`archi-creator-1.0-SNAPSHOT.jar`. Node ставит сборка, а не разработчик:
версия в `pom.xml` родителя, каталог — `archi-bootstrap/.node/` (вне `target/`,
иначе `mvn clean` качал бы его заново).

Помощнику нужен `ANTHROPIC_API_KEY` в `.env`; модель, адрес API и пределы —
`archi.ai.*` в `application.yaml` (`ARCHI_AI_MODEL`, `ARCHI_AI_BASE_URL`).

Порты в `docker-compose.yml` — переменные со значениями по умолчанию
из [§10.1](docs/archi-creator.md#101-состав): `ARCHI_APP_PORT` (8080),
`ARCHI_DB_PORT` (5432), `ARCHI_KEYCLOAK_PORT` (8081). Занят соседним проектом —
меняется `.env`, а не состав.

## Стиль кода держит сборка

Стандарт — внутренний `java-spring-code-guidelines-v2.md` с отступлениями по
[`ADR-0021`](spec/adr/0021-source-style.md) (отступ 4, строка 120, импорты Google,
сложность до 25, без `@author`). Нарушение роняет сборку, а не ревью:

| Что | Чем | Когда |
|---|---|---|
| Строка ≤ 120, без табуляции; импорты — без `*`, без лишних, статические первым блоком, остальные одним по алфавиту; Javadoc у публичных типов, публичных и protected методов (кроме `@Override`) и публичных констант основного кода | Checkstyle, `config/checkstyle.xml` | `validate` |
| Сложность метода ≤ 25, нет пустого `catch` (и с комментарием), нет `var`, нет `System.out` и `printStackTrace`, не ловятся `NullPointerException`, `RuntimeException`, `Exception`, `Throwable`, `Error` — в основном коде и тестах | PMD, `config/pmd-ruleset.xml` | `process-test-classes` |
| Параметры публичных методов без `?`; зависимости только конструктором, поля бинов `final`; связи JPA без `EAGER`; `findAll()` порта без предела — только в списке исключений; Jackson 3, не 2; сущности агрегата меняет только агрегат | ArchUnit, `CodingRulesTest` | `test` |
| Типы домена ([`ADR-0027`](spec/adr/0027-domain-types-and-absence.md)): `Optional` не поле и не параметр (только результат поиска и локальная переменная); ключи `*Id` — `BaseId`; корни агрегатов из раздела `aggregates:` спецификации — `AggregateRoot` без публичных конструкторов; исключения домена — `DomainException` контекста; события — `{Агрегат}{Действие}Event` через тип агрегата | ArchUnit, `CodingRulesTest` | `test` |
| Покрытие строк: `domain` 90 %, `application` 80 %, адаптеры 50 %, весь код 60 % | JaCoCo + `tools/check-coverage.py` | после `verify` |

Ловится то, что код умеет обработать: исключение разбора значения, отказ контекста.
Перехват всего допустим только на границе, которая обязана записать или пережить любой отказ
(журнал попытки, счётчик отказов, фоновый проход, поток клиента), — строкой
`// NOPMD - причина` на самом `catch`.

Конфигурация Checkstyle и PMD в `<configuration>` родителя сильнее `-D`: чтобы увидеть
все нарушения сразу, а не первого модуля, — `./mvnw -fae`. Javadoc — по правилу
комментариев ниже: суть поведения, без ссылок на спецификацию.

## Идентификаторы: UUID только через java-uuid-generator

**UUID создаются и разбираются только библиотекой `com.fasterxml.uuid:java-uuid-generator`**
([`ADR-0020`](spec/adr/0020-uuid-generator.md)). Тип значения — по-прежнему
`java.util.UUID`: библиотека выдаёт его же, менять сигнатуры, записи и сущности не нужно.

- **Новый идентификатор** — версия 7. В коде контекстов — `UuidV7` своего домена
  (`ElementId.next(uuids)` и т. п.): он отдаёт библиотеке часы приложения, поэтому
  время фиксируется в тестах через `Clock`. В основном коде `Generators` зовёт только
  `UuidV7`: идентификаторы Archi (`ArchiIdGenerator`) тоже берут `UuidV7::next`. В тестах —
  `Generators.timeBasedEpochGenerator()`; в тестовом классе — поле
  `IDS = Generators.timeBasedEpochGenerator()`.
- **Строка в UUID** — `UUIDUtil.uuid(value)`, а не `UUID.fromString`.
- **Детерминированный UUID в тесте** — `UUIDUtil.constructUUID(UUIDType.TIME_BASED_EPOCH, hi, lo)`.
- **Запрещены** везде, тесты включительно: `UUID.randomUUID()`, `UUID.nameUUIDFromBytes(…)`,
  `UUID.fromString(…)`, `new UUID(long, long)` — и вызовом, и ссылкой на метод
  (`UUID::randomUUID` в `Supplier<UUID>` — тот же вызов, отложенный). Случайная версия 4 рядом с версией 7
  размывает «ключи монотонны», а своя сборка битов — место для ошибки в версии и варианте.
- Версия библиотеки — в `dependencyManagement` родителя, `slf4j-api` из неё исключён:
  доменным модулям логгер не нужен, а генератор версии 7 и разбор его не трогают.

Проверяет `UuidRulesTest` (`archi-bootstrap`, пакет `architecture`): ArchUnit по байткоду
основного кода (вызов и ссылка на метод — `UUID::randomUUID` тоже) и сканирование исходников
всех модулей. Фронтенда правило не касается —
у браузера свой генератор версии 7 (`frontend/src/model/ids.ts`).

## Комментарии в коде

**Комментарий описывает поведение кода, а не ссылается на спецификацию.** В JavaDoc,
строчных и блочных комментариях всех файлов — Java, TypeScript, SQL, YAML, XML,
`pom.xml`, CSS — нет ссылок на документы разработки:

- номеров разделов `docs/` (`§10.3`, `docs/backend.md §8`) и этапов плана (`этап 2`);
- якорей спецификации `FR-xx`, `NFR-xx`, `INV-<BC>-NNN`, `UC-<BC>-NNN`, `UI-NNN`, `ADR-NNNN`;
- путей к файлам спецификации и обоснований (`spec/…`, `docs/…`) как к источнику «почему».

Вместо ссылки — сама суть правила своими словами: «идентификатор берётся буквально,
при импорте не перегенерируется», а не «по INV-IXC-001». Номер устаревает и молча
начинает указывать не туда; суть читается без второго окна.

Правило касается **только комментариев**. Якоря в коде как данные остаются:
коды ошибок (`"INV-MDL-006"`), `@DisplayName` тестов, сообщения исключений,
константы — на них опираются контракт API и трассируемость тестов. Ссылки
на требования живут в коммитах и планах (см. «Коммиты»), а не в исходниках.
Путь к файлу, который код действительно читает в работе, — не ссылка на документацию,
а описание поведения, и он остаётся.

Исключение — **применённые миграции Flyway** (`db/migration/V*.sql`). Flyway считает
контрольную сумму по всему файлу, комментарии включительно: правка комментария
в `V1`/`V2` роняет `validate` на каждой уже развёрнутой базе. Старые миграции
не правятся, правило действует для новых. Фикстуры тестов (`*.archimate`) — данные,
а не код, их правило тоже не касается.

Проверяет `tools/check-comment-refs.py`, гейт в CI — шаг работы `spec`. Там же
список замороженных миграций (`FROZEN`) и поштучных исключений (`EXEMPT`) с причиной
у каждого: исключение уместно, только когда упоминание описывает поведение — код
читает этот файл или это буквальное значение. Новая миграция в `FROZEN` попадает,
когда её применили хоть на одной базе.

## Контроллер и сценарий

**Контроллер разбирает запрос и вызывает один сценарий — решений он не принимает.**
Что допустимо, какой веткой обслуживается, что подставить по умолчанию, какое
правило нарушено — решения application-слоя или домена. Решение в контроллере
не видит второй вход в тот же сценарий (планировщик, Git, другой адаптер),
и правило начинает жить в двух местах.

В контроллере:

- **можно:** разобрать параметры и тело в запрос сценария (строку — в тип,
  «не задано» передать `null`), отказать в неразборчивом вводе через
  `ModelingDomainException.invalid(...)` («поле не задано», «не тот формат значения»),
  вызвать **один** метод сценария, перевести результат в DTO и статус HTTP;
- **нельзя:** ветвиться по значению запроса, выбирая сценарий (`switch (fmt)`),
  подставлять умолчания со смыслом (пространство, размер узла, имя файла),
  искать объекты и отвечать «не найдено», отказывать с бизнес-кодом, ходить
  в порты и хранение, вызывать несколько сценариев подряд.

Умолчание со смыслом живёт рядом с правилом: размер нового узла — `Bounds.ofNewElement`,
пространство по умолчанию — `WorkspaceResolveQueryHandler`, формат выгрузки —
`ModelExportQueryHandler` по `ExportModelQuery`.

Проверяют `RestLayerConventionsTest` (ArchUnit: контроллер не создаёт исключений
контекстов, не вызывает `notFound`, не зависит от портов, кроме `TextCatalog`,
и от хранения) и `tools/check-string-literals.py` (`case "…"` и ссылка на `*Codes` в контроллере:
код — константа-строка, компилятор подставляет её, и ArchUnit ссылки не видит).

## Тексты и локализация

**В коде — ключ сообщения, текст — в ресурсах локали.** Ни русской строки, ни ссылки
на спецификацию в строковых литералах основного кода нет: интерфейс должен
переводиться подстановкой ресурса, а не правкой кода, и документ разработки
не должен уходить пользователю в тексте ошибки.

- **Сервер.** Исключение несёт `Message(key, args)` (`ModelingDomainException`,
  `InterchangeException`, `InvalidValueException` своего контекста), ключи —
  константы классов `ModelingMessages`, `InterchangeMessages`, `BootstrapMessages`,
  тексты — `archi-bootstrap/src/main/resources/i18n/messages.properties` (русский,
  по умолчанию) и `messages_en.properties`. Язык — из `Accept-Language`. Аргумент
  может быть `Message` — так подпись объекта («модели», «папки») переводится
  вместе с фразой. Текст, который сервер сохраняет как данные (комментарий версии,
  находка импорта, значения CSV), собирается через порт `TextCatalog`.
- **Коды ошибок** (`INV-MDL-006`, `MDL_NOT_FOUND`) — контракт API, константы классов
  `ModelingCodes` и `InterchangeCodes`, во фронтенде — `src/api/codes.ts`. Только там
  строка с якорем и допустима; код ошибки вне `*Codes` (литерал `IXC_…` или константа
  вида `КОД_ОШИБКИ`) роняет `tools/check-string-literals.py`.
- **Тексты MessageFormat:** подстановки `{0}`, без апострофов (служебный символ),
  кавычки — «ёлочки»; ведущий пробел в `.properties` экранируется `\ `.
- **Фронтенд:** весь текст — `src/i18n/`, клиент шлёт `Accept-Language` языка
  интерфейса, отказы сервера приходят на нём же.

Проверяют `tools/check-string-literals.py` (шаг CI работы `spec`), `MessageBundlesTest`
(каждый ключ переведён на оба языка, подстановки совпадают), `LocalizedProblemIT`
и `checks/static-rules.test.ts` во фронтенде. Новый ключ — константа и строка
в обоих файлах локали в одном коммите.

## Коммиты

### Формат — conventional commits, описание по-русски

```
тип(область): описание со строчной буквы без точки на конце

Тело: зачем сделано, а не что изменилось — «что» видно из дифа.
Ссылка на требование или якорь: FR-02, INV-IXC-005, UC-MDL-001, ADR-0001.

Co-Authored-By: …
```

- **Тип** обязателен: `feat`, `fix`, `spec`, `docs`, `test`, `refactor`, `perf`,
  `build`, `ci`, `chore`, `revert`. `spec` — правка нормативной спецификации
  `spec/`, она отделена от `docs` намеренно: одно меняет требования, другое
  объяснения.
- **Область** необязательна, но берётся из закрытого списка: bounded context
  (`modeling`, `interchange`, `advisor`), слой `ui`, часть реализации (`codec`,
  `metamodel`, `rest`, `persistence`, `db`, `export`, `oef`, `git`, `ai`,
  `security`, `frontend`, `bootstrap`) или часть репозитория (`spec`, `docs`,
  `plans`, `tools`, `pom`, `docker`, `ci`).
- **Единственный список типов и областей — в `tools/check-commit-message.py`.**
  Здесь перечисление для чтения; новая область добавляется в скрипт, иначе
  проверка её не пропустит. Два списка разошлись бы на третьей неделе.
- Заголовок ≤ 72 символов, тело отделено пустой строкой, строки тела ≤ 100.
- Слом совместимости — `!` перед двоеточием и объяснение в теле.
- **`feat` и `spec` обязаны ссылаться на требование или якорь**: изменение
  по существу трассируемо, иначе непонятно, чьё требование оно закрывает.
  Скрипт на это предупреждает, а не отказывает — у правки инструментов
  или каркаса якоря может не быть.

Проверяет `tools/check-commit-message.py`: хук `.githooks/commit-msg`
(включается `git config core.hooksPath .githooks` — клон хуки не приносит)
и гейт в CI `tools/check-commit-message.py --range origin/main..HEAD`.
Правило действует с коммита, которым введено: четыре коммита до него ему
не подчиняются, поэтому в CI проверяется диапазон новых коммитов, а не ветка.

### Один таск — один коммит

**Изменения коммитятся по завершении каждого таска, а не пачкой в конце.**
Таск — один пункт в `docs/plans/<план>.md` (сделанное ✅, несделанное ⬜);
если работа идёт без плана —
атомарная законченная задача.

- В коммит входит всё, что закрывает таск: код, тест из таблицы «Маппинг
  на тесты», правка спеки, если она понадобилась, и **отметка ✅ в плане**.
  План коммитится вместе с кодом: через месяц «почему сделано так» отвечает он.
- Два таска в одном коммите — нет: откат одного потянет второй, а `git log`
  перестаёт отвечать на вопрос «когда это появилось».
- Незакоммиченных правок в конце таска не остаётся. Если таск оказался больше,
  чем казался, он дробится на подзадачи в плане, и коммит идёт на каждую.
- До коммита проходят тесты затронутых модулей; при правке `spec/` или `docs/` —
  `tools/check-links.py` и `tools/check-traceability.py`, при правке требований —
  `tools/render-requirements-index.py`.
- **Это постоянное разрешение на коммит:** спрашивать перед каждым не нужно.
  `git push` — по явной просьбе: публикация решается отдельно от фиксации.

## Спецификация: как она устроена

Два каталога с разными ролями. `spec/` говорит, **что** система обязана делать, и это
нормативно. `docs/` объясняет, **почему** так решено, и нормативным не является.

### `spec/` — нормативная часть

| Путь | Что внутри |
|------|-----------|
| [`spec/product/requirements.yaml`](spec/product/requirements.yaml) | **Реестр требований**: 53 FR + 8 NFR, нормативный текст, владелец, этапы, якоря |
| [`spec/domain/<bc>/`](spec/domain/) | Агрегаты, инварианты `INV-<BC>-NNN`, события, глоссарий трёх контекстов: `modeling` (MDL), `interchange` (IXC), `advisor` (ADV) |
| [`spec/application/<bc>/usecases/`](spec/application/) | Use case'ы `UC-<BC>-NNN`: Given/When/Then, порты, отказы |
| [`spec/ui/`](spec/ui/README.md) | Слой интерфейса: правила `UI-NNN`, токены стиля, контракт геометрии фигур |
| [`spec/contracts/<bc>/`](spec/contracts/) | OpenAPI и AsyncAPI |
| [`spec/adr/decisions.yaml`](spec/adr/decisions.yaml) | Единственный регистр решений: 28 записей, шестнадцать с файлами MADR |
| [`spec/nfr/<bc>.yaml`](spec/nfr/) | SLO, latency, throughput, security по контекстам |

Правила при правке `spec/`:

- **Якоря не переиспользуются.** Удалённый помечается `[DEPRECATED]`, новый получает
  следующий свободный номер. На якоря ссылаются тесты, ошибки API и ADR.
- **Требования объявляются только в `product/requirements.yaml`.** В остальных файлах —
  ссылки `FR-xx`. Новый якорь обязан указать требование, новое требование — якорь:
  и то и другое проверяет `tools/check-traceability.py`.
- **Интерфейс — слой, а не bounded context.** У него нет агрегатов и событий, он
  проекция `modeling`. Заводить `domain/ui/` не нужно.
- **Пробел объявляется, а не замалчивается.** Требование без якоря сопровождается полем
  `note` с объяснением — иначе проверка падает.

### `docs/` — обоснования

Четыре файла, **сквозная нумерация разделов**. Раздел сохраняет свой номер независимо от
того, в каком файле лежит: `§5.2` — это экспорт в CSV, где бы на него ни ссылались.

| Файл | Разделы |
|------|---------|
| [`docs/archi-creator.md`](docs/archi-creator.md) | §1 обзор, §2 требования FR/NFR, §3.1–3.2 схема и стек, §10 развёртывание, §12 этапы, §13 открытые вопросы |
| [`docs/backend.md`](docs/backend.md) | §3.3–3.5, §5 API, §6.10 экспорт картинок, §7 ИИ, §8 ошибки, §9.1–9.3 тесты, §11 Git |
| [`docs/frontend.md`](docs/frontend.md) | §6.1–6.9 визуальный язык и экран, §9.4 E2E |
| [`docs/database.md`](docs/database.md) | §4 модель данных, §11.6 поля под Git |

Правила при правке спецификации:

- **Номера разделов не меняются.** На них ссылаются из всех четырёх файлов и из планов;
  перенумерация ломает десятки ссылок. Новое — подразделом или в конец.
- **§2 не правится руками.** Это производная таблица: требования живут в
  `spec/product/requirements.yaml`, §2 порождается из него
  (`tools/render-requirements-index.py`). Правка §2 напрямую будет затёрта
  при следующей генерации и поймана флагом `--check`.
- §13 «Открытые вопросы» — хронология обсуждения: вопрос не удаляется, а зачёркивается
  с датой и ссылкой на раздел, где решение описано. Состояние решения при этом живёт
  в `spec/adr/decisions.yaml` — это регистр, а §13 журнал.
- Главный файл держит у себя указатели вида «→ `backend.md` §3.4 — …» вместо уехавшего
  текста. Переносишь раздел — оставь указатель.

## Архитектура

Слоёное Spring Boot-приложение, один jar (фронтенд внутри). Подробности — §3.3, здесь
только то, что не видно из структуры пакетов.

**PostgreSQL — источник правды, `.archimate` — результат сериализации.** Файл не способ
хранения: модель разложена по таблицам (`Element`, `Relationship`, `View`, `ViewNode`,
`ViewEdge`, `ModelFolder`), а импорт/экспорт — преобразования на границе. Git-репозиторий
(этап 7a) — производная величина, журнал и ревью, а не распределённое редактирование.

**Lossless round-trip (FR-02, NFR-05) — критический контур всего проекта.** Цикл «открыть
→ ничего не менять → сохранить» обязан давать семантически идентичный XML; golden-file
тесты на `docs/Hamkorbank_AS_IS_strict.archimate` гоняются на каждом коммите, регрессия
блокирует сборку. Из этого следуют инварианты, которые нельзя нарушать нигде в коде:

- **Идентификаторы сохраняются буквально** (любой допустимый для Archi; новые — `id-<32 hex>`), при импорте не перегенерируются.
- **Папки — часть модели**, а не производная от типа элемента; дерево папок хранится в БД.
- **Порядок узлов сохраняется**: `sort_order` в пределах родителя, писатель обходит дерево
  в порядке читателя. `sort_order` **разреженный, шаг 1000** — вставка не перенумеровывает
  соседей, иначе каждая правка даёт diff на всю папку при выгрузке в Git.
- **`bounds` относительны родителю** (у детей `Group` — относительно группы).
- **Неизвестное не теряется**: неподдержанные узлы и атрибуты хранятся как `raw_xml`
  (`RawXmlFragment`, флаг `supported = false`) и выводятся без изменений (FR-03).
- **`targetConnections` — производная величина**, восстанавливается из `sourceConnection`,
  но порядок идентификаторов в атрибуте сохраняется как есть.
- `UNIQUE (model_id, archi_id)` на всех сущностях с `archi_id`; связи —
  `ON DELETE RESTRICT`, удаление элемента требует явного удаления связей (как в Archi).

**Фронтенд.** React 19 + TypeScript + Vite, канва на React Flow, узлы — собственные
SVG-компоненты. Тема одна: графитовые панели, светлый холст, переключателя нет (§6.2).
Фигуры рисуются **инлайн-SVG, а не CSS**, геометрия выносится в общий `shapes.json` —
потому что тот же контур печатает серверный `SvgWriter` при экспорте (§6.10). Приоритет
стиля: собственный `fillColor`/`font` объекта → токен слоя (FR-21).

## Границы, о которых легко забыть

Эти «нет» — принятые решения с обоснованием в §13, а не недоделки. Не предлагай их
как улучшения:

- **ИИ не изменяет модель.** Любой результат — текст, ссылки на элементы, подсказки;
  правки вносит только архитектор (FR-22). `/ai/plan` возвращает `ModelPlan` — описание,
  а не мутацию.
- **Импорта нет ни из OEF, ни из CSV.** Единственный принимаемый формат — `.archimate`.
  Обещание lossless относится только к нему; выгрузка в OEF best-effort и обязана
  сопровождаться отчётом о потерях.
- **PDF не делаем** — остаётся печать из браузера. Серверный экспорт: SVG и PNG.
- **Тёмной темы не будет.**
- **Текстовое слияние YAML запрещено** — слияние только по сущностям через стабильные
  идентификаторы (§11.4).
- Ключ Anthropic живёт только на сервере и не попадает в браузер (NFR-06).

## Этапы

Порядок работ и критерии готовности — [§12](docs/archi-creator.md#12-этапы-работ).
Коротко: 1 — кодек, 2 — БД и API, 3–4 — канва и редактирование, 5/5a — ИИ и экспорт
картинок, 6/6a — метамодель фазы 2 и OEF, 7a–7c — Git. Этап 1 закрывается round-trip
референсной модели; браться за канву раньше, чем кодек проходит golden-file тесты, смысла
нет — весь фронтенд опирается на структуру, которую даёт кодек.
