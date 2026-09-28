---
version: 0.1
last_modified: 2026-09-22
bounded_context: interchange
---

# Product spec: Interchange

## Зачем существует

`interchange` — граница системы. Всё, что приходит файлом и уходит файлом,
проходит через него: нативный формат Archi, Open Exchange Format, CSV-каталог,
картинки представлений, выгрузка в Git.

Ценность BC — в обещании, ради которого продукт вообще внедряем: модель,
открытая в Archi Creator и сохранённая обратно, остаётся тем же
`.archimate`-файлом. Это снимает vendor lock-in и позволяет работать рядом с
существующим Archi, а не вместо него. Обещание держится на одном инварианте —
lossless round-trip (INV-IXC-005, NFR-05), и весь остальной BC существует,
чтобы этот инвариант не сломался.

## In scope

- Чтение `.archimate` (`version="5.0.0"`) с сохранением папок, вложенности,
  идентификаторов, свойств, документации, порядка (FR-01).
- Запись `.archimate` с lossless round-trip (FR-02) — критический контур,
  проверяемый golden-file тестами на каждом коммите (NFR-05).
- Сохранение неподдержанных узлов и атрибутов как непрозрачных фрагментов
  и их дословная отдача при экспорте (FR-03).
- Односторонняя выгрузка в Open Exchange Format с проверкой по XSD и
  обязательным отчётом о потерях (FR-43, FR-44).
- Экспорт каталога элементов в CSV (FR-45).
- Серверный рендер представлений в SVG и PNG, совпадающий с канвой
  (FR-41, FR-42).
- Выгрузка модели в Git разложенным YAML, импорт состояния по ссылке,
  слияние по сущностям (этапы 7a–7c).
- Отчёт импорта и режим строгого импорта рабочего пространства
  (FR-49, FR-50).

## Out of scope

- Смысл модели. `interchange` знает, что у объекта есть тип и порядок, но
  не знает, допустима ли связь по матрице ArchiMate: валидация метамодели —
  BC `modeling`.
- Хранение. Результат разбора отдаётся как `ModelDocument`; что с ним
  произойдёт дальше, решает `modeling`.
- **Импорт из OEF и из CSV.** Единственный принимаемый формат — `.archimate`
  ([`docs/spec/backend.md` §3.5](../../docs/spec/backend.md#35-экспорт-в-open-exchange-format),
  [§5.2](../../docs/spec/backend.md#52-экспорт-каталога-в-csv)). Это решение,
  а не пробел: приём второго диалекта требует разбора чужих вольностей и
  собственного набора golden-file тестов, а сценария «нам присылают модели
  не в Archi» нет.
- **PDF.** Остаётся печать из браузера (§13.4).
- Git как источник правды. Репозиторий — журнал и ревью; база остаётся
  источником правды ([`docs/spec/backend.md` §11.1](../../docs/spec/backend.md#111-роль-git-журнал-и-ревью-а-не-распределённое-редактирование)).

## Метрики успеха

| KPI | Целевое значение | Источник |
|---|---|---|
| Round-trip референсной модели | 100 % коммитов зелёные; регрессия блокирует сборку (NFR-05) | Golden-file тесты в CI на `docs/Hamkorbank_AS_IS_strict.archimate` |
| Импорт файла на 5 000 строк XML | ≤ 3 с на сервере (NFR-02) | `interchange_usecase_duration_seconds{usecase="ImportArchimateFile"}` |
| Воспроизводимость выгрузки в Git | Повторный экспорт без правок даёт побайтово тот же результат — 100 % (INV-IXC-004) | Тест повторной выгрузки в CI |
| Молчаливые потери при выгрузке | 0: каждая выгрузка в формат с потерями сопровождается отчётом (INV-IXC-006) | `interchange_export_loss_entries_total`, отсутствие отчёта — ошибка сборки |
| Доля импортов, отклонённых из-за повреждённых данных | ≤ 1 % от всех импортов; каждый отказ объясним находкой в отчёте | `interchange_import_sessions_total{outcome="REJECTED"}` |

## Стейкхолдеры

- **Product owner:** архитектурная практика Hamkorbank (персона — TBD)
- **Architect:** TBD
- **Tech lead:** TBD
- **Downstream consumers:** BC `modeling` (приём `ModelDocument`),
  пользователи десктопного Archi, получатели выгрузок (аудит, регулятор,
  подрядчики), ревьюеры merge request'ов в Git

## Ссылки

- Доменная модель: `domain/interchange/aggregates.yaml`
- Глоссарий: `domain/interchange/ubiquitous-language.md`
- Инварианты: `domain/interchange/invariants.md`
- События: `domain/interchange/events.yaml`
- Use cases: `application/interchange/usecases/` (пусто — заводятся режимом add-usecase)
- API: `contracts/interchange/rest-api.openapi.yaml`
- События (контракт): `contracts/interchange/events.asyncapi.yaml`
- NFR: `nfr/interchange.yaml`
- Форма `ModelDocument` повторяет `domain/modeling/aggregates.yaml#ArchitectureModel`
- Прозаическая спецификация: [`docs/spec/backend.md` §3.4](../../docs/spec/backend.md#34-кодек-archimate--ключевые-решения),
  [§11](../../docs/spec/backend.md#11-git-интеграция-и-формат-хранения)
