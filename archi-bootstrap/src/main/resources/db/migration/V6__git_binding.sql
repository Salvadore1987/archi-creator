-- ───────────────────────────────────────────────────────────────────
-- V6 — привязка рабочего пространства к Git-репозиторию.
--
-- Привязка — отдельная таблица, а не столбцы workspace: у неё своё
-- состояние (доступен ли репозиторий, до какого коммита сервер довёл
-- ветку), и пишет её другой контекст. Пустые столбцы git_* из V1,
-- заведённые впрок, удаляются: данных в них нет.
--
-- Токена в базе нет — только ссылка на секрет (env:ИМЯ).
-- ───────────────────────────────────────────────────────────────────

CREATE TABLE git_binding
(
    workspace_id    uuid        NOT NULL,
    repo_url        text        NOT NULL,
    branch          text        NOT NULL,
    token_ref       text        NOT NULL,
    status          text        NOT NULL,
    -- Последний коммит, до которого ветку довёл сервер: всё, что новее
    -- и трогает каталог модели, сделано не нами — расхождение.
    last_commit_sha text,
    last_checked_at timestamptz,

    CONSTRAINT pk_git_binding PRIMARY KEY (workspace_id),
    CONSTRAINT fk_git_binding_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id) ON DELETE CASCADE,
    CONSTRAINT ck_git_binding_status CHECK (status IN ('CONFIGURED', 'UNREACHABLE', 'DISABLED')),
    CONSTRAINT ck_git_binding_url CHECK (length(repo_url) BETWEEN 1 AND 1000),
    CONSTRAINT ck_git_binding_branch CHECK (length(branch) BETWEEN 1 AND 200),
    CONSTRAINT ck_git_binding_token_ref CHECK (token_ref ~ '^env:'),
    CONSTRAINT ck_git_binding_sha CHECK (last_commit_sha IS NULL OR last_commit_sha ~ '^[0-9a-f]{40}$')
);

-- Субъект Keycloak → автор коммита. Порядок — как задал администратор.
-- Ключ — по месту в списке: замена списка переписывает строки по месту,
-- и проверка уникальности субъекта отложена до конца транзакции.
CREATE TABLE git_author
(
    workspace_id uuid NOT NULL,
    sort_order   int  NOT NULL,
    subject      text NOT NULL,
    name         text NOT NULL,
    email        text NOT NULL,

    CONSTRAINT pk_git_author PRIMARY KEY (workspace_id, sort_order),
    CONSTRAINT uq_git_author_subject UNIQUE (workspace_id, subject) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_git_author_binding FOREIGN KEY (workspace_id) REFERENCES git_binding (workspace_id)
        ON DELETE CASCADE
);

ALTER TABLE workspace
    DROP COLUMN git_repo_url,
    DROP COLUMN git_branch,
    DROP COLUMN git_token_ref;
