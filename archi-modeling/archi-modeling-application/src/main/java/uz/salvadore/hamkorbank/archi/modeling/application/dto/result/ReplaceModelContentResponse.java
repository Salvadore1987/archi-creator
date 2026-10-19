package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

/**
 * Итог замены содержимого.
 *
 * @param created       ложно — содержимое совпало с текущим, версия прежняя
 * @param opaqueObjects сколько объектов нового содержимого редактор не правит
 */
public record ReplaceModelContentResponse(long versionNo, boolean created, int opaqueObjects) {
}
