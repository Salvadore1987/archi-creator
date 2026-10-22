package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Правка имени и описания модели.
 *
 * @param name          новое имя; {@code null} — не менять
 * @param documentation новое описание; {@code null} — не менять, пустое — снять
 */
public record EditModelCommand(UUID modelId, @Nullable String name, @Nullable String documentation) {
}
