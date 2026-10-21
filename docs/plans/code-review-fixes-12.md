# Исправления по этапу 12 ревью — bootstrap: проводка, конфигурация, безопасность

**Основание:** находки [`docs/review/12-bootstrap-security.md`](../review/12-bootstrap-security.md);
порядок работы — как в планах этапов 1–11 (владелец 2026-10-21: «если есть изменения — сразу исправь»).

Каждая задача — проверка в сборке или тест, прогон на старом коде (обязан упасть),
перенос всех мест, `./mvnw -P '!frontend' install` зелёный, один коммит.

## Порядок

Сначала роли свойством (1): уходит вызов бина из бина, на котором держится прокси конфигурации, —
после этого `proxyBeanMethods = false` (2) ничего не ломает. Затем безопасность по убыванию риска:
профили (3), цепочки и закрытость по умолчанию (4), `traceId` до цепочки (5), контроллер сессии (6).
Процессор метаданных (7) — до валидации настроек (8): тест валидации опирается на метаданные ключей.
Пояс очистки (9) добавляет настройку и идёт после (8). Мелкое — `scanBasePackages` (10), ADR (11).

## Задачи

| № | Задача | Находки | Проверка | Статус |
|---|---|---|---|---|
| 1 | Роли из `realm_access.roles` — свойством `authorities-claim-expressions` и префиксом `ROLE_`; конвертер и его бин удалить | 12-07 | `KeycloakIT` (роли из токена Keycloak); `CodingRulesTest`: в основном коде нет `Converter<Jwt, …>` | ✅ |
| 2 | `@Configuration(proxyBeanMethods = false)` у всех конфигураций | 12-05 | `CodingRulesTest#configurationsDoNotProxyBeanMethods` | ✅ |
| 3 | Заглушка — `dev & !prod`, настоящая цепочка — `!dev \| prod` | 12-09 | `SecurityProfilesTest`: при `dev,prod` — настоящая цепочка, при `dev` — заглушка, без профиля — настоящая | ✅ |
| 4 | Три цепочки: Actuator (`EndpointRequest`), API, UI; открытое — списком, остальное — отказ | 12-01, 12-02 | `PublicSurfaceIT`: статика, `/ui-config`, пробы — открыты; адрес вне списка и Actuator без токена — не `200` | ✅ |
| 5 | `traceId` и `modelId` — фильтром до цепочки безопасности, субъект — после | 12-03 | `PublicSurfaceIT`: `401` несёт `X-Trace-Id` | ✅ |
| 6 | Контроллер сессии: перевод — `SessionDtoMapper`, роли и субъект — из `CurrentEditorResolver`, `401` — цепочка; `@EnableConfigurationProperties(UiSettings)` — в конфигурацию | 12-08 | `RestLayerConventionsTest#controllersDoNotBuildDtos` охватывает DTO bootstrap; `#controllersDoNotConstructExceptions` | ✅ |
| 7 | `spring-boot-configuration-processor` в `annotationProcessorPaths` модуля `archi-bootstrap` | 12-06 | `ConfigurationMetadataTest`: метаданные есть у каждого префикса `@ConfigurationProperties` | ✅ |
| 8 | `@Validated` и ограничения на всех шести настройках; умолчания `InterchangeSettings`; одно умолчание каталога кэша Git | 12-04 | `SettingsValidationTest`: недопустимые значения роняют контекст; `CodingRulesTest`: `@ConfigurationProperties` — с `@Validated` | ✅ |
| 9 | Пояс очистки снимков и её расписания — `archi.modeling.retention-zone` | 12-10 | `CodingRulesTest`: нет `ZoneId.systemDefault()` и `TimeZone.getDefault()` в основном коде | ✅ |
| 10 | `scanBasePackages` у класса запуска | 12-11 | `CodingRulesTest`: класс запуска называет корень сканирования | ✅ |
| 11 | ADR: роль — таблицей операций в ядре сценария, `@PreAuthorize` не вводится; D-21 | 12-12 | `tools/check-links.py`, `tools/check-traceability.py` | ✅ |

## Расхождения плана с фактом

- **Задача 1.** Закрытость набора ролей переехала из конвертера в разбор автора команды
  (`CurrentEditorResolver.roles`): в контекст безопасности теперь попадают и `ROLE_offline_access`,
  `ROLE_default-roles-archi`, но ни одно правило по ним не решает — сценарии видят только
  `VIEWER`, `ARCHITECT`, `ADMIN`, а цепочка проверяет лишь `ADMIN` у Actuator. Тест конвертера заменён
  `RealmRolesTest`: тот же разбор, что собирает Boot, по выражению и префиксу из `application.yaml`.
  Отсутствие `realm_access` даёт пустой набор: ошибка выражения SpEL Spring Security глушит сам.
  На старом коде правило `noHandWrittenJwtConverters` падало на `KeycloakRealmRolesConverter`.
- **Задача 2.** Вызов метода бина из другого был один — `jwtAuthenticationConverter()` в `SecurityConfig`,
  и он ушёл задачей 1; остальные шесть конфигураций получали зависимости параметрами и прежде.
  На старом коде правило падало семь раз — на каждой конфигурации.
- **Задача 3.** Тест проверяет выбор цепочки по выражениям `@Profile`, а не старт приложения: что при
  `dev,prod` настоящая цепочка не поднимется без ресурс-сервера (его автоконфигурацию выключает
  `application-dev.yaml`), следует из устройства Spring Security и тестом не закреплено — для этого
  пришлось бы поднимать контекст с базой ради одного отказа. На старом коде два случая из пяти
  (`dev,prod` и `prod,dev`) включали заглушку.
- **Задача 4.** Проверка — не «не `200`», а отказ доступа: на старом коде адрес вне `/api` без токена
  отвечал `404` (обработчика нет), и «не `200`» прошло бы и там. Теперь — `403` цепочки UI. Открыта
  `/error`: на неё контейнер переадресует отказ, и без неё ответ об ошибке сам упирался бы в отказ доступа.
  Статика SPA — ровно то, что кладёт сборка Vite: `index.html` и `assets/`; других путей у собранного
  интерфейса нет. Actuator адресуется `EndpointRequest` по идентификаторам (`health`, `info`), а не путём.
- **Задача 5.** Фильтр разделён на два: `RequestCorrelationFilter` (`traceId`, `modelId`) стоит
  на 10 раньше цепочки безопасности (`SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10`),
  `RequestUserFilter` (субъект) — на прежнем месте после неё. Проверены `401` API по `traceparent`
  и `403` Actuator. На старом коде `401` уходил без `X-Trace-Id`.
- **Задача 6.** Кроме охвата DTO bootstrap заведено правило «контроллер не бросает исключений сам»
  (`controllersDoNotConstructExceptions`): старый `/me` строил `InsufficientAuthenticationException`.
  Исключения JDK из правила выведены — `UncheckedIOException` вокруг чтения загруженного файла
  в `InterchangeController` и `MatchException`, которую компилятор ставит в `switch` по исходу импорта.
  Роли в `/me` идут в порядке перечисления `Role` (VIEWER, ARCHITECT, ADMIN), как и раньше: набор
  в `EditorIdentity` неупорядочен, поэтому маппер сортирует. Своя проверка «вошёл ли» у `/me` ушла:
  цепочка API не пускает к нему без токена, `SessionIT` это проверяет. На старом коде правила падали
  трижды на DTO и один раз на исключении `SessionController`.
- **Задача 7.** Файл метаданных с тем же именем лежит и в jar'ах Spring Boot, поэтому тест собирает
  группы со всех файлов classpath, а не с первого найденного: первый вариант на старом коде нашёл
  чужой файл и упал не на отсутствии файла, а на группе. Процессор подключён только в `archi-bootstrap` —
  все шесть классов настроек живут там. На старом коде групп `archi.*` не было ни одной.
- **Задача 8.** Проверка нашла настоящий случай в тестах: интеграционные тесты задавали пустой
  issuer, и адрес входа, выведенный из него, был пуст при включённом входе — фронтенд получил бы
  пустой `authority`. Тестам адрес задан явно (`IntegrationTest`). Тексты нарушений — сообщения
  Bean Validation по умолчанию: они уходят в журнал старта, а не пользователю. Обязательность адреса входа —
  `@AssertTrue` на методе записи `Oidc`. Умолчание каталога кэша Git осталось одно — в `application.yaml`;
  без него приложение не стартует. Умолчания `InterchangeSettings` повторяют `application.yaml`: так класс
  без YAML (тест, другой профиль) получает рабочие пределы, а не нули. На старом коде падали все
  12 случаев `SettingsValidationTest` и правило `settingsAreValidated`.
- **Задача 9.** Пояс — отдельная настройка `archi.modeling.retention-zone` (`ARCHI_RETENTION_ZONE`,
  по умолчанию `Asia/Tashkent`), а не общий с поясом бюджета помощника: это разные решения установки,
  хоть сейчас и совпадают. Расписание идёт `CronTrigger` с тем же поясом. Правило ловит явный вызов пояса
  JVM (`ZoneId.systemDefault`, `TimeZone.getDefault`, `Clock.systemDefaultZone`), но не неявный —
  `addCronTask(…, String)` без пояса берёт пояс JVM внутри Spring, и ArchUnit этого не видит.
  На старом коде правило падало на `ModelingConfiguration.snapshotRetentionApplicationService`.
