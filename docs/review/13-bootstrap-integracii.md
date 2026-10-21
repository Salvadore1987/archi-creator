# Этап 13. Bootstrap: интеграции и мосты

**Дата:** 2026-10-21. **Репозиторий:** ветка `cr_phase_13`, после `5839a94` (исправления этапа 12).
**Стандарт:** коммит `97bce16`, §4.9 (HTTP-клиенты и устойчивость), §4.5 (метрики), §6.7 (HTTP Service
Clients), §6.9 (встроенная отказоустойчивость), чеклист §5.3.
**Объём:** `bootstrap/advisor/` (9 файлов), `export/` (4), `git/` (7) — 1675 строк; прочитаны целиком,
без субагентов. Точечно сверены: `AdvisorConfiguration`, `AdvisorArchitectureTest`, `AnthropicLlmClientTest`,
`FakeAnthropic`, `AdvisorSessionStartCommandHandler` (журнал отказов), `GitPublisher` и `GitAttempts`
(куда уходит текст сетевого отказа), `GitBinding`, `SecretRef`, `spec/nfr/advisor.yaml`,
`spec/nfr/interchange.yaml`, `deploy/prometheus/alerts.yaml`, исходники Anthropic Java SDK 2.71.0
(`Timeout`, `ClientOptions`, `RetryingHttpClient`).

## Находки

Пути — от `archi-bootstrap/src/main/java/uz/salvadore/hamkorbank/archi/bootstrap/`.

| № | Уровень | Правило | Где | Суть | Предложение | Ловит сборка? |
|---|---|---|---|---|---|---|
| 13-01 | ОБЯЗАТЕЛЬНО | §4.9.5, §6.9 №7; `spec/nfr/advisor.yaml` `upstream_calls.anthropic_api.circuit_breaker` | `advisor/AnthropicLlmClient.java` | Предохранителя на Anthropic API нет, хотя спецификация его задаёт (50 % отказов в окне 10 вызовов, медленный — 60 s, открыт 60 s) и объявляет алерт `UpstreamCircuitOpen` по метрике, которой нет. Пока API лежит, каждое обращение ждёт соединения и два повтора SDK и только потом получает `503`; в `deploy/prometheus/alerts.yaml` алерта нет | Resilience4j (явная версия) вокруг открытия потока и чтения: отказ API и медленный вызов — в окно, отмена клиентом — нет; открытый предохранитель — сразу `LlmUnavailableException` (`503`). Параметры — `archi.ai.circuit-breaker.*`, сверка с `spec/nfr` — в `check-nfr-config.py`; метрика `resilience4j_circuitbreaker_state` и алерт | нет → юнит-тест: после серии отказов вызов не доходит до сервера; `check-nfr-config.py` |
| 13-02 | ОБЯЗАТЕЛЬНО | §4.9.5: таймауты connect и read — из конфигурации; §6.7 №3 | `advisor/AnthropicLlmClient.java:59` | `timeout(Duration)` SDK задаёт только предел вызова целиком; таймаут соединения остаётся умолчанием SDK — минута. С двумя повторами недоступный адрес держит обращение до трёх минут до отказа | `archi.ai.connect-timeout` (10 s) и `Timeout.builder().connect(…).request(…)`; число — в `spec/nfr` и в сверке | нет → `check-nfr-config.py` |
| 13-03 | ОБЯЗАТЕЛЬНО | §4.5.3; `spec/nfr/advisor.yaml` `metrics` | `advisor/AdvisorMicrometerMetrics.java:57–76` | Датчик `advisor.budget.utilization.ratio` заводится на пару «пространство, месяц» и не снимается: с новым месяцем прошлый датчик остаётся с последним значением, и алерт `BudgetNearlyExhausted` (`> 0.8`) горит до перезапуска. `advisor.tokens.consumed` — `DistributionSummary` (`_sum`, `_count`, `_max`), а спецификация и `spec/product/advisor.md` объявляют счётчик | датчик — один на пространство: новый месяц снимает прежний; расход — `Counter` | нет → юнит-тест на `SimpleMeterRegistry` |
| 13-04 | ПРОЕКТ | `CLAUDE.md` «Помощник не изменяет модель»; правило «код, который писал не я» | `AdvisorArchitectureTest` (test, `architecture/`) | Правило моста смотрит только на `modeling.application`: зависимость от адаптера хранения modeling (`modeling.adapter..`, JPA-репозитории) его не роняет. В список чтения входит `WorkspaceApplicationService` целиком, а в нём запись — `ensureDefaultWorkspace`; вызов ловится лишь попутно, через тип команды. Javadoc теста говорит о «записи размещений», которой нет | мост к modeling видит только домен, запросы и результаты, а из входных портов вызывает перечисленные методы чтения; Javadoc — по факту | частично → `AdvisorArchitectureTest` |
| 13-05 | ПРОЕКТ | `CLAUDE.md`: расхождение спеки и кода правится в спеке; `spec/nfr` | `spec/nfr/interchange.yaml` `upstream_calls.git_remote`; `spec/nfr/advisor.yaml` `modeling_read_port`, `anthropic_api.retries` | Спецификация описывает не то, что делает код. Git: «3 повтора с 1 s и предохранитель», а публикация повторяет через очередь без предела попыток — с 5 s вдвое до 10 min (`archi.git.retry-*`), интерактивные операции отказывают сразу `503`. Порт чтения modeling — вызов в том же процессе, а спецификация даёт ему таймаут 10 s, повтор и предохранитель. Повторы Anthropic — SDK: с 0,5 s вдвое до 8 s с джиттером, а не «с 2 s» | спецификация — по факту, с обоснованием; числа Git — в сверке `check-nfr-config.py` | нет → `check-nfr-config.py` |
| 13-06 | РЕКОМЕНДУЕТСЯ | §4.9.5 №1, §6.7 №3: таймаут без бесконечности | `git/JGitRepository.java:483`, `git/GitSettings.java:35` | JGit берёт таймаут целыми секундами, `(int) timeout.toSeconds()`: значение меньше секунды даёт `0` — бесконечное ожидание. Настройка проверяется только на `@NotNull` | `@DurationMin(seconds = 1)` на `timeout` | нет → `SettingsValidationTest` |
| 13-07 | РЕКОМЕНДУЕТСЯ | §2.1.7 ресурсы — `try-with-resources` | `export/InterTypesetter.java:104–111` | `TrueTypeFont` подмножества закрывается вручную после записи: отказ `TTFSubsetter` оставляет его открытым. В `load` того же класса — `try-with-resources` | `try-with-resources` | нет; правило PMD `CloseResource` в проекте не включено — проверка вне объёма |

## Что проверено и в порядке

- **Ключ API** не попадает ни в журнал, ни в ответ: `AiSettings.toString` маскирует его, SDK без
  `ANTHROPIC_LOG` не журналирует запросы, отказы журналирует сценарий — только класс исключения
  (`AdvisorSessionStartCommandHandler`), в браузер ключ не уходит (`AdvisorSecurityIT`).
- **Повторы Anthropic** — SDK: только до первого байта ответа, на 408/409/429/5xx и обрыв соединения,
  экспоненциально с джиттером 25 % (§6.9 №2–4). После открытия потока повторов нет. Повтор не виден
  в метриках (§6.9 №8): у SDK нет точки наблюдения за попытками; повтор виден ростом
  `advisor.first.token`. Своя обёртка повторов поверх SDK ради счётчика — второй механизм повтора,
  не взят.
- **Журнал исходящих вызовов** (§4.9.3): тело запроса и ответа намеренно не журналируются — это
  модель пользователя и ответ модели (NFR-06); отказы — `WARN` с классом, исход и расход каждой
  сессии — журнал аудита и `advisor.sessions`.
- **Мост к modeling на чтение** — `ModelingContextReader`, `ModelingWorkspaceDirectory`,
  `ArchimateMetamodelCatalog`: сценарий запросов с ролью и ACL, каталог и матрица — те же объекты
  домена, что у сервера. Пробелы теста — 13-04.
- **Batik:** внешние ресурсы и `onload` выключены явно (кандидат этапа 7 закрыт); вход — SVG
  собственного писателя без `DOCTYPE`, текст экранирован, поэтому внешним сущностям XML взяться неоткуда.
  **SnakeYAML 2** по умолчанию не создаёт объектов по глобальным тегам, а читает он файл classpath.
  **fontbox** читает шрифт из памяти.
- **JGit:** таймаут задан на каждом транспорте; токен — из окружения по ссылке `env:ARCHI_GIT_…`, в URL
  его не пропускает домен (`GitBinding.carriesCredentials`), `file://` — только с
  `archi.git.allow-local-repositories`; схемы — `https`, `ssh`, `git`, `file`. Текст сетевого отказа
  (`Unreachable`) уходит в журнал попыток очереди, а пользователю — сообщение с адресом репозитория
  без секрета. Push — с ожидаемой головой, без принуждения. Блокировка — на пространство.
- **SSRF** (§6.7 №7): адрес репозитория задаёт `ADMIN`, и это обычно внутренний GitLab в частной сети —
  фильтр частных диапазонов отключил бы основной случай. Защита — роль, схемы и запрет `file://`.
- **Метрики** (§4.5.3): исходы сессий, первый токен, расход, бюджет, усечение контекста; публикация
  в Git — `gitPush`; экспорт картинок и прочие сценарии — метрика ядра сценария. Ошибки метрик бюджета — 13-03.
