-- ───────────────────────────────────────────────────────────────────
-- V10 — индексы внешних ключей без своего индекса.
--
-- Удаление строки, на которую ссылается внешний ключ, проверяется поиском
-- ссылающихся строк. Без индекса с ведущим столбцом ключа это полный
-- просмотр таблицы на каждую удалённую строку: каскадное удаление модели,
-- замена содержимого и удаление представления становились квадратичными
-- по числу узлов и рёбер всей базы. Индексы дерева (модель, родитель,
-- порядок) начинаются не со столбца ключа и проверке не помогают.
--
-- Концы ребра заполнены на половину строк — индексы частичные.
-- ───────────────────────────────────────────────────────────────────

CREATE INDEX ix_model_folder_parent_id ON model_folder (parent_id);
CREATE INDEX ix_relationship_folder ON relationship (folder_id);
CREATE INDEX ix_view_folder ON view (folder_id);
CREATE INDEX ix_view_node_parent ON view_node (parent_id);

CREATE INDEX ix_view_edge_source_node ON view_edge (source_node_id) WHERE source_node_id IS NOT NULL;
CREATE INDEX ix_view_edge_source_edge ON view_edge (source_edge_id) WHERE source_edge_id IS NOT NULL;
CREATE INDEX ix_view_edge_target_node ON view_edge (target_node_id) WHERE target_node_id IS NOT NULL;
CREATE INDEX ix_view_edge_target_edge ON view_edge (target_edge_id) WHERE target_edge_id IS NOT NULL;
