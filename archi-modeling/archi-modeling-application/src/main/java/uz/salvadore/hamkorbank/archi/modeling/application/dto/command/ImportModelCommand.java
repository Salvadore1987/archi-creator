package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.ModelRows;

/**
 * Новая модель из импортированного документа.
 *
 * @param rows    содержимое строками, разложенными из документа на стороне вызывающего
 * @param comment комментарий первой версии
 */
public record ImportModelCommand(UUID workspaceId, ModelRows rows, @Nullable String comment) {

    public ImportModelCommand {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(rows, "rows");
    }
}
