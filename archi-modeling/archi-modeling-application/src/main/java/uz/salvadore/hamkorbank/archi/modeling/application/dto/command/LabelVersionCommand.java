package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Поставить или снять метку релиза.
 *
 * @param label метка; {@code null} и пустая — снять
 */
public record LabelVersionCommand(UUID modelId, long versionNo, @Nullable String label) {
}
