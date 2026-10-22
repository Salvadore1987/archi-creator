package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import org.jspecify.annotations.Nullable;

/**
 * Завести первое пространство на пустой базе.
 *
 * @param name имя, заданное установкой; {@code null} — текст ресурса на текущем языке
 */
public record EnsureDefaultWorkspaceCommand(@Nullable String name) {
}
