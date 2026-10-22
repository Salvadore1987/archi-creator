package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

class GitValuesTest {

    private static final WorkspaceId WORKSPACE =
            new WorkspaceId(UUIDUtil.uuid("0192f000-0000-7000-8000-000000000001"));
    private static final Instant NOW = Instant.parse("2026-10-14T09:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"main", "feature/x", "refs/heads/main", "v1.0", "0a1b2c3", "release-2026.10"})
    @DisplayName("допустимое имя Git принимается и ссылкой, и веткой привязки")
    void validNamesAreAcceptedAsRefAndBranch(String name) {
        assertDoesNotThrow(() -> GitRef.of(name));
        assertDoesNotThrow(() -> binding(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-x", "/x", ".hidden", "feature/.x", "a.lock/b", "a..b", "a//b", "a/", "a.", "a b",
        "a@{1}", "a.lock"})
    @DisplayName("имя, которое Git не допускает, — отказ и ссылке, и ветке привязки")
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
    @DisplayName("управляющий символ в имени автора — отказ")
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
    @DisplayName("переменная токена Git принимается ссылкой на секрет")
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
    @DisplayName("адрес без учётных данных принимается, и @ в пути — не учётные данные")
    void urlsWithoutCredentialsAreAccepted(String url) {
        assertDoesNotThrow(() -> GitBinding.configure(WORKSPACE, url, null, SecretRef.of("env:ARCHI_GIT_TOKEN"),
                List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "0123456789ABCDEF0123456789abcdef01234567",
        "0123456789abcdef0123456789abcdef0123456", "0123456789abcdef0123456789abcdef012345678",
        "g123456789abcdef0123456789abcdef01234567"})
    @DisplayName("идентификатор коммита — ровно 40 hex в нижнем регистре")
    void malformedShaIsRejected(String value) {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> new GitSha(value));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.GIT_SHA_INVALID, e.reason().key());
        assertEquals(List.of(value), e.reason().args());
    }

    @ParameterizedTest
    @ValueSource(strings = {"git.example.com/archi.git", "ftp://git.example.com/a.git", "https://git example.com/a",
        "https://"})
    @DisplayName("адрес репозитория — только http(s), ssh, git, file или scp-вида")
    void malformedUrlIsRejected(String url) {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> GitBinding.configure(WORKSPACE, url,
                null, SecretRef.of("env:ARCHI_GIT_TOKEN"), List.of()));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.GIT_URL_INVALID, e.reason().key());
        assertEquals(List.of(url), e.reason().args());
    }

    @Test
    @DisplayName("привязка без адреса репозитория — отказ «значение не задано», а не NPE")
    void missingUrlIsRejected() {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> GitBinding.configure(WORKSPACE,
                null, null, SecretRef.of("env:ARCHI_GIT_TOKEN"), List.of()));

        assertEquals(InterchangeMessages.GIT_URL_INVALID, e.reason().key());
        assertEquals(List.of(Message.of(InterchangeMessages.VALUE_MISSING)), e.reason().args());
    }

    @Test
    @DisplayName("один субъект Keycloak не может дважды стоять среди авторов привязки")
    void repeatedAuthorSubjectIsRejected() {
        List<GitAuthor> authors = List.of(new GitAuthor("sub-1", "Анна", "anna@example.com"),
                new GitAuthor("sub-1", "Анна Иванова", "a.ivanova@example.com"));

        InvalidValueException e = assertThrows(InvalidValueException.class, () -> GitBinding.configure(WORKSPACE,
                "https://git.example.com/archi.git", null, SecretRef.of("env:ARCHI_GIT_TOKEN"), authors));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.GIT_AUTHOR_REPEATED, e.reason().key());
        assertEquals(List.of("sub-1"), e.reason().args());
    }

    @Test
    @DisplayName("снятая привязка не принимает отметку о доступности репозитория и не меняется")
    void disabledBindingRejectsCheck() {
        GitBinding disabled = binding("main").disabled();

        InterchangeDomainException e = assertThrows(InterchangeDomainException.class, () -> disabled.checked(NOW));

        assertEquals(InterchangeCodes.GIT_BINDING_DISABLED, e.code());
        assertEquals(InterchangeMessages.GIT_BINDING_DISABLED, e.reason().key());
        assertEquals(List.of(WORKSPACE.value()), e.reason().args());
        assertEquals(GitBindingStatus.DISABLED, disabled.status());
        assertNull(disabled.lastCheckedAt());
    }

    @ParameterizedTest
    @CsvSource({"Анна, anna", "Анна, anna@", "Анна, anna example@x.com", "Анна <a>, anna@example.com",
        "Анна>, anna@example.com"})
    @DisplayName("почта автора — вида имя@домен, в имени нет угловых скобок: они ломают строку автора")
    void malformedAuthorIsRejected(String name, String email) {
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> new GitAuthor("sub-1", name, email));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.GIT_AUTHOR_INVALID, e.reason().key());
        assertEquals(List.of("sub-1"), e.reason().args());
    }

    private static GitBinding binding(String branch) {
        return GitBinding.configure(WORKSPACE, "https://git.example.com/archi.git", branch,
                SecretRef.of("env:ARCHI_GIT_TOKEN"), List.of());
    }
}
