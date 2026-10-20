package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.UUID;

/**
 * Новая модель из импорта.
 *
 * @param versionNo     её первая версия
 * @param opaqueObjects сколько объектов редактор не правит: их типы ещё не поддержаны
 */
public record ImportModelResponse(UUID modelId, long versionNo, int opaqueObjects) {
}
