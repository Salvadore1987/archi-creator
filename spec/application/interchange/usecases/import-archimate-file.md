---
version: 1.0
last_modified: 2026-09-28
bounded_context: interchange
use_case: UC-IXC-001
requirements: [FR-01, FR-03, FR-49, FR-50, NFR-02]
invariants: [INV-IXC-001, INV-IXC-002, INV-IXC-003, INV-IXC-007]
stages: ["1", "2"]
---

# UC-IXC-001 — Импорт файла `.archimate`

**Актор:** `ARCHITECT` · **Триггер:** загрузка файла в рабочее пространство ·
**Результат:** новая модель в состоянии `ACTIVE` и отчёт об импорте

## Предусловия

- Пользователь аутентифицирован и имеет роль `ARCHITECT` или `ADMIN` (FR-28).
- Файл читается как XML с корнем `archimate:model`.
- Известно значение `strict_import` рабочего пространства (FR-49).

## Основной сценарий

**Given** файл `.archimate` версии `5.0.0` и рабочее пространство
с `strict_import = false`

**When** актор подаёт файл на импорт с ключом идемпотентности

**Then**
1. Создаётся `ImportSession` в состоянии `RECEIVED`; повторная подача того же
   ключа возвращает существующую сессию и не создаёт вторую модель
   (`INV-IXC-003`).
2. Читатель проходит документ потоково, сохраняя для каждого узла `archiId`,
   тип, порядок в пределах родителя и — для неизвестных узлов и атрибутов —
   дословный `raw_xml` с `supported = false` (`INV-IXC-001`, FR-03).
3. Собирается отчёт: нарушения матрицы связей как `ImportFinding` уровня
   `WARNING`, неизвестные типы как `IXC_UNKNOWN_ELEMENT_TYPE`.
4. Сессия переходит `RECEIVED → VALIDATED`; применить можно только проверенное
   (`INV-IXC-002`).
5. `ApplyImport` создаёт модель одной транзакцией: дерево папок с девятью
   корнями, элементы, связи, представления, `sort_order` с шагом 1000.
6. Сессия переходит в `APPLIED`, публикуется `ImportApplied`.
7. Отчёт доступен актору; находки попадают в фильтр «Замечания» дерева (UI-012).

## Альтернативы и отказы

| Ситуация | Ответ | Поведение |
|---|---|---|
| Невалидный XML | `400` | Отказ всегда, с номером строки и позицией (`INV-IXC-007`, FR-50) |
| Ссылка на несуществующий элемент, дубль `archiId`, связь без конца | `400` | Отказ всегда, независимо от `strict_import` (FR-50) |
| Нарушения матрицы, `strict_import = false` | `200` + отчёт | Модель создаётся, находки в отчёте (FR-49) |
| Нарушения матрицы, `strict_import = true` | `422` | Отказ целиком со списком нарушений, модель не создаётся |
| Повтор с тем же ключом идемпотентности | `200` | Возвращается результат первой попытки (`INV-IXC-003`) |
| Файл больше предела рабочего пространства | `413` | Отказ до чтения тела |

## Порты

**Входящий:** `ImportArchimateFile(workspaceId, fileName, bytes, idempotencyKey)`
→ `ImportReport`

**Исходящие:**
- `ArchiDocumentReader` — потоковое чтение XML в `ModelDocument`
- `RelationMatrix` — проверка допустимости связи (читается из `modeling`)
- `ModelWriter` — применение документа как новой модели (команда в `modeling`)
- `ImportSessionRepository` — хранение сессии и находок

## События

`ImportApplied` —
[`../../../domain/interchange/events.yaml`](../../../domain/interchange/events.yaml)

## Нефункциональное

Импорт файла на 5 000 строк XML — ≤ 3 с на сервере (NFR-02). Чтение потоковое
(StAX): держать документ целиком в памяти нельзя, референсная модель — 4 437 строк
и растёт.

## Тесты

| Проверка | Тест |
|---|---|
| Round-trip после импорта | `RoundTripGoldenFileTest#hamkorbankAsIsSurvivesRoundTrip` |
| Идемпотентность | `ImportIT#retryDoesNotCreateSecondModel` |
| Строгий режим | `ImportIT#corruptedFileIsRejectedInBothModes` |
| Порядок и папки | `ModelImportIT#folderTreeSurvivesRoundTrip` |
