package uz.salvadore.hamkorbank.archi.bootstrap.web;

/**
 * Ключи сообщений инфраструктуры приложения: общие отказы HTTP, вход, настройки.
 */
public final class BootstrapMessages {

    public static final String BAD_REQUEST = "bootstrap.problem.bad-request";
    public static final String CONCURRENT_MODIFICATION = "bootstrap.problem.concurrent-modification";
    public static final String FILE_TOO_LARGE = "bootstrap.problem.file-too-large";
    public static final String INTEGRITY_CONFLICT = "bootstrap.problem.integrity-conflict";
    public static final String TITLE_CONFLICT = "bootstrap.problem.title-conflict";
    public static final String NOT_AUTHENTICATED = "bootstrap.security.not-authenticated";

    private BootstrapMessages() {
    }
}
