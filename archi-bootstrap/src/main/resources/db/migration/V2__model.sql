-- ───────────────────────────────────────────────────────────────────
-- V2 — модель целиком: дерево папок, элементы, связи, представления,
-- версии, блокировки, список доступа, идемпотентность, сессии импорта.
--
-- Схема: docs/database.md §4.1–§4.2, агрегаты — spec/domain/modeling/aggregates.yaml.
-- Ключи — UUIDv7 от приложения (ADR-0002), DEFAULT у них нет.
--
-- Три правила, которые держат round-trip (FR-02, NFR-05):
--   * archi_id уникален в пределах модели (INV-MDL-001) — UNIQUE (model_id, archi_id)
--     в каждой таблице с ним;
--   * sort_order разреженный, шаг 1000 (INV-MDL-005, §11.6) — CHECK > 0, без DEFAULT:
--     порядок назначает домен, а не база;
--   * raw_xml — остаток XML объекта (ADR-0017): всё, что не легло в столбцы,
--     с позициями. Формат — interchange, база его не разбирает.
--
-- Внутренние ссылки модели — DEFERRABLE INITIALLY DEFERRED и ON DELETE NO ACTION:
-- по смыслу RESTRICT (INV-MDL-004), но проверка в конце транзакции. Импорт пишет
-- тысячи строк одной транзакцией, связь ссылается на связь, вставленную позже,
-- а физическое удаление модели снимает всё каскадом от строки model. Отказ при
-- удалении связанного элемента даёт домен раньше базы; база — страховка.
-- ───────────────────────────────────────────────────────────────────

CREATE TABLE model
(
    id            uuid        NOT NULL,
    workspace_id  uuid        NOT NULL,
    archi_id      text        NOT NULL,
    name          text        NOT NULL,
    documentation text,
    archi_version text        NOT NULL,
    status        text        NOT NULL,
    raw_xml       text,
    created_by    text        NOT NULL,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    -- Оптимистичная блокировка строки (NFR-07).
    version       bigint      NOT NULL,

    CONSTRAINT pk_model PRIMARY KEY (id),
    CONSTRAINT fk_model_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    -- PURGED строки не оставляет: физическое удаление стирает её вместе с содержимым.
    CONSTRAINT ck_model_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_model_name_length CHECK (length(name) <= 500),
    CONSTRAINT ck_model_archi_version CHECK (archi_version ~ '^\d+\.\d+\.\d+$')
);

CREATE INDEX ix_model_workspace ON model (workspace_id, status);

COMMENT ON TABLE model IS 'Архитектурная модель — агрегат ArchitectureModel (docs/database.md §4.1).';
COMMENT ON COLUMN model.raw_xml IS 'Остаток корня archimate:model: пространства имён, profile, неизвестное (ADR-0017).';

CREATE TABLE model_property
(
    owner_id   uuid   NOT NULL,
    sort_order bigint NOT NULL,
    key        text   NOT NULL,
    value      text   NOT NULL,

    CONSTRAINT pk_model_property PRIMARY KEY (owner_id, sort_order),
    CONSTRAINT fk_model_property_owner FOREIGN KEY (owner_id) REFERENCES model (id) ON DELETE CASCADE,
    CONSTRAINT ck_model_property_sort_order CHECK (sort_order > 0)
);

-- ── Дерево папок (INV-MDL-009) ─────────────────────────────────────

CREATE TABLE model_folder
(
    id          uuid   NOT NULL,
    model_id    uuid   NOT NULL,
    parent_id   uuid,
    archi_id    text   NOT NULL,
    name        text   NOT NULL,
    folder_type text,
    sort_order  bigint NOT NULL,
    raw_xml     text,

    CONSTRAINT pk_model_folder PRIMARY KEY (id),
    CONSTRAINT fk_model_folder_model FOREIGN KEY (model_id) REFERENCES model (id) ON DELETE CASCADE,
    CONSTRAINT fk_model_folder_parent FOREIGN KEY (parent_id) REFERENCES model_folder (id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_model_folder_archi_id UNIQUE (model_id, archi_id),
    -- Тип только у корня, родитель только у вложенной.
    CONSTRAINT ck_model_folder_root CHECK ((folder_type IS NULL) = (parent_id IS NOT NULL)),
    CONSTRAINT ck_model_folder_type CHECK (folder_type IS NULL OR folder_type IN
        ('STRATEGY', 'BUSINESS', 'APPLICATION', 'TECHNOLOGY', 'MOTIVATION', 'IMPLEMENTATION_MIGRATION',
         'OTHER', 'RELATIONS', 'DIAGRAMS')),
    CONSTRAINT ck_model_folder_sort_order CHECK (sort_order > 0)
);

CREATE UNIQUE INDEX uq_model_folder_root_type ON model_folder (model_id, folder_type) WHERE folder_type IS NOT NULL;
CREATE INDEX ix_model_folder_parent ON model_folder (model_id, parent_id, sort_order);

-- ── Элементы ───────────────────────────────────────────────────────

CREATE TABLE element
(
    id            uuid    NOT NULL,
    model_id      uuid    NOT NULL,
    folder_id     uuid    NOT NULL,
    archi_id      text    NOT NULL,
    archi_type    text    NOT NULL,
    -- Производное от archi_type; хранится ради выборки по слою.
    layer         text    NOT NULL,
    name          text    NOT NULL,
    documentation text,
    sort_order    bigint  NOT NULL,
    supported     boolean NOT NULL,
    raw_xml       text,

    CONSTRAINT pk_element PRIMARY KEY (id),
    CONSTRAINT fk_element_model FOREIGN KEY (model_id) REFERENCES model (id) ON DELETE CASCADE,
    CONSTRAINT fk_element_folder FOREIGN KEY (folder_id) REFERENCES model_folder (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_element_archi_id UNIQUE (model_id, archi_id),
    CONSTRAINT ck_element_sort_order CHECK (sort_order > 0),
    -- FR-03: непрозрачное без остатка невоспроизводимо.
    CONSTRAINT ck_element_opaque_has_raw CHECK (supported OR raw_xml IS NOT NULL)
);

CREATE INDEX ix_element_type ON element (model_id, archi_type);
CREATE INDEX ix_element_folder ON element (folder_id, sort_order);

CREATE TABLE element_property
(
    owner_id   uuid   NOT NULL,
    sort_order bigint NOT NULL,
    key        text   NOT NULL,
    value      text   NOT NULL,

    CONSTRAINT pk_element_property PRIMARY KEY (owner_id, sort_order),
    CONSTRAINT fk_element_property_owner FOREIGN KEY (owner_id) REFERENCES element (id) ON DELETE CASCADE,
    CONSTRAINT ck_element_property_sort_order CHECK (sort_order > 0)
);

-- ── Связи (INV-MDL-004) ────────────────────────────────────────────

CREATE TABLE relationship
(
    id                     uuid    NOT NULL,
    model_id               uuid    NOT NULL,
    folder_id              uuid    NOT NULL,
    archi_id               text    NOT NULL,
    archi_type             text    NOT NULL,
    -- Конец — элемент или связь: ассоциация к связи — законный ArchiMate.
    source_element_id      uuid,
    source_relationship_id uuid,
    target_element_id      uuid,
    target_relationship_id uuid,
    name                   text,
    documentation          text,
    access_type            text,
    directed               boolean,
    sort_order             bigint  NOT NULL,
    supported              boolean NOT NULL,
    raw_xml                text,

    CONSTRAINT pk_relationship PRIMARY KEY (id),
    CONSTRAINT fk_relationship_model FOREIGN KEY (model_id) REFERENCES model (id) ON DELETE CASCADE,
    CONSTRAINT fk_relationship_folder FOREIGN KEY (folder_id) REFERENCES model_folder (id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_relationship_source_element FOREIGN KEY (source_element_id) REFERENCES element (id)
        ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_relationship_source_relationship FOREIGN KEY (source_relationship_id) REFERENCES relationship (id)
        ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_relationship_target_element FOREIGN KEY (target_element_id) REFERENCES element (id)
        ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_relationship_target_relationship FOREIGN KEY (target_relationship_id) REFERENCES relationship (id)
        ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_relationship_archi_id UNIQUE (model_id, archi_id),
    CONSTRAINT ck_relationship_one_source CHECK (num_nonnulls(source_element_id, source_relationship_id) = 1),
    CONSTRAINT ck_relationship_one_target CHECK (num_nonnulls(target_element_id, target_relationship_id) = 1),
    CONSTRAINT ck_relationship_access_type CHECK (access_type IS NULL OR access_type IN
        ('WRITE', 'READ', 'ACCESS', 'READ_WRITE')),
    CONSTRAINT ck_relationship_sort_order CHECK (sort_order > 0),
    CONSTRAINT ck_relationship_opaque_has_raw CHECK (supported OR raw_xml IS NOT NULL)
);

CREATE INDEX ix_relationship_type ON relationship (model_id, archi_type);
CREATE INDEX ix_relationship_source_element ON relationship (source_element_id);
CREATE INDEX ix_relationship_target_element ON relationship (target_element_id);
CREATE INDEX ix_relationship_source_relationship ON relationship (source_relationship_id);
CREATE INDEX ix_relationship_target_relationship ON relationship (target_relationship_id);

CREATE TABLE relationship_property
(
    owner_id   uuid   NOT NULL,
    sort_order bigint NOT NULL,
    key        text   NOT NULL,
    value      text   NOT NULL,

    CONSTRAINT pk_relationship_property PRIMARY KEY (owner_id, sort_order),
    CONSTRAINT fk_relationship_property_owner FOREIGN KEY (owner_id) REFERENCES relationship (id) ON DELETE CASCADE,
    CONSTRAINT ck_relationship_property_sort_order CHECK (sort_order > 0)
);

-- ── Представления (INV-MDL-008) ────────────────────────────────────

CREATE TABLE view
(
    id            uuid   NOT NULL,
    model_id      uuid   NOT NULL,
    folder_id     uuid   NOT NULL,
    archi_id      text   NOT NULL,
    archi_type    text   NOT NULL,
    name          text   NOT NULL,
    documentation text,
    viewpoint     text,
    sort_order    bigint NOT NULL,
    raw_xml       text,
    version       bigint NOT NULL,

    CONSTRAINT pk_view PRIMARY KEY (id),
    CONSTRAINT fk_view_model FOREIGN KEY (model_id) REFERENCES model (id) ON DELETE CASCADE,
    CONSTRAINT fk_view_folder FOREIGN KEY (folder_id) REFERENCES model_folder (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_view_archi_id UNIQUE (model_id, archi_id),
    CONSTRAINT ck_view_sort_order CHECK (sort_order > 0)
);

CREATE INDEX ix_view_model ON view (model_id);

CREATE TABLE view_property
(
    owner_id   uuid   NOT NULL,
    sort_order bigint NOT NULL,
    key        text   NOT NULL,
    value      text   NOT NULL,

    CONSTRAINT pk_view_property PRIMARY KEY (owner_id, sort_order),
    CONSTRAINT fk_view_property_owner FOREIGN KEY (owner_id) REFERENCES view (id) ON DELETE CASCADE,
    CONSTRAINT ck_view_property_sort_order CHECK (sort_order > 0)
);

CREATE TABLE view_node
(
    id             uuid    NOT NULL,
    -- Повтор model_id ради UNIQUE (model_id, archi_id) без триггеров.
    model_id       uuid    NOT NULL,
    view_id        uuid    NOT NULL,
    parent_id      uuid,
    archi_id       text    NOT NULL,
    archi_type     text    NOT NULL,
    kind           text    NOT NULL,
    element_id     uuid,
    -- bounds относительно родителя (§3.4, п. 4); -1 в размере — «по умолчанию» Archi.
    x              integer NOT NULL,
    y              integer NOT NULL,
    width          integer NOT NULL,
    height         integer NOT NULL,
    fill_color     text,
    font           text,
    font_color     text,
    line_color     text,
    text_alignment integer,
    sort_order     bigint  NOT NULL,
    raw_xml        text,

    CONSTRAINT pk_view_node PRIMARY KEY (id),
    CONSTRAINT fk_view_node_model FOREIGN KEY (model_id) REFERENCES model (id) ON DELETE CASCADE,
    CONSTRAINT fk_view_node_view FOREIGN KEY (view_id) REFERENCES view (id) ON DELETE CASCADE,
    CONSTRAINT fk_view_node_parent FOREIGN KEY (parent_id) REFERENCES view_node (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_view_node_element FOREIGN KEY (element_id) REFERENCES element (id)
        ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_view_node_archi_id UNIQUE (model_id, archi_id),
    CONSTRAINT ck_view_node_kind CHECK (kind IN ('DIAGRAM_OBJECT', 'GROUP', 'NOTE', 'OTHER')),
    CONSTRAINT ck_view_node_element CHECK (
        (kind = 'DIAGRAM_OBJECT' AND element_id IS NOT NULL)
            OR (kind IN ('GROUP', 'NOTE') AND element_id IS NULL)
            OR kind = 'OTHER'),
    CONSTRAINT ck_view_node_size CHECK ((width > 0 OR width = -1) AND (height > 0 OR height = -1)),
    CONSTRAINT ck_view_node_sort_order CHECK (sort_order > 0)
);

-- Сборка дерева представления одним запросом (§4.2).
CREATE INDEX ix_view_node_tree ON view_node (view_id, parent_id, sort_order);
CREATE INDEX ix_view_node_element ON view_node (element_id);

CREATE TABLE view_edge
(
    id              uuid   NOT NULL,
    model_id        uuid   NOT NULL,
    view_id         uuid   NOT NULL,
    archi_id        text   NOT NULL,
    archi_type      text   NOT NULL,
    relationship_id uuid,
    -- Конец — узел или ребро: ребро к ребру рисует связь, конец которой — связь.
    source_node_id  uuid,
    source_edge_id  uuid,
    target_node_id  uuid,
    target_edge_id  uuid,
    -- Точки перегиба Archi: [[startX, startY, endX, endY], ...].
    bendpoints      jsonb  NOT NULL,
    fill_color      text,
    font            text,
    font_color      text,
    line_color      text,
    text_alignment  integer,
    -- Позиция в содержимом источника: в файле ребро — sourceConnection источника.
    sort_order      bigint NOT NULL,
    raw_xml         text,

    CONSTRAINT pk_view_edge PRIMARY KEY (id),
    CONSTRAINT fk_view_edge_model FOREIGN KEY (model_id) REFERENCES model (id) ON DELETE CASCADE,
    CONSTRAINT fk_view_edge_view FOREIGN KEY (view_id) REFERENCES view (id) ON DELETE CASCADE,
    CONSTRAINT fk_view_edge_relationship FOREIGN KEY (relationship_id) REFERENCES relationship (id)
        ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_view_edge_source_node FOREIGN KEY (source_node_id) REFERENCES view_node (id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_view_edge_source_edge FOREIGN KEY (source_edge_id) REFERENCES view_edge (id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_view_edge_target_node FOREIGN KEY (target_node_id) REFERENCES view_node (id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_view_edge_target_edge FOREIGN KEY (target_edge_id) REFERENCES view_edge (id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_view_edge_archi_id UNIQUE (model_id, archi_id),
    CONSTRAINT ck_view_edge_one_source CHECK (num_nonnulls(source_node_id, source_edge_id) = 1),
    CONSTRAINT ck_view_edge_one_target CHECK (num_nonnulls(target_node_id, target_edge_id) = 1),
    CONSTRAINT ck_view_edge_bendpoints CHECK (jsonb_typeof(bendpoints) = 'array'),
    CONSTRAINT ck_view_edge_sort_order CHECK (sort_order > 0)
);

CREATE INDEX ix_view_edge_view ON view_edge (view_id);
CREATE INDEX ix_view_edge_relationship ON view_edge (relationship_id);

-- ── Версии (INV-MDL-010) ───────────────────────────────────────────

CREATE TABLE model_version
(
    id           uuid        NOT NULL,
    model_id     uuid        NOT NULL,
    version_no   bigint      NOT NULL,
    author       text        NOT NULL,
    comment      text,
    -- Метка релиза: снимок помеченной версии не удаляется (FR-48).
    label        text,
    created_at   timestamptz NOT NULL,
    -- Сжатый .archimate; NULL — очищен по ретеншену (§4.4), только при Git (FR-47).
    snapshot     bytea,
    content_hash text        NOT NULL,
    git_sha      text,

    CONSTRAINT pk_model_version PRIMARY KEY (id),
    CONSTRAINT fk_model_version_model FOREIGN KEY (model_id) REFERENCES model (id) ON DELETE CASCADE,
    CONSTRAINT uq_model_version_no UNIQUE (model_id, version_no),
    CONSTRAINT ck_model_version_no CHECK (version_no > 0),
    CONSTRAINT ck_model_version_comment CHECK (comment IS NULL OR length(comment) <= 1000),
    CONSTRAINT ck_model_version_label CHECK (label IS NULL OR length(label) <= 100),
    CONSTRAINT ck_model_version_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_model_version_git_sha CHECK (git_sha IS NULL OR git_sha ~ '^[0-9a-f]{40}$')
);

-- ── Блокировка (INV-MDL-006) ───────────────────────────────────────

CREATE TABLE model_lock
(
    -- PK = одна блокировка на модель: второй захват — нарушение ключа, а не гонка.
    model_id    uuid        NOT NULL,
    owner       text        NOT NULL,
    acquired_at timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,

    CONSTRAINT pk_model_lock PRIMARY KEY (model_id),
    CONSTRAINT fk_model_lock_model FOREIGN KEY (model_id) REFERENCES model (id) ON DELETE CASCADE,
    CONSTRAINT ck_model_lock_expiry CHECK (expires_at > acquired_at)
);

-- ── Список доступа (FR-29, INV-MDL-011) ────────────────────────────

CREATE TABLE model_access_entry
(
    model_id       uuid NOT NULL,
    principal_type text NOT NULL,
    principal      text NOT NULL,
    access         text NOT NULL,
    sort_order     integer NOT NULL,

    CONSTRAINT pk_model_access_entry PRIMARY KEY (model_id, principal_type, principal),
    CONSTRAINT fk_model_access_entry_model FOREIGN KEY (model_id) REFERENCES model (id) ON DELETE CASCADE,
    CONSTRAINT ck_model_access_entry_type CHECK (principal_type IN ('USER', 'GROUP')),
    CONSTRAINT ck_model_access_entry_access CHECK (access IN ('READ', 'WRITE'))
);

-- ── Идемпотентность (INV-MDL-003) ──────────────────────────────────

CREATE TABLE idempotency_record
(
    scope       text        NOT NULL,
    actor       text        NOT NULL,
    idem_key    text        NOT NULL,
    fingerprint text        NOT NULL,
    result_ref  text        NOT NULL,
    created_at  timestamptz NOT NULL,

    CONSTRAINT pk_idempotency_record PRIMARY KEY (scope, actor, idem_key),
    CONSTRAINT ck_idempotency_key_length CHECK (length(idem_key) BETWEEN 1 AND 64)
);

-- ── Сессии импорта (INV-IXC-002, INV-IXC-003) ──────────────────────

CREATE TABLE import_session
(
    id              uuid        NOT NULL,
    workspace_id    uuid        NOT NULL,
    target_model_id uuid,
    source_name     text        NOT NULL,
    source_hash     text        NOT NULL,
    source_size     bigint      NOT NULL,
    idempotency_key text        NOT NULL,
    strict_mode     boolean     NOT NULL,
    status          text        NOT NULL,
    started_by      text        NOT NULL,
    started_at      timestamptz NOT NULL,
    finished_at     timestamptz,
    -- Результат применения: модель и её первая версия. Ссылки на model нет
    -- намеренно — сессия переживает физическое удаление модели как журнал.
    model_id        uuid,
    version_no      bigint,

    CONSTRAINT pk_import_session PRIMARY KEY (id),
    CONSTRAINT fk_import_session_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT uq_import_session_key UNIQUE (workspace_id, idempotency_key),
    CONSTRAINT ck_import_session_status CHECK (status IN ('RECEIVED', 'PARSED', 'VALIDATED', 'APPLIED', 'REJECTED')),
    CONSTRAINT ck_import_session_hash CHECK (source_hash ~ '^[0-9a-f]{64}$')
);

CREATE TABLE import_finding
(
    id         uuid    NOT NULL,
    session_id uuid    NOT NULL,
    ordinal    integer NOT NULL,
    severity   text    NOT NULL,
    code       text    NOT NULL,
    message    text    NOT NULL,
    archi_id   text,
    xml_line   integer,

    CONSTRAINT pk_import_finding PRIMARY KEY (id),
    CONSTRAINT fk_import_finding_session FOREIGN KEY (session_id) REFERENCES import_session (id) ON DELETE CASCADE,
    CONSTRAINT uq_import_finding_ordinal UNIQUE (session_id, ordinal),
    CONSTRAINT ck_import_finding_severity CHECK (severity IN ('ERROR', 'WARNING', 'INFO'))
);

-- ── Аудит ИИ (§4.1; наполняет этап 5) ──────────────────────────────

CREATE TABLE ai_audit_log
(
    id                uuid        NOT NULL,
    workspace_id      uuid        NOT NULL,
    model_id          uuid,
    user_subject      text        NOT NULL,
    action            text        NOT NULL,
    prompt_tokens     integer     NOT NULL,
    completion_tokens integer     NOT NULL,
    created_at        timestamptz NOT NULL,

    CONSTRAINT pk_ai_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_ai_audit_log_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id)
);

CREATE INDEX ix_ai_audit_log_month ON ai_audit_log (workspace_id, created_at);
