package uz.salvadore.hamkorbank.archi.bootstrap.web;

import org.springframework.http.HttpStatus;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.bootstrap.wiring.ErrorCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Отказ формы запроса — тем же кодом и текстом, каким контекст обработчика отказывает
 * в неразборчивом вводе сам: клиент ветвится по одному коду, где бы ни сработала проверка.
 */
enum FormRefusal {

    MODELING("modeling", HttpStatus.UNPROCESSABLE_CONTENT, ModelingCodes.INVALID_INPUT,
            ModelingMessages.FIELD_MISSING, ModelingMessages.FIELDS_MISSING),
    INTERCHANGE("interchange", HttpStatus.UNPROCESSABLE_CONTENT, InterchangeCodes.INVALID_REQUEST,
            InterchangeMessages.REQUEST_FIELD_MISSING, InterchangeMessages.REQUEST_FIELDS_MISSING),
    ADVISOR("advisor", HttpStatus.UNPROCESSABLE_CONTENT, AdvisorCodes.INVALID_INPUT,
            AdvisorMessages.FIELD_REQUIRED, AdvisorMessages.FIELDS_REQUIRED),
    OTHER("", HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST,
            BootstrapMessages.MISSING_FIELDS, BootstrapMessages.MISSING_FIELDS);

    private static final String ROOT = "uz.salvadore.hamkorbank.archi.";

    private final String context;
    private final HttpStatus status;
    private final String code;
    private final String oneField;
    private final String manyFields;

    FormRefusal(String context, HttpStatus status, String code, String oneField, String manyFields) {
        this.context = context;
        this.status = status;
        this.code = code;
        this.oneField = oneField;
        this.manyFields = manyFields;
    }

    /** Контекст — по пакету класса обработчика. */
    static FormRefusal of(Class<?> handler) {
        for (FormRefusal refusal : values()) {
            if (!refusal.context.isEmpty() && handler.getPackageName().startsWith(ROOT + refusal.context + ".")) {
                return refusal;
            }
        }
        return OTHER;
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }

    /** Ключ текста: одно поле — с его путём, несколько — перечнем. */
    String key(int fields) {
        return fields == 1 ? oneField : manyFields;
    }
}
