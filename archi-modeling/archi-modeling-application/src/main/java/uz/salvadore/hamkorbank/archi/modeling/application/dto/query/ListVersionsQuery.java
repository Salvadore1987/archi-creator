package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Страница истории версий модели.
 *
 * @param size   сколько версий отдать; не задано — по умолчанию сценария
 * @param before номер версии, с которой продолжить к старым; не задано — с последней
 */
public record ListVersionsQuery(UUID modelId, @Nullable Integer size, @Nullable Long before) {
}
