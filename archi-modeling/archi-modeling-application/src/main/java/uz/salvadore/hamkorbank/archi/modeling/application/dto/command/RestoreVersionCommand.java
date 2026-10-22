package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Откатить модель к версии; откат фиксируется новой версией.
 *
 * @param comment комментарий новой версии; {@code null} и пустой — комментарий по умолчанию
 */
public record RestoreVersionCommand(UUID modelId, long versionNo, @Nullable String comment) {
}
