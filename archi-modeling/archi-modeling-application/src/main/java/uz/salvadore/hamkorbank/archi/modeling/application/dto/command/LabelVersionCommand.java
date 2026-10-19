package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/**
 * Поставить или снять метку релиза.
 *
 * @param label метка; {@code null} и пустая — снять
 */
public record LabelVersionCommand(UUID modelId, long versionNo, String label) {
}
