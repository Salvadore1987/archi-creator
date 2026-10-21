package uz.salvadore.hamkorbank.archi.advisor.application.port;

/** Открытый поток ответа языковой модели. */
public interface LlmStream {

    /**
     * Читать поток до конца, отдавая события слушателю. Блокирует поток выполнения.
     * Обрыв внешнего API — {@link LlmUnavailableException}; израсходованное к этому
     * моменту слушатель уже получил через {@link LlmListener#usage}.
     */
    void consume(LlmListener listener);

    /** Прервать поток: новый запрос того же автора отменяет предыдущий. */
    void cancel();
}
