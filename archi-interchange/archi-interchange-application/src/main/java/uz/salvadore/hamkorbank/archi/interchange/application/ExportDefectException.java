package uz.salvadore.hamkorbank.archi.interchange.application;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Выгрузка не прошла собственную проверку сервера — дефект писателя, а не ошибка
 * запроса. Клиент получает код и текст по ключу; подробность нарушения — только для
 * журнала сервера: в ней пути схемы и фрагменты документа, которые клиенту ни к чему.
 */
public class ExportDefectException extends RuntimeException {

    private final String code;
    private final Message reason;
    private final String detail;

    public ExportDefectException(String code, Message reason, String detail) {
        super(code + ": " + detail);
        this.code = Objects.requireNonNull(code, "code");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    /** Код ошибки для клиента API. */
    public String code() {
        return code;
    }

    /** Сообщение с ключом, которое переводится на язык запроса. */
    public Message reason() {
        return reason;
    }

    /** Подробность нарушения для журнала сервера; клиенту не отдаётся. */
    public String detail() {
        return detail;
    }
}
