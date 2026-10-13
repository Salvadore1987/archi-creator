package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uz.salvadore.hamkorbank.archi.bootstrap.roundtrip.XmlEquivalence.assertXmlEquivalent;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;

/**
 * Критерий этапа: цикл БД → Git → БД проходит без потерь. Эталонная модель
 * импортируется, публикуется коммитом, из дерева коммита собирается обратно
 * и снова импортируется — выгрузка второй модели совпадает с исходным файлом.
 */
class GitRoundTripIT extends GitApiTest {

    @Test
    @DisplayName("FR-40, §12: цикл БД → YAML в Git → БД → .archimate даёт исходный файл")
    void databaseToGitToDatabaseIsLossless() throws Exception {
        byte[] original = Fixtures.reference();
        UUID first = importModel(original);
        publishing.publishPending();
        byte[] exportedFirst = export(first);

        byte[] fromGit = new ArchiXmlWriter().write(new GitYamlReader().read(modelDirectory(history().getFirst())));
        assertThat(fromGit).as("из дерева коммита — те же байты, что выгрузка версии").isEqualTo(exportedFirst);

        UUID second = importModel(fromGit);
        assertXmlEquivalent(original, export(second));
    }

    private byte[] export(UUID model) throws Exception {
        return mvc.perform(get("/api/v1/models/" + model + "/export").with(viewer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }
}
