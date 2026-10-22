package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.PropertyValue;

/**
 * Правка элемента.
 *
 * @param name          новое имя; {@code null} — не менять
 * @param documentation новое описание; {@code null} — не менять
 * @param properties    свойства целиком в нужном порядке; {@code null} — не менять
 */
public record EditElementCommand(UUID elementId, @Nullable String name, @Nullable String documentation,
                                 @Nullable List<PropertyValue> properties) {

    public EditElementCommand {
        properties = properties == null ? null : List.copyOf(properties);
    }
}
