-- ───────────────────────────────────────────────────────────────────
-- V5 — типы Motivation и Strategy становятся редактируемыми.
--
-- Флаг supported записывается при импорте по каталогу типов того времени,
-- поэтому элементы этих четырнадцати типов, загруженные раньше, остались
-- непрозрачными. Каталог теперь их поддерживает, и флаг переворачивается.
--
-- Остаток raw_xml не трогается: импорт пишет его одинаково для поддержанного
-- и непрозрачного элемента — имя, документация и свойства и так лежат
-- в столбцах. Выгрузка таких элементов не меняется.
-- ───────────────────────────────────────────────────────────────────

UPDATE element
SET supported = true
WHERE NOT supported
  AND archi_type IN (
      'archimate:Stakeholder', 'archimate:Driver', 'archimate:Assessment', 'archimate:Goal',
      'archimate:Outcome', 'archimate:Principle', 'archimate:Requirement', 'archimate:Constraint',
      'archimate:Meaning', 'archimate:Value',
      'archimate:Resource', 'archimate:Capability', 'archimate:ValueStream', 'archimate:CourseOfAction'
  );
