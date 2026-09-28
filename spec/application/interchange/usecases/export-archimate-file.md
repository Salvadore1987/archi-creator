---
version: 1.0
last_modified: 2026-09-28
bounded_context: interchange
use_case: UC-IXC-002
requirements: [FR-02, FR-03, FR-21, NFR-05]
invariants: [INV-IXC-004, INV-IXC-005, INV-IXC-008]
stages: ["1", "2"]
---

# UC-IXC-002 — Экспорт модели в `.archimate`

**Актор:** `VIEWER` и выше · **Триггер:** запрос выгрузки модели ·
**Результат:** файл `.archimate`, семантически идентичный исходному, если модель
не менялась

Это критический контур проекта: цикл «открыть → ничего не менять → сохранить»
обязан давать тот же XML (NFR-05). Регрессия блокирует сборку.

## Предусловия

- Модель существует и не в состоянии `PURGED`.
- Указана версия либо берётся последняя зафиксированная (`INV-IXC-008`).

## Основной сценарий

**Given** модель, импортированная из `.archimate` и не изменявшаяся

**When** актор запрашивает экспорт в `.archimate`

**Then**
1. `ExportJob` фиксирует `sourceVersionNo`: выгружается зафиксированная версия,
   а не текущее состояние (`INV-IXC-008`).
2. Писатель обходит дерево в порядке читателя, `sort_order` в пределах родителя
   определяет позицию узла.
3. `archiId` выводятся буквально, без перегенерации.
4. Узлы с `supported = false` печатаются из `raw_xml` без изменений (FR-03).
5. `targetConnections` восстанавливаются из `sourceConnection`; порядок
   идентификаторов в атрибуте сохраняется как был.
6. Собственные `fillColor` и `font` объектов выводятся как есть (FR-21).
7. `bounds` пишутся относительно родителя, у детей `Group` — относительно группы.
8. Повторный вызов даёт побайтово тот же результат (`INV-IXC-004`).
9. Результат отдаётся как `Artifact`, публикуется `ExportCompleted`.

## Альтернативы и отказы

| Ситуация | Ответ | Поведение |
|---|---|---|
| Модель удалена (`DELETED`) | `409` | Экспорт удалённой модели запрещён, код `MODEL_DELETED` |
| Запрошена очищенная версия без Git | `410` | Снимок удалён по ретеншену, восстанавливать неоткуда (FR-47) |
| Запрошена очищенная версия при настроенном Git | `200` | Содержимое восстанавливается по `git_sha` прозрачно (FR-46) |
| Модель правится в момент выгрузки | `200` | Отдаётся зафиксированная версия, правки в файл не попадают |

## Порты

**Входящий:** `ExportModel(modelId, format = ARCHIMATE, versionNo?)` → `Artifact`

**Исходящие:**
- `ModelDocumentAssembler` — сборка `ModelDocument` из данных `modeling`
- `ArchiDocumentWriter` — детерминированная запись XML
- `VersionSnapshotReader` — снимок версии, при отсутствии — восстановление из Git

## События

`ExportCompleted` — [`../../../domain/interchange/events.yaml`](../../../domain/interchange/events.yaml)

## Тесты

| Проверка | Тест |
|---|---|
| Round-trip референсной модели | `RoundTripGoldenFileTest#hamkorbankAsIsSurvivesRoundTrip` |
| Детерминированность писателя | `ArchiWriterTest#writingTwiceProducesIdenticalBytes` |
| Сохранность неизвестного | `RoundTripGoldenFileTest#opaqueNodesSurvive` |
| Фиксация версии | `ExportJobTest#exportPinsSourceVersion` |
