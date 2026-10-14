-- ───────────────────────────────────────────────────────────────────
-- V8 — последний коммит, слитый в модель по сущностям.
--
-- Строка на модель, новое слияние её заменяет. Правки слитого коммита
-- архитектор принял: публикация не считает их расхождением, а сам коммит
-- становится вторым родителем следующего коммита, пока ветка привязки
-- его не содержит. Пишется той же транзакцией, что версия слияния.
-- ───────────────────────────────────────────────────────────────────

CREATE TABLE git_model_merge
(
    model_id   uuid        NOT NULL,
    merged_sha text        NOT NULL,
    merged_at  timestamptz NOT NULL,

    CONSTRAINT pk_git_model_merge PRIMARY KEY (model_id),
    CONSTRAINT fk_git_model_merge_model FOREIGN KEY (model_id) REFERENCES model (id) ON DELETE CASCADE,
    CONSTRAINT ck_git_model_merge_sha CHECK (merged_sha ~ '^[0-9a-f]{40}$')
);
