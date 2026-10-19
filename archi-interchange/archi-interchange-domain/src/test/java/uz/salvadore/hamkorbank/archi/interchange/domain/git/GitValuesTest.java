package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

class GitValuesTest {

    private static final WorkspaceId WORKSPACE =
            new WorkspaceId(UUIDUtil.uuid("0192f000-0000-7000-8000-000000000001"));

    @ParameterizedTest
    @ValueSource(strings = {"main", "feature/x", "refs/heads/main", "v1.0", "0a1b2c3", "release-2026.10"})
    void validNamesAreAcceptedAsRefAndBranch(String name) {
        assertDoesNotThrow(() -> GitRef.of(name));
        assertDoesNotThrow(() -> binding(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-x", "/x", ".hidden", "feature/.x", "a.lock/b", "a..b", "a//b", "a/", "a.", "a b",
        "a@{1}", "a.lock"})
    void invalidNamesAreRejectedAsRefAndBranch(String name) {
        assertThrows(InvalidValueException.class, () -> GitRef.of(name));
        assertThrows(InvalidValueException.class, () -> binding(name));
    }

    @Test
    @DisplayName("ссылка из null — отказ значения, а не ветка с именем null")
    void nullRefIsRejected() {
        assertThrows(InvalidValueException.class, () -> GitRef.of(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Анна\rИванова", "Анна\tИванова", "Анна\u0007", "Анна\u007f"})
    void controlCharactersInAuthorNameAreRejected(String name) {
        assertThrows(InvalidValueException.class, () -> new GitAuthor("subject", name, "anna@example.com"));
    }

    @Test
    @DisplayName("управляющий символ в субъекте и почте автора — отказ")
    void controlCharactersInSubjectAndEmailAreRejected() {
        assertThrows(InvalidValueException.class, () -> new GitAuthor("sub\rject", "Анна", "anna@example.com"));
        assertThrows(InvalidValueException.class, () -> new GitAuthor("subject", "Анна", "anna\u0000@example.com"));
    }

    private static GitBinding binding(String branch) {
        return GitBinding.configure(WORKSPACE, "https://git.example.com/archi.git", branch,
                SecretRef.of("env:GIT_TOKEN"), List.of());
    }
}
