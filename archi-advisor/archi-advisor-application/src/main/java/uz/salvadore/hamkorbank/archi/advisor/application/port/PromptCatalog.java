package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.Locale;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;

/**
 * Системные промпты функций — текст на естественном языке, поэтому живёт в ресурсах,
 * а не в коде.
 */
public interface PromptCatalog {

    /** Системный промпт функции; язык ответа — язык запроса. */
    String system(AdvisorFunction function, Locale answerLanguage);

    /** Описание инструмента для модели: когда и как его вызывать. */
    String toolDescription(String toolName);
}
