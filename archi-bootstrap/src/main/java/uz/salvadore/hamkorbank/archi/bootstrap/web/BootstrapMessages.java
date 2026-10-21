package uz.salvadore.hamkorbank.archi.bootstrap.web;

/**
 * Ключи сообщений инфраструктуры приложения: общие отказы HTTP, вход, настройки.
 */
public final class BootstrapMessages {

    /** Запрос не разобран: тело или параметр не того формата. */
    public static final String BAD_REQUEST = "bootstrap.problem.bad-request";
    /** Метод, тип тела или адрес запроса здесь не обслуживаются: метод и путь запроса. */
    public static final String REQUEST_NOT_SUPPORTED = "bootstrap.problem.request-not-supported";
    /** Неожиданный сбой сервера: подробностей клиенту нет, они в журнале. */
    public static final String INTERNAL_ERROR = "bootstrap.problem.internal-error";
    /** Модель изменил параллельный писатель — нужно перечитать состояние и повторить. */
    public static final String CONCURRENT_MODIFICATION = "bootstrap.problem.concurrent-modification";
    /** Загружаемый файл больше допустимого размера. */
    public static final String FILE_TOO_LARGE = "bootstrap.problem.file-too-large";
    /** Изменение противоречит данным, записанным параллельно, — нарушено ограничение базы. */
    public static final String INTEGRITY_CONFLICT = "bootstrap.problem.integrity-conflict";
    /** Заголовок ответа об отказе по конфликту. */
    public static final String TITLE_CONFLICT = "bootstrap.problem.title-conflict";
    /** Запрос пришёл без аутентификации. */
    public static final String NOT_AUTHENTICATED = "bootstrap.security.not-authenticated";

    private BootstrapMessages() {
    }
}
