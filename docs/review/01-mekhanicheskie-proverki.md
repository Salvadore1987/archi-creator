# Этап 1. Механические проверки по всей кодовой базе

**Дата:** 2026-10-15. **Репозиторий:** ветка `cr_phase_1`, после `456b7ba`. **Стандарт:** коммит `97bce16`,
§2.1–§2.5, §2.8–§2.11, §6.18, Приложение C, чеклист §5.3.
**Объём:** основной код — 474 файла `src/main/java`, 31 886 строк; тесты — 143 файла
`src/test/java`, 14 591 строка. Код глазами не читался: текстовые выборки скриптом
(комментарии и строковые литералы вырезаются до поиска), разовые прогоны Checkstyle 14.3.0
и PMD 7.28.0 вне сборки; каждое попадание проверено по строке кода.

Этапы 2–17 эти правила не перепроверяют и ссылаются на номера ниже.

**Исправлено 2026-10-15** — план [`code-review-fixes-01.md`](../plans/code-review-fixes-01.md).
Все находки закрыты, у каждой появилась проверка в сборке; 01-09 и 01-11 закрыты решением
владельца в ADR-0021 (порядок импортов Google, предел сложности 25), 54 отступления
от порядка Google выправлены. Колонка «Ловит сборка?» ниже — состояние на момент ревью.

## Находки

| № | Уровень | Правило | Где | Суть | Предложение | Ловит сборка? |
|---|---|---|---|---|---|---|
| 01-01 | ЗАПРЕЩЕНО | §2.1.1 | основной код — 27 мест в 13 файлах, тесты — 32 места в 11 файлах (приложение А) | `var` в локальных переменных и `try`-ресурсах | явный тип | нет → Checkstyle `MatchXpath` по `var` или скрипт `tools/` (байткод ArchUnit `var` не видит) |
| 01-02 | ЗАПРЕЩЕНО | §2.1.3 | `GitYamlReader.java:3`, `GitYamlWriter.java:3` | `import static …GitYamlFormat.*` | поимённые статические импорты | нет → Checkstyle `AvoidStarImport` |
| 01-03 | ЗАПРЕЩЕНО | §2.1.2 | порты `DomainEventPublisher.publish(Collection<? extends DomainEvent>)`, `AdvisorEvents.publish(Collection<? extends DomainEvent>)`, `InterchangeEvents.publish(Collection<?>)`; реализации `AfterCommitEventPublisher:22`, `OutboxEventPublisher:30` | generic wildcard в параметре публичного метода; у `InterchangeEvents` — ещё и нетипизированный `Collection<?>` | `<E extends DomainEvent> void publish(Collection<E>)` или `List<DomainEvent>`; у interchange — свой тип события вместо `?`. Приватные методы с `?` (5 мест) стандарт допускает | нет → ArchUnit: параметры публичных методов без `JavaWildcardType` |
| 01-04 | ЗАПРЕЩЕНО | §2.1.6, §2.10 | 16 JPA-адаптеров в трёх `*-adapter-persistence` (приложение Б) | `@PersistenceContext private EntityManager em;` — внедрение в поле, поле не `final` | внедрение через конструктор (`private final EntityManager em`): Spring Data JPA регистрирует общий `EntityManager`-прокси бином | нет → ArchUnit: поля не аннотированы `@PersistenceContext`/`@Autowired`, поля бинов — `final` |
| 01-05 | ЗАПРЕЩЕНО | §2.1.7, §4.7 | `GitBindingEntity.java:44` | `@ElementCollection(fetch = FetchType.EAGER)` на авторах привязки | `LAZY` и `JOIN FETCH` в запросе чтения привязки (или `EntityGraph`) | нет → ArchUnit по значению `fetch` в аннотациях связей |
| 01-06 | ЗАПРЕЩЕНО | §2.1.7 | `WorkspaceRepository.findAll()` (порт), `JpaWorkspaceRepository:20`, вызов `WorkspaceService:52` | выборка всех пространств без предела, чтобы взять первое | метод порта «первое пространство» (`findFirst()` → `Optional`) с `LIMIT 1` в адаптере. Пагинация списков в REST — отдельно, D-16. `ModelAccessListRepository.findAll(Collection<ModelId>)` ограничен входом — не находка | нет → ArchUnit: метод `findAll` без параметра `Pageable` в портах запрещён |
| 01-07 | ОБЯЗАТЕЛЬНО | §6.18, §5.3 | `AnthropicLlmClient.java:13–14, 40, 159` | Jackson 2 (`com.fasterxml.jackson.databind.ObjectMapper`) рядом с Jackson 3 (`tools.jackson`, 7 импортов в остальном коде) | разбор аргументов вызова — через `tools.jackson.databind.json.JsonMapper`; Jackson 2 остаётся только транзитивно от SDK Anthropic | нет → ArchUnit: `noClasses().should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson.databind..")` |
| 01-08 | ЗАПРЕЩЕНО | §2.3 | `InterchangeProblemHandler.java:16` (`InterchangeCodes`), `SvgWriter.java:24` (`ViewScene.Kind`), `Workspace.java:7` (`Names`) | неиспользуемые импорты | удалить | нет → Checkstyle `UnusedImports` |
| 01-09 | ОБЯЗАТЕЛЬНО | §2.3 | 428 из 617 файлов (основной код и тесты) | порядок импортов проекта — Google Java Style: статические первыми, остальные одним блоком по алфавиту. Стандарт требует четыре группы через пустую строку: `java`/`javax` → сторонние → `uz.salvadore…` → статические. Своему порядку проект следует почти везде: отступлений от него 54 в 13 файлах (приложение В) | **решение владельца:** ADR-0021 прямо оставляет §2.3 в силе. Либо дополнить ADR-0021 порядком Google (переформатирование 428 файлов ломает `blame` так же, как отступ), либо переформатировать отдельным коммитом без смысловых правок и внести его в `.git-blame-ignore-revs`. В любом случае — 54 отступления выправить | нет → Checkstyle `CustomImportOrder` с выбранным порядком |
| 01-10 | ЗАПРЕЩЕНО | §2.11 (предел 25) | `GitYamlReader.java:211` `node(Fields, String)` — 30 | цикломатическая сложность выше предела | разбить по видам узла (таблица обработчиков или отдельные методы), эталоны `GitYamlWriterTest` держат поведение | нет → PMD `CyclomaticComplexity`, `methodReportLevel=26` блокирующим |
| 01-11 | РЕКОМЕНДУЕТСЯ | §2.11 (цель 10) | 15 методов со сложностью 11–18 (приложение Г, топ-20) | выше целевой сложности | по месту, на этапах 2–13. `SvgWriter`, `Routing`, `OefWriter` — только с пересъёмкой и сверкой эталонов (этап 7) | нет → PMD с предупреждением на 11 |
| 01-12 | ОБЯЗАТЕЛЬНО | Приложение C | `AdvisorService.java:365`, `AdvisorController.java:94` | `catch (RuntimeException clientGone) { }` — пустой, с комментарием о причине; `catch (RuntimeException broken) { emitter.completeWithError(broken); }` — без лога: пишет ли Spring ошибку асинхронного ответа в журнал, не проверено | в обоих — строка лога (обрыв клиента — без стека, ошибка потока — со стеком), если Spring её не пишет сам; проверить на этапе 11 (SSE) и 14 (логирование) | нет |
| 01-13 | ОБЯЗАТЕЛЬНО | §2.6.1 (D-02) | все модули (приложение Д) | Javadoc: публичные типы — 464 из 545 (85 %), без Javadoc 81 тип, из них 52 — записи DTO в классах-держателях (`Dtos.java` трёх адаптеров REST, `SessionDtos`) и 19 — JPA-сущности с вложенными ключами; публичные методы классов (без `@Override`) — 477 из 951 (50 %); методы интерфейсов — 76 из 146 (52 %). Хуже всего — `archi-advisor-adapter-rest` (типы 20 %, методы 0 %), `archi-modeling-adapter-rest` (41 %, 24 %), `archi-modeling-adapter-persistence` (36 %) | дописать по модулям, начиная с портов и адаптеров REST; содержание — по правилу комментариев проекта | нет → Checkstyle `MissingJavadocType`, `MissingJavadocMethod` (scope `public`, `allowMissingPropertyJavadoc`) |
| 01-14 | ОБЯЗАТЕЛЬНО | §4.14.2 (D-17) | все `pom.xml` | JaCoCo не подключён ни в одном модуле: покрытие не измеряется, порогов нет | `jacoco-maven-plugin` в `pluginManagement` родителя, отчёт агрегатом в `archi-bootstrap` (IT покрывают адаптеры), пороги: проект 60 %, `domain` 90 %, `application` 80 % | нет — это сама проверка |
| 01-15 | РЕКОМЕНДУЕТСЯ | §4.4.9 | `LoadProfileIT.java:63` (тест) | `System.out.println` с конкатенацией | отчёт замера — в журнал теста через SLF4J с `{}` или в файл отчёта failsafe | нет |

## Проверено — нарушений нет

| Правило | Результат |
|---|---|
| §2.1.4 `@Value`, `@Autowired`, `@Inject` в основном коде | 0. В тестах `@Autowired` на полях — 32 места в 10 классах `*IT`: стандарт сам так пишет тесты (§4.14), не находка |
| §2.1.6 setter injection | 0. `NativeQueryImportPolicy.setStrictImport` — метод порта, не внедрение |
| §2.8 Lombok `@Data`/`@Setter` | Lombok не подключён — 0 импортов `lombok.` |
| §2.4 табуляция, CRLF, BOM, кодировка | 0; все 617 файлов читаются как UTF-8 |
| §2.4 длина строки | свыше 120 — 0 (держит Checkstyle на `validate`); свыше 100 — 1 736 в основном коде и 1 226 в тестах, принято ADR-0021 (D-01) |
| §2.4 скобки всегда (`NeedBraces`) | 0 |
| §2.1.7 закомментированный код | 0 в основном коде; в тестах одно попадание эвристики (`SnapshotRetentionTest:34`) — комментарий-фраза, не код |
| §6.18 `RestTemplate`, `@MockBean`/`@SpyBean` | 0 |
| §6.18 `javax.*` | только `javax.xml.*` (StAX, валидация, DOM) и `javax.imageio` — пакеты JDK, из Framework 7 удалены `javax.servlet`/`javax.persistence` и подобные; не находка |
| §4.4.9 `printStackTrace`, `System.out`/`err` в основном коде, конкатенация в вызовах логгера | 0 |
| Приложение C `catch (Exception e)` | широкий `catch` — 8 мест в основном коде: 4 пробрасывают, 2 логируют, 2 — находка 01-12. Узкий пустой `catch` — один, `JGitRepository:150` (`MissingObjectException`): ожидаемая ветка разбора ссылки с комментарием, не находка |

## Заметки для следующих этапов

- **Этап 14.** `AdvisorService:356` при обрыве потока логирует только имя класса исключения,
  без стека; application-слой пишет через `System.Logger` (без SLF4J — граница enforcer).
  Сверить с §4.4: «ошибки — со stack trace».
- **Этап 10.** `GitBindingEntity.authors` — публичное изменяемое поле сущности (`public List<Author>`),
  как, вероятно, и в остальных сущностях; проверить инкапсуляцию.
- **Этап 15.** Кроме Checkstyle (длина строки, табуляция) в сборке ничего из §2 не проверяется:
  все находки этапа — «нет». Конфигурация разовых прогонов — ниже, в «Как воспроизвести».

## Как воспроизвести

Скрипт текстовых выборок жил в каталоге сессии и в репозиторий не вошёл: проверки,
которые останутся, заводятся в сборке (этап 15). Разовые прогоны:

```bash
# Checkstyle: импорты и скобки (stand-alone jar 14.3.0)
#   UnusedImports, AvoidStarImport, RedundantImport, NeedBraces,
#   CustomImportOrder: STANDARD_JAVA_PACKAGE###THIRD_PARTY_PACKAGE###SPECIAL_IMPORTS###STATIC,
#   specialImportsRegExp=^uz\.salvadore\., separateLineBetweenGroups, sortImportsInGroupAlphabetically
java -jar checkstyle-14.3.0-all.jar -c cs-imports.xml $(find . -path '*/src/*/java/*.java')

# PMD: category/java/design.xml/CyclomaticComplexity, methodReportLevel=7
pmd check --use-version java-25 -R pmd-cc.xml --file-list main-files.txt -f csv
```

Счёт Javadoc: публичный тип (вложенные включительно) или метод с модификатором `public`,
над которым после аннотаций стоит `/** … */`; методы с `@Override` не считаются — Javadoc
наследуется. На этапе 0 типы считались только верхнего уровня (373 из 392), методы —
вместе с `@Override` (~30 %); отсюда расхождение чисел, не состояния кода.

## Приложение А. `var` (01-01)

Основной код:

| Мест | Файл |
|---|---|
| 5 | `archi-modeling-adapter-rest/…/ContentController.java` |
| 4 | `archi-modeling-adapter-persistence/…/JpaModelRepository.java` |
| 3 | `archi-modeling-adapter-persistence/…/JpaViewRepository.java` |
| 3 | `archi-modeling-adapter-rest/…/ViewController.java` |
| 2 | `archi-bootstrap/…/bootstrap/advisor/AnthropicLlmClient.java` |
| 2 | `archi-modeling-application/…/service/LockService.java` |
| 2 | `archi-modeling-application/…/service/RelationshipService.java` |
| 1 | `archi-interchange-application/…/exporting/ExportService.java` |
| 1 | `archi-interchange-application/…/mapping/DocumentAssembler.java` |
| 1 | `archi-modeling-adapter-rest/…/ModelController.java` |
| 1 | `archi-modeling-adapter-rest/…/VersionController.java` |
| 1 | `archi-modeling-application/…/service/ElementService.java` |
| 1 | `archi-modeling-domain/…/metamodel/ArchiTypeRegistry.java` |

Тесты:

| Мест | Файл |
|---|---|
| 15 | `archi-modeling-domain/…/view/layout/AutoLayoutTest.java` |
| 5 | `archi-modeling-application/…/service/ModelingScenariosTest.java` |
| 3 | `archi-modeling-application/…/service/ClientIdsScenariosTest.java` |
| 2 | `archi-bootstrap/…/api/ViewNodeTextIT.java` |
| 1 | `archi-bootstrap/…/api/ModelAclIT.java`, `…/api/ViewEditingIT.java`, `…/git/GitApiTest.java`, `…/persistence/ModelPersistenceIT.java`, `…/persistence/PlacementsIT.java` |
| 1 | `archi-interchange-application/…/oef/OefWriterTest.java`, `archi-modeling-domain/…/view/ViewIntegrityTest.java` |

## Приложение Б. Внедрение `EntityManager` в поле (01-04)

- `archi-advisor-adapter-persistence`: `JpaAdvisorSessionRepository`, `JpaAuditLog`,
  `JpaTokenBudgetRepository`, `NativeQueryAdvisorSettings`
- `archi-interchange-adapter-persistence`: `JpaGitBindingRepository`, `JpaGitOutbox`,
  `JpaImportSessionRepository`, `JpaModelMerges`, `NativeQueryImportPolicy`
- `archi-modeling-adapter-persistence`: `JpaIdempotencyRepository`, `JpaModelAccessListRepository`,
  `JpaModelLockRepository`, `JpaModelRepository`, `JpaModelVersionRepository`, `JpaViewRepository`,
  `JpaWorkspaceRepository`

## Приложение В. Отступления от собственного порядка импортов (01-09)

Порядок Google (статические, пустая строка, остальные по алфавиту) нарушен в 13 файлах, 54 места:

| Мест | Файл |
|---|---|
| 18 | `archi-interchange-application/src/test/…/exporting/CatalogCsvWriterTest.java` |
| 9 | `archi-bootstrap/src/test/…/architecture/RestLayerConventionsTest.java` |
| 8 | `archi-modeling-application/src/test/…/service/InMemoryPorts.java` |
| 3 | `archi-bootstrap/src/main/…/security/DevSecurityConfig.java` |
| 3 | `archi-interchange-application/src/main/…/exporting/ExportService.java` |
| 1 | `archi-bootstrap/src/main/…/security/SecurityConfig.java` |
| 1 | `archi-bootstrap/src/test/…/architecture/BoundedContextIsolationTest.java` |
| 1 | `archi-bootstrap/src/test/…/git/GitPublisherTest.java` |
| 1 | `archi-interchange-domain/src/main/…/codec/StaxArchiDocumentReader.java` |
| 1 | `archi-interchange-domain/src/test/…/document/ArchiIdTest.java` |
| 1 | `archi-interchange-domain/src/test/…/importing/StrictImportTest.java` |
| 1 | `archi-modeling-adapter-rest/src/main/…/DtoMapper.java` |
| 1 | `archi-modeling-domain/src/main/…/metamodel/ArchiTypeRegistry.java` |

## Приложение Г. Цикломатическая сложность, топ-20 (01-10, 01-11)

Основной код; методов со сложностью ≥ 7 — 83, ≥ 11 — 15, > 25 — 1.

| CC | Метод |
|---|---|
| 30 | `interchange/application/git/GitYamlReader.java:211` `node(Fields, String)` |
| 18 | `interchange/application/rendering/SvgWriter.java:193` `label(Node, ShapeEntry, Box, Paint)` |
| 17 | `interchange/application/rendering/EdgeNotation.java:47` `of(String, Optional<AccessType>, boolean)` |
| 14 | `interchange/domain/git/EntityMerge.java:299` `mergeObject(ArchiId)` |
| 14 | `interchange/domain/codec/StaxArchiDocumentReader.java:313` `rest(StringBuilder, boolean, int)` |
| 14 | `interchange/application/rendering/Routing.java:69` `route(Rect, Rect, List<Bendpoint>)` |
| 14 | `interchange/application/oef/OefWriter.java:763` `font(StringBuilder, DocumentNode, String, int)` |
| 13 | `interchange/application/git/GitYamlReader.java:153` `items(Fields, String, Optional<String>)` |
| 12 | `interchange/domain/git/EntityMerge.java:581` `prune(ArchiId, List<DocumentNode>, Set<ArchiId>)` |
| 12 | `interchange/application/git/PositionPlan.java:22` `plan(List<String>, Map<String, Long>)` |
| 12 | `interchange/application/git/GitPublishingService.java:295` `attempt(Entry, GitBinding, Version)` |
| 11 | `modeling/domain/view/layout/AutoLayout.java:220` `reorder(List<List<UUID>>, int, Map<UUID, Integer>, boolean)` |
| 11 | `interchange/application/oef/OefWriter.java:256` `relationships()` |
| 11 | `interchange/application/git/YamlEmitter.java:138` `plainAllowed(String)` |
| 11 | `bootstrap/git/JGitRepository.java:317` `push(Remote, Optional<GitSha>, CommitRequest)` |
| 10 | `interchange/domain/codec/StaxArchiDocumentReader.java:119` `document()` |
| 10 | `interchange/application/rendering/Svg.java:30` `escape(String)` |
| 10 | `interchange/application/oef/OefWriter.java:624` `node(StringBuilder, OutNode, int)` |
| 10 | `bootstrap/advisor/AnthropicLlmClient.java:107` `consume(LlmListener)` |
| 10 | `advisor/application/service/AdvisorService.java:209` `replay(AdvisorSession, AdvisorRequest)` |

## Приложение Д. Покрытие Javadoc по модулям (01-13)

| Модуль | Публичные типы | Публичные методы классов | Методы интерфейсов |
|---|---|---|---|
| `archi-advisor-domain` | 46/46 (100 %) | 60/132 (45 %) | 0/1 |
| `archi-advisor-application` | 31/31 (100 %) | 16/19 (84 %) | 21/40 |
| `archi-advisor-adapter-persistence` | 6/8 (75 %) | 1/1 | — |
| `archi-advisor-adapter-rest` | 4/20 (20 %) | 0/24 (0 %) | — |
| `archi-interchange-domain` | 76/76 (100 %) | 91/172 (52 %) | 2/7 |
| `archi-interchange-application` | 64/71 (90 %) | 56/90 (62 %) | 31/47 |
| `archi-interchange-adapter-persistence` | 5/9 (55 %) | — | — |
| `archi-interchange-adapter-rest` | 22/26 (84 %) | 27/36 (75 %) | — |
| `archi-modeling-domain` | 89/89 (100 %) | 132/279 (47 %) | 0/3 |
| `archi-modeling-application` | 44/45 (97 %) | 65/80 (81 %) | 20/46 |
| `archi-modeling-adapter-persistence` | 8/22 (36 %) | 0/3 | 2/2 |
| `archi-modeling-adapter-rest` | 22/53 (41 %) | 25/101 (24 %) | — |
| `archi-bootstrap` | 47/49 (95 %) | 4/14 (28 %) | — |
| **Итого** | **464/545 (85 %)** | **477/951 (50 %)** | **76/146 (52 %)** |
