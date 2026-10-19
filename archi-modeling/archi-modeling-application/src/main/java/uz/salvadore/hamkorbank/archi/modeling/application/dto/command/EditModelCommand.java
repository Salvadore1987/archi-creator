package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/**
 * Правка имени и описания модели.
 *
 * @param name          новое имя; {@code null} — не менять
 * @param documentation новое описание; {@code null} — не менять, пустое — снять
 */
public record EditModelCommand(UUID modelId, String name, String documentation) {
}
