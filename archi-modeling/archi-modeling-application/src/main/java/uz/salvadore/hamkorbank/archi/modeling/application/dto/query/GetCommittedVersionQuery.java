package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.UUID;

/** Зафиксированная версия со снимком — для публикации в Git. */
public record GetCommittedVersionQuery(UUID modelId, long versionNo) {
}
