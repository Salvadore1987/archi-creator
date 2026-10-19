package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

/**
 * Итог сохранения.
 *
 * @param created ложно, если изменений не было или это повтор ключа
 */
public record SaveVersionResponse(VersionResponse version, boolean created) {
}
