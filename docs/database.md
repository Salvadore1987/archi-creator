# Archi Creator — хранение данных

> Часть спецификации [`archi-creator.md`](archi-creator.md): сущности PostgreSQL,
> индексы и ограничения, стратегия хранения, ретеншен версий и требования к схеме,
> которые нужно заложить уже на этапе 1.
>
> Нумерация разделов сквозная по всему комплекту — раздел сохраняет тот номер, под которым
> на него ссылаются: `§4.4`, `§11.6`. Требования `FR-xx`/`NFR-xx` живут в
> [§2 главного файла](archi-creator.md#2-требования).
>
> Документ живой: решения, которые изменятся по ходу работы, правятся здесь, а не в переписке.

**Содержание:** §4.1 сущности · §4.2 индексы и ограничения · §4.3 стратегия хранения ·
§4.4 хранение и очистка версий · §11.6 что нужно сделать уже на этапе 1.

**Рядом:** [бэкенд](backend.md) · [фронтенд](frontend.md) · [главный файл](archi-creator.md)

СУБД — PostgreSQL 16, миграции — Flyway ([§3.2](archi-creator.md#32-стек)).

---

## 4. Модель данных

### 4.1 Сущности

```
Workspace 1──* Model 1──* ModelFolder ──* ModelFolder (вложенность)
                 │
                 ├──* Element        (узлы модели)
                 ├──* Relationship   (связи модели)
                 ├──* View           (представления)
                 │      └──* ViewNode ──* ViewNode (вложенность)
                 │      └──* ViewEdge
                 ├──* ModelVersion   (история)
                 └──0..1 ModelLock    (блокировка редактирования)
```

| Сущность | Поля |
|----------|------|
| `Workspace` | `id`, `name`, `ai_enabled`, `ai_monthly_token_limit` ([§7.5](backend.md#75-лимиты-расходов)), `strict_import` (по умолчанию `false`, [§8.3](backend.md#83-строгость-импорта)), `created_at`, `git_repo_url`, `git_branch`, `git_token_ref` (все nullable, §11.6) |
| `Model` | `id`, `workspace_id`, `archi_id` (id корня из файла), `name`, `documentation`, `archi_version` (`5.0.0`), `status` (`ACTIVE`/`DELETED`; `PURGED` строки не оставляет), `raw_xml`, `created_by`, `created_at`, `updated_at`, `version` |
| `ModelFolder` | `id`, `model_id`, `parent_id`, `archi_id`, `name`, `folder_type` (`strategy`…`diagrams`, null у вложенных), `sort_order`, `raw_xml` |
| `Element` | `id`, `model_id`, `folder_id`, `archi_id`, `archi_type` (`archimate:ApplicationComponent`), `layer`, `name`, `documentation`, `sort_order`, `supported` (false → opaque), `raw_xml` |
| `Relationship` | `id`, `model_id`, `folder_id`, `archi_id`, `archi_type`, `source_element_id` \| `source_relationship_id`, `target_element_id` \| `target_relationship_id` (конец — элемент или связь, ровно одно из пары), `name`, `documentation`, `access_type`, `directed`, `sort_order`, `supported`, `raw_xml` |
| `View` | `id`, `model_id`, `folder_id`, `archi_id`, `archi_type`, `name`, `documentation`, `sort_order`, `viewpoint`, `raw_xml` |
| `ViewNode` | `id`, `model_id`, `view_id`, `parent_id`, `archi_id`, `archi_type`, `kind` (`DIAGRAM_OBJECT`/`GROUP`/`NOTE`/`OTHER`), `element_id` (null для групп/заметок), `x`, `y`, `width`, `height`, `fill_color`, `font`, `font_color`, `line_color`, `text_alignment`, `label` (атрибут `name` группы, объектов скетча и холста), `content` (текст заметки), `sort_order`, `raw_xml` |
| `ViewEdge` | `id`, `model_id`, `view_id`, `archi_id`, `archi_type`, `relationship_id`, `source_node_id` \| `source_edge_id`, `target_node_id` \| `target_edge_id`, `bendpoints` (jsonb), `fill_color`, `font`, `font_color`, `line_color`, `text_alignment`, `sort_order` (позиция в содержимом источника), `raw_xml` |
| `ModelProperty` / `ElementProperty` / `RelationshipProperty` / `ViewProperty` | `owner_id`, `sort_order`, `key`, `value` |
| `ModelVersion` | `id`, `model_id`, `version_no`, `author`, `comment`, `label` (метка релиза, nullable), `created_at`, `snapshot` (сжатый `.archimate`, **nullable** — может быть очищен, §4.4), `content_hash` (SHA-256 несжатого снимка), `git_sha` (nullable, §11.6) |
| `ModelLock` | `model_id` (PK), `owner`, `acquired_at`, `expires_at` |
| `ModelAccessEntry` | `model_id`, `principal_type` (`USER`/`GROUP`), `principal`, `access` (`READ`/`WRITE`) — FR-29, `INV-MDL-011` |
| `IdempotencyRecord` | `scope`, `actor`, `idem_key`, `fingerprint`, `result_ref`, `created_at` — `INV-MDL-003` |
| `ImportSession` / `ImportFinding` | сессия импорта и её находки (`INV-IXC-002`, `INV-IXC-003`) |
| `AiAuditLog` | `id`, `workspace_id`, `model_id`, `user`, `action`, `prompt_tokens`, `completion_tokens`, `created_at` |

### 4.2 Индексы и ограничения

- `UNIQUE (model_id, archi_id)` на `Element`, `Relationship`, `View`, `ViewNode`, `ViewEdge`, `ModelFolder` — гарантия уникальности идентификаторов внутри модели.
- `INDEX (model_id, archi_type)` — выборка по типу для палитры и валидации.
- `INDEX (view_id, parent_id, sort_order)` — сборка дерева представления одним запросом.
- Концы связи — `ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED`: удаление элемента требует явного удаления его связей (как в Archi). По смыслу это `RESTRICT`, но проверка в конце транзакции: импорт пишет тысячи строк одной транзакцией, а связь может ссылаться на связь, вставленную позже. Отказ при удалении даёт домен раньше базы (`INV-MDL-004`), база — страховка.
- `raw_xml` — остаток XML объекта ([`ADR-0017`](../spec/adr/0017-xml-residue.md)): всё, что не легло в столбцы, с позициями. Столбцы сильнее остатка.
- Снимок `ModelVersion.snapshot` — `bytea`, gzip.
- `sort_order` — **разреженный, шаг 1000** (§11.6): вставка элемента не перенумеровывает
  соседей, иначе каждая вставка давала бы diff на всю папку при выгрузке в Git.
  Перебалансировка ветви — только когда зазор исчерпан.

### 4.3 Стратегия хранения

PostgreSQL — источник правды. Открытие модели: один запрос на дерево модели (элементы,
связи, папки) и по запросу — payload представления. Файл `.archimate` — результат
сериализации, а не способ хранения.

**Этап 7a (Git):** выгрузка модели в Git-репозиторий при сохранении (`commit`),
импорт состояния по ссылке и слияние по сущностям при расхождении. Полностью описано
в [§11](backend.md#11-git-интеграция-и-формат-хранения); база остаётся источником правды, репозиторий — производная величина.

### 4.4 Хранение и очистка версий

Запись о версии — около 200 байт: кто, когда, комментарий, метка, `git_sha`.
Снимок модели — около 150 КБ в gzip на вашей модели. При десяти сохранениях в день
это примерно 500 МБ в год на модель: для PostgreSQL немного, но растёт линейно и вечно,
а содержимое к тому же дублирует репозиторий.

Поэтому **записи хранятся бессрочно, снимки — по правилу**:

| Снимок сохраняется | Почему |
|--------------------|--------|
| За последние 30 дней | Рабочий горизонт: откат «что я вчера сломал» должен быть мгновенным |
| У всех помеченных версий (`label`) | Релизы архитектуры — AS-IS на дату аудита, утверждённый TO-BE |
| Последний за каждый календарный месяц | Разумная сетка для «покажи, как выглядел ландшафт в марте» |

Остальные снимки обнуляются фоновой задачей. История при этом **не теряется**: запись
о версии остаётся, а содержимое восстанавливается из Git по `git_sha`. Для пользователя
разницы нет — просмотр старой версии просто отрабатывает чуть дольше.

**Жёсткое условие (FR-47).** Очистка включается только для рабочего пространства,
где репозиторий настроен и доступен. Без Git снимок — единственная копия содержимого,
и удалять его нельзя. До этапа 7a правило ретеншена не работает ни для кого;
это не недоделка, а прямое следствие того, что восстанавливать неоткуда.

**Порядок при удалении модели.** Soft delete не трогает версии. Физическое удаление
модели вместе с версиями — только администратором и явной операцией; коммиты в Git
при этом не переписываются, репозиторий остаётся журналом.

---

### 11.6 Что нужно сделать уже на этапе 1

Чтобы не переделывать хранение под Git задним числом, в первую версию закладывается
малое, но обязательное:

1. **`sort_order` разреженный** (шаг 1000) во всех таблицах с порядком — `ModelFolder`,
   `Element`, `Relationship`, `View`, `ViewNode`, `ViewEdge`, `*Property`.
   Плотная нумерация превратила бы любую вставку в diff на всю папку. Изменение дешёвое
   сейчас и дорогое потом.
2. **`ModelVersion.git_sha`** (nullable) — связь версии с коммитом.
3. **`Workspace.git_repo_url`, `git_branch`, `git_token_ref`** (nullable) — настройки
   интеграции, пустые до этапа 7.
4. **`raw_xml` действительно заполняется** для всего неподдержанного (FR-03) — иначе
   цикл БД → Git → БД потеряет данные.

Слаги имён файлов в базе не хранятся: вычисляются при экспорте детерминированно.


---

## Чем проверяется хранение

Отдельного набора тестов у схемы нет: она проверяется теми же контурами, что и кодек.
Все они описаны в [`backend.md`](backend.md#9-план-тестирования).

| Проверка | Где | Что ловит |
|----------|-----|-----------|
| `git_roundtrip` | [§9.1](backend.md#91-golden-file-тесты-round-trip--критический-контур) | Цикл БД → YAML → БД → `.archimate` даёт исходный файл: потеря поля в схеме видна сразу |
| Импорт → сохранение в БД → экспорт | [§9.3](backend.md#93-интеграционные-тесты-testcontainers) | Полнота отображения `.archimate` в таблицы, включая `raw_xml` и `sort_order` |
| Конкурентное сохранение | [§9.3](backend.md#93-интеграционные-тесты-testcontainers) | Атомарность изменений и `409` (NFR-07) |
| Блокировка двумя клиентами | [§9.3](backend.md#93-интеграционные-тесты-testcontainers) | `ModelLock`: захват, конфликт, принудительное снятие |
| Версии: создание, список, откат | [§9.3](backend.md#93-интеграционные-тесты-testcontainers) | `ModelVersion` вместе с правилом ретеншена §4.4 |

Контейнер интеграционных тестов — `postgres:16`, тот же образ, что в
[§10.1](archi-creator.md#101-состав).
