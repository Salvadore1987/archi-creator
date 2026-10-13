-- ───────────────────────────────────────────────────────────────────
-- V7 — outbox событий с обязательной доставкой.
--
-- Событие, потеря которого что-то ломает, пишется сюда той же
-- транзакцией, что и изменение: откат не оставляет записи, коммит не
-- теряет её. Фоновый обработчик читает записи по порядку внутри
-- partition_key и закрывает их; доставка — хотя бы один раз, потребитель
-- идемпотентен. Сейчас сюда пишется одно событие — зафиксированная версия
-- модели, которую публикует выгрузка в Git.
-- ───────────────────────────────────────────────────────────────────

CREATE TABLE outbox_event
(
    id              uuid        NOT NULL,
    event_type      text        NOT NULL,
    -- Порядок доставки — внутри ключа: версии одной модели строго по номеру.
    partition_key   uuid        NOT NULL,
    payload         jsonb       NOT NULL,
    -- PENDING — ждёт; DONE — доставлено; SKIPPED — доставлять некуда
    -- (модели или привязки нет); DIVERGED — потребитель остановился и ждёт
    -- разбора, следующие записи ключа стоят за ней.
    status          text        NOT NULL DEFAULT 'PENDING',
    attempts        int         NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    last_error      text,
    created_at      timestamptz NOT NULL,
    processed_at    timestamptz,

    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT ck_outbox_event_status CHECK (status IN ('PENDING', 'DONE', 'SKIPPED', 'DIVERGED')),
    CONSTRAINT ck_outbox_event_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_outbox_event_open ON outbox_event (event_type, partition_key, id)
    WHERE status IN ('PENDING', 'DIVERGED');
