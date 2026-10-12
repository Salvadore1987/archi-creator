-- ───────────────────────────────────────────────────────────────────
-- V4 — помощник: сессии обращений, бюджеты месяцев, исход в аудите.
--
-- Лимит и выключатель остаются столбцами workspace (V1): это настройки
-- пространства. Пустой ai_monthly_token_limit значит «лимит по умолчанию
-- из конфигурации», а не «без ограничения»: бюджет без верхней границы
-- ничего не ограничивает.
--
-- От запроса хранится только отпечаток (SHA-256 параметров): ни контекст
-- модели, ни промпт, ни описание пользователя в базу не попадают.
-- Результат — находки, план, ответ — хранится с сессией: повтор с тем же
-- ключом отдаёт его без нового обращения к внешнему API.
-- ───────────────────────────────────────────────────────────────────

CREATE TABLE advisor_session
(
    id                uuid        NOT NULL,
    workspace_id      uuid        NOT NULL,
    -- Без внешнего ключа: сессия и аудит переживают окончательное удаление модели.
    model_id          uuid        NOT NULL,
    function          text        NOT NULL,
    view_id           uuid,
    element_archi_id  text,
    idempotency_key   text        NOT NULL,
    fingerprint       text        NOT NULL,
    status            text        NOT NULL,
    context_tokens    integer,
    context_truncated boolean     NOT NULL DEFAULT false,
    prompt_tokens     bigint      NOT NULL DEFAULT 0,
    completion_tokens bigint      NOT NULL DEFAULT 0,
    rejection_reason  text,
    -- Находки, план или ответ в JSON; пусто у отказа и неуспеха.
    result            text,
    requested_by      text        NOT NULL,
    requested_at      timestamptz NOT NULL,
    first_token_at    timestamptz,
    finished_at       timestamptz,

    CONSTRAINT pk_advisor_session PRIMARY KEY (id),
    CONSTRAINT fk_advisor_session_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    -- Ключ идемпотентности уникален в пределах автора.
    CONSTRAINT uq_advisor_session_key UNIQUE (requested_by, idempotency_key),
    CONSTRAINT ck_advisor_session_function CHECK (function IN ('REVIEW', 'EXPLAIN', 'PLAN', 'DOCUMENT')),
    CONSTRAINT ck_advisor_session_status
        CHECK (status IN ('REQUESTED', 'STREAMING', 'COMPLETED', 'FAILED', 'REJECTED')),
    CONSTRAINT ck_advisor_session_rejection CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT ck_advisor_session_tokens CHECK (prompt_tokens >= 0 AND completion_tokens >= 0),
    CONSTRAINT ck_advisor_session_key_length CHECK (length(idempotency_key) BETWEEN 1 AND 64)
);

CREATE INDEX ix_advisor_session_workspace ON advisor_session (workspace_id, requested_at);

-- Бюджет месяца: строка на пару (пространство, месяц). Новый месяц — новая
-- строка, а не сброс старой. Расход списывается под блокировкой строки.
CREATE TABLE token_budget
(
    id                        uuid        NOT NULL,
    workspace_id              uuid        NOT NULL,
    period                    text        NOT NULL,
    consumed_tokens           bigint      NOT NULL DEFAULT 0,
    warning_threshold_percent integer     NOT NULL DEFAULT 80,
    -- Состояние выводится из расхода и лимита; хранится для чтения глазами и отчётов.
    status                    text        NOT NULL,
    version                   bigint      NOT NULL DEFAULT 0,
    updated_at                timestamptz NOT NULL,

    CONSTRAINT pk_token_budget PRIMARY KEY (id),
    CONSTRAINT fk_token_budget_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT uq_token_budget_period UNIQUE (workspace_id, period),
    CONSTRAINT ck_token_budget_period CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_token_budget_consumed CHECK (consumed_tokens >= 0),
    CONSTRAINT ck_token_budget_threshold CHECK (warning_threshold_percent BETWEEN 1 AND 99),
    CONSTRAINT ck_token_budget_status CHECK (status IN ('OK', 'WARNING', 'EXHAUSTED'))
);

-- Аудит: ровно одна запись на сессию, с исходом — отказы видны наравне с ответами.
-- Аудит живёт дольше сессий, поэтому удаление сессии ссылку обнуляет, а запись оставляет.
ALTER TABLE ai_audit_log
    ADD COLUMN session_id       uuid,
    ADD COLUMN outcome          text NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN rejection_reason text,
    ADD CONSTRAINT fk_ai_audit_log_session FOREIGN KEY (session_id) REFERENCES advisor_session (id)
        ON DELETE SET NULL,
    ADD CONSTRAINT uq_ai_audit_log_session UNIQUE (session_id),
    ADD CONSTRAINT ck_ai_audit_log_outcome CHECK (outcome IN ('COMPLETED', 'FAILED', 'REJECTED'));

ALTER TABLE ai_audit_log
    ALTER COLUMN outcome DROP DEFAULT;
