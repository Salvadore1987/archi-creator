# Этап 14. Логирование и наблюдаемость сквозь код

**Дата:** 2026-10-21. **Репозиторий:** ветка `cr_phase_14`, после `455ef7c` (исправления этапа 13).
**Стандарт:** коммит `97bce16`, §4.4 (логирование), §4.5 (метрики и трассировка), §6.13 (наблюдаемость в 4.x),
чеклист §5.3.
**Объём:** точечно по `grep`: все вызовы журнала основного кода (7 файлов SLF4J, 2 файла `System.Logger`),
`application*.yaml`, фильтры корреляции, цепочки безопасности Actuator, `MicrometerUseCaseMetrics`
и метрики контекстов, `docker-compose.yml`, `Dockerfile`, шаг CI с проверкой образа, `spec/nfr/*.yaml`
(`observability`, `upstream_calls.keycloak_jwks`), исходники Spring Security 7.1.1 (`NimbusJwtDecoder`,
`JwtDecoderProviderConfigurationUtils`) и Spring Boot 4.1.1 (`JwtDecoderConfiguration`).

## Находки

Пути — от `archi-bootstrap/src/main/java/uz/salvadore/hamkorbank/archi/bootstrap/`, если не указан модуль.

| № | Уровень | Правило | Где | Суть | Предложение | Ловит сборка? |
|---|---|---|---|---|---|---|
| 14-01 | ОБЯЗАТЕЛЬНО | §4.5.2: Actuator — отдельный порт, наружу не публикуется | `application.yaml` `management`, `docker-compose.yml:98` | Actuator слушает порт приложения 8080, а состав публикует его наружу: метрики и пробы доступны с того же адреса, что интерфейс. Остался от этапа 12 | `management.server.port` (9090, `ARCHI_MANAGEMENT_PORT`) без публикации в составе; проверка здоровья в составе и CI — на этом порту; цепочка Actuator прежняя | нет → IT на настоящих портах |
| 14-02 | ОБЯЗАТЕЛЬНО | §4.5.2: внешние зависимости — в группе `readiness` | `application.yaml` `management.endpoint.health` | Группа `readiness` — умолчание Boot, только `readinessState`: без базы приложение остаётся «готовым» и получает запросы | `group.readiness.include: readinessState,db`; `liveness` — только состояние приложения (умолчание) | нет → IT по `HealthEndpointGroups` |
| 14-03 | ОБЯЗАТЕЛЬНО | §4.5.2, §6.13 №8: `build-info` генерируется сборкой | `archi-bootstrap/pom.xml` `spring-boot-maven-plugin` | Цели `build-info` нет: `/actuator/info` пуст, версию запущенного образа не узнать | цель `build-info` | нет → IT `/actuator/info` |
| 14-04 | ЗАПРЕЩЕНО | §4.4.2: `DEBUG` и `TRACE` в основном коде | `web/CommonProblemHandler.java:92,133`; `application-dev.yaml` | Неразобранный запрос и отказ после начала ответа пишутся `DEBUG` — в `prod` их не видно вовсе. Профиль `dev` поднимает пакеты приложения до `DEBUG` | `INFO`; уровень `DEBUG` из `dev` убрать | нет → ArchUnit |
| 14-05 | ОБЯЗАТЕЛЬНО | §4.4.2: в коде только `INFO` и `ERROR`, `WARN` — только в настройке чужих библиотек | `advisor/BudgetNotifications.java:21,29`, `wiring/GitConfiguration.java:148`, `GitPublisher.java:283,409`, `AdvisorSessionStartCommandHandler.java:178,402` | Семь вызовов `WARN` в коде | отказ интеграции, сбой прохода, исчерпанный бюджет — `ERROR`; порог бюджета — `INFO` | нет → ArchUnit |
| 14-06 | ПРОЕКТ | `ADR-0025`: SLF4J API; §4.4.9: плейсхолдеры, не конкатенация | `archi-interchange-application` `git/GitPublisher.java`, `archi-advisor-application` `handler/AdvisorSessionStartCommandHandler.java` | Application-слои interchange и advisor пишут через `System.Logger` (мостом JUL в Logback), а не SLF4J; в `GitPublisher` сообщение склеивается `+`, в обработчике — подстановки `MessageFormat` `{0}` | `slf4j-api` в двух модулях, плейсхолдеры `{}` | нет → ArchUnit, сканирование исходников |
| 14-07 | ОБЯЗАТЕЛЬНО | §4.4.5: входящие запросы и ответы логируются фильтром | `web/` | Журнала запросов нет: ни метода, ни пути, ни статуса, ни длительности. Отказ `4xx` без сообщения сценария в журнале не виден вовсе | фильтр после корреляции: метод, путь, статус, длительность; заголовков и тел нет — в теле модель пользователя, в заголовке токен | нет → юнит-тест фильтра |
| 14-08 | ОБЯЗАТЕЛЬНО | §4.4.9, §5.3 «Наблюдаемость»: начало и результат бизнес-операции | `wiring/MicrometerUseCaseMetrics.java` | Сценарии не пишут в журнал ни начала, ни исхода: о том, что пользователь импортировал модель или сохранил версию, журнал молчит; метрика есть | наблюдатель сценариев (одна точка для всех трёх контекстов) пишет начало и исход `INFO` с кодом отказа и длительностью; имя сценария — в MDC | нет → юнит-тест |
| 14-09 | ОБЯЗАТЕЛЬНО | §4.5.4, §6.13 №6: контекст в собственных потоках пробрасывается явно | `archi-advisor-adapter-rest` `AdvisorController.java:95`; `applicationTaskExecutor` | Поток помощника читается в `applicationTaskExecutor`, MDC туда не переходит: записи журнала потока (`advisor stream broken`, `advisor stream failed`) — без `traceId`, пользователя и модели | `TaskDecorator` с копией MDC — Boot ставит его на `applicationTaskExecutor` | нет → юнит-тест, IT исполнителя |
| 14-10 | ОБЯЗАТЕЛЬНО | §4.9.5: таймауты исходящих вызовов — из конфигурации; `spec/nfr/modeling.yaml` `keycloak_jwks.timeout: 3s` | проводка ресурс-сервера (Boot) | Ключи Keycloak (`JWKS`) и метаданные issuer'а читаются с умолчаниями Spring Security — 30 s на соединение и 30 s на чтение, под блокировкой: зависший Keycloak держит запросы минуту | `JwkSetUriJwtDecoderBuilderCustomizer` с `archi.security.jwks-timeout` (3s); число — в сверке `check-nfr-config.py` | нет → юнит-тест, `check-nfr-config.py` |
| 14-11 | ПРОЕКТ | `CLAUDE.md`: расхождение спеки и кода правится в спеке; `spec/nfr` | `spec/nfr/*.yaml` `observability.tracing`, `modeling.yaml` `upstream_calls.keycloak_jwks`; `application.yaml` `management.tracing` | Спецификация обещает трассировку (`parent_based_traceidratio`, доли 0,1 и 1,0, W3C), а библиотеки трассировки нет: `traceId` — из `traceparent` своим фильтром, спанов нет, `management.tracing.sampling.probability` ничего не настраивает. У `keycloak_jwks` — два повтора и предохранитель, которых у декодера нет; «кэш до ротации ключей» — кэш Nimbus на 5 минут и перечитывание на незнакомый `kid` | спецификация — по факту, трассировка — объявленный пробел; мёртвую настройку убрать | нет → `check-nfr-config.py` |
| 14-12 | кандидат в реестр | §4.5.3: высококардинальные значения в тегах запрещены | `wiring/ModelingMicrometerMetrics.java`, `wiring/InterchangeMicrometerMetrics.java`; `spec/nfr/modeling.yaml`, `interchange.yaml` `metrics` | Тег `model_id` у `modeling.model.elements`, `modeling.lock.wait`, `modeling.validation.findings`, `interchange.opaque.objects` — идентификатор сущности, но так требует спецификация: ряд на модель нужен для разбора. Моделей — десятки, не миллионы | решение ADR: отступление принято, с пределом и условием пересмотра | — |

## Что проверено и в порядке

- **Формат** — `ADR-0025`: Logback, в `prod` — ECS в stdout, MDC выходит полями; файловых appender'ов нет.
- **Корреляция** (§4.4.7): `traceId` из W3C `traceparent` или новый, `modelId` из пути, `user` — субъект
  токена, не имя и не почта; каждый ключ снимается в `finally`. `401` и `403` несут тот же `traceId`
  (фильтр стоит до цепочки безопасности).
- **Маскирование** (§4.4.4): фильтра маскирования нет, и он не нужен — основной способ стандарта соблюдён:
  тела, заголовки, промпты и ответы модели не журналируются (`log_prompt_content: false`), DTO целиком
  через `toString()` не пишутся, ключ Anthropic маскирует `AiSettings.toString`, токен Git в URL
  не пропускает домен. `pii_fields: []` во всех трёх контекстах.
- **«Log and throw»** (§4.4.9) — нет: журналируют обработчики отказов (`CommonProblemHandler`,
  `InterchangeProblemHandler`), фоновые границы (проход очереди Git, поток помощника); сценарии
  отказ бросают, не журналируя. `ERROR` с трассой стека — у неожиданных отказов и дефекта выгрузки.
- **Actuator** (§4.5.2): список эндпоинтов закрытый (`health,info,metrics,prometheus`), `env`, `heapdump`,
  `threaddump`, `shutdown` не выставлены; `/info` окружения не раскрывает (умолчание Boot 4);
  `liveness` — только состояние приложения; подробности здоровья — авторизованным; метрики — `ADMIN`.
- **Бизнес-метрики** (§4.5.3): запросы, длительность и отказы каждого сценария трёх контекстов,
  импорт, потери выгрузки, публикация в Git, сессии, первый токен и расход помощника, предохранитель.
