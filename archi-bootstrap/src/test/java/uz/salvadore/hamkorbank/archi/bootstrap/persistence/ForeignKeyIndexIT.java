package uz.salvadore.hamkorbank.archi.bootstrap.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;

class ForeignKeyIndexIT extends IntegrationTest {

    /**
     * Внешний ключ без индекса с теми же ведущими столбцами: удаление строки, на которую он
     * ссылается, проверяется полным просмотром ссылающейся таблицы.
     */
    @Test
    @DisplayName("у каждого внешнего ключа схемы есть индекс с теми же ведущими столбцами")
    void everyForeignKeyHasLeadingIndex() {
        List<String> unindexed = column("""
                select c.conrelid::regclass || '.' || c.conname from pg_constraint c
                where c.contype = 'f' and c.connamespace = 'public'::regnamespace
                  and not exists (
                      select 1 from pg_index i
                      where i.indrelid = c.conrelid
                        and (i.indkey::int2[])[0:cardinality(c.conkey) - 1] @> c.conkey
                        and (i.indkey::int2[])[0:cardinality(c.conkey) - 1] <@ c.conkey)
                order by 1
                """, String.class);

        assertEquals(List.of(), unindexed);
    }
}
