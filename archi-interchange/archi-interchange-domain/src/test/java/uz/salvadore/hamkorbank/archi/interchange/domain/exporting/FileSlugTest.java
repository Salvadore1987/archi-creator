package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;

class FileSlugTest {

    @Test
    @DisplayName("имя представления — латиницей, короткий id — из идентификатора Archi")
    void viewNameIsTransliterated() {
        assertEquals("01-kontekst-as-is.f11fbcb8",
                FileSlug.of("01 Контекст AS IS", ArchiId.of("id-f11fbcb80991efa5a637e786")));
        assertEquals("shchuka-i-ezh", FileSlug.slug("Щука и Ёж"));
        assertEquals("iabs-r-style", FileSlug.slug("ИАБС / R-Style"));
        assertEquals("o-zbekiston", FileSlug.slug("O'zbekiston"));
    }

    @Test
    @DisplayName("пустое и слишком длинное имя дают годное имя файла")
    void degenerateNames() {
        assertEquals("unnamed", FileSlug.slug("«»  —"));
        String slug = FileSlug.slug("очень ".repeat(30));
        assertTrue(slug.length() <= 60 && !slug.endsWith("-"), slug);
    }

    @Test
    @DisplayName("идентификатор не в форме id-<hex> — короткий id из отпечатка, стабильно")
    void shortIdOfForeignId() {
        String first = FileSlug.shortId(ArchiId.of("my_custom.view"));
        assertEquals(8, first.length());
        assertEquals(first, FileSlug.shortId(ArchiId.of("my_custom.view")));
    }
}
