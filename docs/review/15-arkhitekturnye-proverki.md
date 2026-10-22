# Этап 15. Архитектурные проверки в сборке

**Дата:** 2026-10-22. **Репозиторий:** ветка `cr_phase_15`, после `8571069` (исправления этапа 14).
**Стандарт:** коммит `97bce16`, §3.6 (ArchUnit), §4.16.2 (обязательные плагины), §2.4 (форматирование),
§2.5 (порядок элементов), §2.11 (сложность), чеклист §5.3.
**Объём:** 13 классов `archi-bootstrap/src/test/.../architecture/` (1865 строк), `config/checkstyle.xml`,
`config/pmd-ruleset.xml`, enforcer и плагины родительского `pom.xml`, `.editorconfig`; столбец «Ловит сборка?»
всех находок этапов 1–14 (164 находки «нет») против таблиц «Проверка» планов исправлений 1–14 и кода.
Разовые прогоны Checkstyle 14.3.0 и PMD 7.28.0 с правилами-кандидатами по всем 988 файлам Java.

## Находки

Пути — от `archi-bootstrap/src/test/java/uz/salvadore/hamkorbank/archi/bootstrap/architecture/`, если не указано иное.

| № | Уровень | Правило | Где | Суть | Предложение | Ловит сборка? |
|---|---|---|---|---|---|---|
| 15-01 | ОБЯЗАТЕЛЬНО | §3.6.1 `domainShouldBeFrameworkFree`; `CLAUDE.md`: в домене единственная зависимость — генератор UUID | enforcer `ban-framework` в `pom.xml` | Enforcer запрещает перечень фреймворков (Spring, JPA, Hibernate, Jackson), а не разрешает перечень: Guava, Commons или любая другая библиотека прошла бы в модуль домена молча. Сейчас домен чист | правило ArchUnit: домен зависит только от JDK (`java..`, StAX, DOM), `com.fasterxml.uuid` и своего контекста | нет → ArchUnit |
| 15-02 | кандидат в реестр | §3.6.1 `portsShouldBeInterfaces`; приложение B — выходной порт в `port/out` | `application/port` трёх контекстов | Правила нет. Выходные порты лежат в `application.port`, а не `port/out`, и рядом с ними — 7 record'ов и исключений из их сигнатуры (`ModelContent`, `ViewPlacements`, `LlmRequest`, `ToolSpec`, `ConcurrentUpdateException`, `SnapshotUnreadableException`, `LlmUnavailableException`). Буквальное правило стандарта их запрещает | решение ADR: раскладка принята; правило — «в пакете портов интерфейсы и типы их сигнатуры, без реализаций» | нет → ArchUnit |
| 15-03 | ОБЯЗАТЕЛЬНО | §3.6.1 `jpaEntitiesStayInPersistenceAdapter` | — | Правила нет. JPA есть в classpath `archi-bootstrap`: сущность, объявленная там, компилируется, и её увидел бы REST или мост | правило ArchUnit: `@Entity`, `@Embeddable`, `@MappedSuperclass` — только в `..adapter.persistence..` | нет → ArchUnit |
| 15-04 | ПРОЕКТ | `CLAUDE.md`: адаптеры зовут сценарии через входные порты | `ApplicationLayerTest` | Правило для modeling слабее, чем для interchange и advisor: запрещены только `service` и `handler`, а `mapper` и `access` открыты. Исключение `NOT_YET_MIGRATED` (`ArchimateSnapshots`) устарело: класс зовёт только `bootstrap.wiring`, у которого и так исключение. `allowEmptyShould(true)` у правил, пакеты которых есть во всех трёх контекстах: при переименовании пакета правило молча стало бы пустым | modeling — как advisor: только `port`, `port.in`, `dto` и поимённо `ModelRowRules` (правила формата для раскладки); исключение и `allowEmptyShould` убрать | частично |
| 15-05 | ОБЯЗАТЕЛЬНО | §2.4; `ADR-0021`: скобки всегда, отступ 4 и продолжение 8, LF | `config/checkstyle.xml` | Checkstyle проверяет только длину строки и табуляцию. Скобки, египетский стиль, отступ, пробелы, перевод строки и пробел в конце строки (`.editorconfig`) сборка не видит. Этап 1 отложил `NeedBraces` сюда. Код почти однороден: 7 мест с отступом (ресурсы `try` выровнены под скобку), 1 пробел в конце строки | `NeedBraces`, `LeftCurly`, `RightCurly`, `Indentation` (4/8), `WhitespaceAround`, `WhitespaceAfter`, `NoWhitespaceBefore`, `MethodParamPad`, `ParenPad`, `NewlineAtEndOfFile` (LF), запрет `\r` и пробела в конце строки; 8 мест — поправить | нет → Checkstyle |
| 15-06 | ОБЯЗАТЕЛЬНО | §2.5 (действует по `ADR-0021`): константы → поля → конструктор → методы, перегрузки рядом | 31 файл, приложение А | Порядок элементов класса не проверяется: 92 нарушения `DeclarationOrder` (конструктор до констант, поле после методов, `private` до `public`) и 25 — перегрузки врозь | `DeclarationOrder`, `OverloadMethodsDeclarationOrder`; перестановка без смысловых правок | нет → Checkstyle |
| 15-07 | РЕКОМЕНДУЕТСЯ | §2.1.7 ресурсы — `try-with-resources`; находки 03-18, 07-08, 13-07 | `config/pmd-ruleset.xml` | Три находки о незакрытых ресурсах и сравнении ссылками исправлены по месту, правила PMD `CloseResource` и `CompareObjectsWithEquals` выключены (план этапа 13 отложил их сюда). Прогон: 3 исполнителя и клиент HTTP в `*IT` закрываются вручную, одно сравнение ссылок — намеренное (`ActiveSessions`) | включить оба правила; ресурсы — `try-with-resources`, намеренное сравнение — `NOPMD` с причиной | нет → PMD |
| 15-08 | ПРОЕКТ | `CLAUDE.md`: контроллер вызывает один сценарий; находка 06-06 | `RestLayerConventionsTest` | 06-06 (контроллер импорта звал второй сценарий) исправлена, а проверки нет: план этапа 6 записал «отдельной проверки не заводили». Второй вызов порта в обработчике сборка не заметит | ArchUnit: метод контроллера вызывает не больше одного метода `..application.port.in..` | нет → ArchUnit |
| 15-09 | РЕКОМЕНДУЕТСЯ | §2.2 имена; находка 05-11 | `CodingRulesTest` | 05-11 (исключение называлось как класс JDK) исправлена переименованием, проверки нет | ArchUnit: имя класса основного кода не совпадает с классом `java.lang`, `java.util`, `java.io`, `java.time`, `java.nio.file` | нет → ArchUnit |
| 15-10 | ПРОЕКТ | одно правило — одно место; находка 02-20 | `DocumentKindRuleTest` | Литералы типов объектов представления сканируются только в путях `/interchange/`: 02-20 перевела `ViewNodeKind` modeling на константы `DiagramType`, но строку `"archimate:Group"` в modeling или `bootstrap` сборка пропустит | сканировать все модули; владельцы — `DocumentNode` и `DiagramType` | частично |
| 15-11 | РЕКОМЕНДУЕТСЯ | §5.3 метрики отражают зафиксированное; находка 09-06 | `AdvisorAdminHandlersTest` | 09-06 (датчик бюджета обновлялся внутри транзакции) исправлена переносом вызова, теста нет: фейк метрик пустой, возврат вызова внутрь `write` никто не заметит | фейк метрик записывает, была ли открыта транзакция; тест — датчик после коммита | нет → юнит-тест |

## Сверка с §3.6.1 (вариант C)

| Правило стандарта | В проекте |
|---|---|
| `domainShouldNotDependOnAnything` | модули Maven: домен не зависит от application и адаптеров — не скомпилируется |
| `domainShouldBeFrameworkFree` | enforcer — перечнем запретов; перечнем разрешений — 15-01 |
| `applicationShouldNotDependOnInfrastructure` | модули Maven |
| `applicationShouldNotUseJpa` | enforcer (`jakarta.persistence`, `org.springframework*`, `org.hibernate*`) |
| `portsShouldBeInterfaces` | 15-02 |
| `controllersShouldNotAccessRepositories` | `RestLayerConventionsTest.controllersCallUseCasesOnly` (порты, кроме `TextCatalog`, и `..adapter.persistence..`) |
| `jpaEntitiesStayInPersistenceAdapter` | 15-03 |
| `noVarargsWildcardPorts` (port.out ↛ infrastructure) | модули Maven |
| §3.6.2 (CQRS, вариант D) | не применяется: вариант C; handler'ы названы `…CommandHandler`/`…QueryHandler`, но чтение и запись не разделены портами |
| §3.6.3 `noFieldInjection` | `CodingRulesTest.constructorInjectionOnly` |

Сверх стандарта сборка держит: изоляцию контекстов (`BoundedContextIsolationTest`, `AdvisorArchitectureTest`),
входные порты и DTO сценариев (`ApplicationLayerTest`), транзакцию через `UnitOfWork` (`TransactionBoundaryTest`),
события из домена (`DomainEventOriginTest`), таблицы ролей и контракты OpenAPI против кода (`OperationTablesTest`,
`OpenApiContractTest`), метаданные настроек (`ConfigurationMetadataTest`), UUID и журнал (`UuidRulesTest`,
`LoggingRulesTest`), правила кодирования (`CodingRulesTest`, 21 правило до этапа).

## Находки этапов 1–14 без проверки в сборке

Из 164 находок «нет» 143 теперь ловит сборка — заявленные в планах проверки найдены в коде. Остальные:

**Механически ловятся — заведены этим этапом:** 02-20 (15-10), 03-18, 07-08, 13-07 (15-07), 05-11 (15-09),
06-06 (15-08), 06-14 в части порядка объявлений (15-06), 09-06 (15-11).

**Семантические — механической проверки нет, остаются ревью:** 02-16 (Javadoc расходится с кодом), 02-18 в части
имени метода, 03-17, 04-11, 06-13 (лишняя работа: проходы, хеш — проверяется только замером), 06-10, 07-05
(дублирование по смыслу: CPD видит копию текста, а не повтор правила), 08-19 (исключение не проглатывается молча —
решение по месту), 08-23 (что считать поиском), 06-14 в части `Optional`, который есть всегда.

**Механически ловятся, но не заводятся:**

- 02-18 в части `instanceof` с приведением — нужен Error Prone (`PatternMatchingInstanceof`): второй анализатор
  в компиляторе ради одного правила дороже пользы.
- 04-10 (поля агрегатов в `spec/domain/<bc>/aggregates.yaml` против кода): сверка имён полей упирается в поля,
  которых спецификация не описывает намеренно (накопитель событий базового класса, производные карты), и требует
  словаря исключений на каждый агрегат. Остаётся ревью при правке агрегата.

**Не исправлены по решению:** 01-11 (цель сложности ≤ 10 — `ADR-0021`), 08-26 в части порядка шагов
(UC-IXC-006 требует сессию `REJECTED`), 14-11 (трассировка — объявленный пробел спецификации).

**Устаревшие имена в планах исправлений** (проверка есть, имя другое): 03-13, 08-25 — `GitBindingServiceTest`
удалён вместе с сервисом на этапе 8, случаи живут в `GitHandlersTest`; 08-20 — `YamlEmitterTest` не заводился,
случай покрывает `GitYamlWriterTest`. 12-09 — `SecurityProfilesTest` выбирает конфигурацию по выражениям `@Profile`
(при `dev,prod` — настоящая цепочка), контекст с двумя профилями не поднимается: выбор решают выражения,
и проверка достаточна.

## Что проверено и в порядке

- **Плагины §4.16.2:** compiler, surefire, failsafe, JaCoCo (пороги — `tools/check-coverage.py` шагом CI), PMD,
  Checkstyle, enforcer — есть и роняют сборку. SpotBugs, OWASP `dependency-check` и правила enforcer о конфликтах
  версий (`dependencyConvergence`) — предмет этапа 17 (сканирование зависимостей и этапы CI).
- **Сложность** (§2.11): PMD `CyclomaticComplexity`, предел 25 (`ADR-0021`).
- **`.editorconfig`** есть: UTF-8, LF, отступ 4, строка 120 для Java; эталоны и фикстуры исключены.
- **D-14** (`@NullMarked`): разметки нет, владелец выбрал проверки в конструкторе (план исправлений этапа 2);
  нарушение остаётся в реестре до этапа 18.

## Приложение А. Порядок объявлений (15-06)

Файлов — 31 (основной код — 18, тесты — 13); больше всего — `InMemoryPorts` (тест, 28), `XmlSchema` (27),
`ModelRowsMapper` (8), `EntityMapper` (6), `GitFakes` (тест, 6), `DiagramType` (5), `RenderingFixtures` (тест, 5).
