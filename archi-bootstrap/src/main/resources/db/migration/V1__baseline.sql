-- ───────────────────────────────────────────────────────────────────
-- V1 — базовая линия схемы: рабочее пространство.
--
-- Остальные таблицы (model, element, relationship, view, ...) заводит
-- этап 2 отдельными миграциями: до кодека хранить нечего. Workspace
-- появляется здесь, потому что от него зависят настройки, которые
-- нужны раньше данных, — лимит ИИ, строгость импорта, адрес Git.
--
-- Схема: docs/database.md §4.1 (поля), §11.6 (поля под Git).
-- Ключ: UUID, генерируется приложением (ADR-0002, UUIDv7), поэтому
-- DEFAULT у столбца нет — значение всегда приходит снаружи.
-- ───────────────────────────────────────────────────────────────────

CREATE TABLE workspace
(
    id                     uuid        NOT NULL,
    name                   text        NOT NULL,

    -- ИИ-помощник: выключатель и месячный бюджет в токенах (§7.5).
    -- NULL в лимите = бюджет не задан, ограничения по месяцу нет.
    ai_enabled             boolean     NOT NULL DEFAULT false,
    ai_monthly_token_limit bigint,

    -- Строгость импорта (§8.3, FR-49). По умолчанию false не из мягкости:
    -- реальные выгрузки почти всегда содержат вольности, и строгий режим
    -- сделал бы инструмент бесполезным там, где он нужен.
    strict_import          boolean     NOT NULL DEFAULT false,

    created_at             timestamptz NOT NULL DEFAULT now(),

    -- Настройки Git-интеграции (§11.6). Пустые до этапа 7a; заведены
    -- сейчас, чтобы не переделывать хранение задним числом.
    -- git_token_ref — ссылка на секрет, а не сам токен (NFR-06).
    git_repo_url           text,
    git_branch             text,
    git_token_ref          text,

    CONSTRAINT pk_workspace PRIMARY KEY (id),
    CONSTRAINT uq_workspace_name UNIQUE (name),
    CONSTRAINT ck_workspace_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_workspace_ai_limit_positive CHECK (ai_monthly_token_limit IS NULL OR ai_monthly_token_limit > 0)
);

COMMENT ON TABLE workspace IS
    'Рабочее пространство: контейнер моделей и место настроек (docs/database.md §4.1).';
COMMENT ON COLUMN workspace.git_token_ref IS
    'Ссылка на секрет с токеном доступа к репозиторию, не сам токен (NFR-06).';
