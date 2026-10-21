package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @ParameterizedTest
    @ValueSource(strings = {"env:ANTHROPIC_API_KEY", "env:spring.datasource.password", "env:DB_PASSWORD",
        "env:ARCHI_GIT_", "env:archi_git_token", "env:ARCHI_GIT_TOKEN.x"})
    @DisplayName("ссылка на секрет — только переменная токена Git: чужой секрет сервера не уходит на адрес привязки")
    void secretRefIsLimitedToGitTokens(String ref) {
        assertThrows(InvalidValueException.class, () -> SecretRef.of(ref));
    }

    @ParameterizedTest
    @ValueSource(strings = {"env:ARCHI_GIT_TOKEN", "env:ARCHI_GIT_TOKEN_2", "env:ARCHI_GIT_LANDSCAPE"})
    void gitTokenVariablesAreAccepted(String ref) {
        assertDoesNotThrow(() -> SecretRef.of(ref));
    }

    @Test
    @DisplayName("токен, вставленный вместо ссылки, не попадает в текст отказа")
    void rejectedSecretRefDoesNotEchoValue() {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> SecretRef.of("ghp_secret"));
        assertFalse(e.reason().args().contains("ghp_secret"));
        assertFalse(String.valueOf(e.getMessage()).contains("ghp_secret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://user:glpat-secret@git.example.com/archi.git",
        "https://glpat-secret@git.example.com/a.git", "HTTP://x:y@git.example.com/a.git",
        "ssh://git:pass@git.example.com/a.git"})
    @DisplayName("учётные данные в адресе — отказ, и адрес с ними в текст отказа не попадает")
    void credentialsInUrlAreRejected(String url) {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> GitBinding.configure(WORKSPACE, url,
                null, SecretRef.of("env:ARCHI_GIT_TOKEN"), List.of()));
        assertFalse(e.reason().args().stream().anyMatch(a -> String.valueOf(a).contains("secret")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ssh://git@git.example.com/a.git", "git@git.example.com:ea/a.git",
        "https://git.example.com/a@b.git", "https://git.example.com:8443/a.git"})
    void urlsWithoutCredentialsAreAccepted(String url) {
        assertDoesNotThrow(() -> GitBinding.configure(WORKSPACE, url, null, SecretRef.of("env:ARCHI_GIT_TOKEN"),
                List.of()));
    }

    private static GitBinding binding(String branch) {
        return GitBinding.configure(WORKSPACE, "https://git.example.com/archi.git", branch,
                SecretRef.of("env:ARCHI_GIT_TOKEN"), List.of());
    }
}
