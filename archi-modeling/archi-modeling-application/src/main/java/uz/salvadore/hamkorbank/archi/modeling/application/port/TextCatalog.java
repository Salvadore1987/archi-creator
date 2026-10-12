package uz.salvadore.hamkorbank.archi.modeling.application.port;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;

/**
 * Текст, который сервер сохраняет как данные: комментарий системной версии,
 * находка отчёта. Собирается на языке запроса, в котором сценарий выполняется,
 * и дальше живёт как текст — как комментарий, введённый человеком.
 */
public interface TextCatalog {

    String text(Message message);
}
