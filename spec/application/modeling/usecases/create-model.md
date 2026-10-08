---
version: 1.0
last_modified: 2026-09-28
bounded_context: modeling
use_case: UC-MDL-001
requirements: [FR-04, FR-28]
invariants: [INV-MDL-002, INV-MDL-003, INV-MDL-009]
stages: ["2"]
---

# UC-MDL-001 — Создать, переименовать и удалить модель

**Актор:** `ARCHITECT` (создание, переименование), `ADMIN` (физическое уничтожение) ·
**Результат:** модель в состоянии `ACTIVE` с готовым деревом папок

## Предусловия

- Рабочее пространство существует, актор имеет к нему доступ (FR-28, FR-29).

## Основной сценарий: создание

**Given** рабочее пространство и имя новой модели

**When** актор создаёт пустую модель с ключом идемпотентности

**Then**
1. Создаётся `ArchitectureModel` в состоянии `ACTIVE` с `UUIDv7` как внутренним
   ключом и `archi_id` формата `id-<32 hex>` ([`ADR-0002`](../../../adr/0002-dual-identity.md)).
2. Заводится дерево папок с восемью системными корнями: `Strategy`, `Business`,
   `Application`, `Technology`, `Motivation`, `Other`, `Relations`, `Views`
   (`INV-MDL-009`). Они создаются сразу, а не по первому элементу: файл Archi
   без них невалиден.
3. Повтор с тем же ключом идемпотентности возвращает ту же модель и не создаёт
   вторую (`INV-MDL-003`).

## Основной сценарий: удаление

**Given** модель в состоянии `ACTIVE`

**When** актор удаляет модель

**Then** модель переходит в `DELETED` (soft delete) и исчезает из списка,
но данные остаются. `DELETED → ACTIVE` восстанавливает её.
`ACTIVE → PURGED` запрещён: физическое уничтожение возможно только
из `DELETED` и только `ADMIN` (`INV-MDL-002`).

## Альтернативы и отказы

| Ситуация | Ответ | Поведение |
|---|---|---|
| Имя пустое или длиннее 200 символов | `422` | Отказ до создания |
| Команда изменения содержимого в состоянии `DELETED` | `409` | Отклоняется: правки порождали бы версии, которых пользователь не увидит |
| `PURGE` активной модели | `422` | Запрещён переход, нужен промежуточный `DELETED` |
| Попытка `PURGE` ролью `ARCHITECT` | `403` | Только `ADMIN` |

## Порты

**Входящие:** `CreateModel(workspaceId, name, idempotencyKey)`,
`RenameModel(modelId, name)`, `DeleteModel(modelId)`, `PurgeModel(modelId)`

**Исходящие:** `ModelRepository`, `FolderTreeInitializer`

## События

`ModelDeleted` — [`../../../domain/modeling/events.yaml`](../../../domain/modeling/events.yaml)

## Тесты

| Проверка | Тест |
|---|---|
| Переходы состояний | `ArchitectureModelStateMachineTest#onlyAllowedTransitionsArePermitted` |
| Восемь корней при создании | `FolderTreeTest#eightRootFoldersAlwaysExist` |
| Идемпотентность | `IdempotencyIT#sameKeyDifferentBodyReturns409` |
