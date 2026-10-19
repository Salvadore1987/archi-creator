package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/** Мягко удалить модель: данные и история остаются, блокировка снимается. */
public record DeleteModelCommand(UUID modelId) {
}
