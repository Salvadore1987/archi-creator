package uz.salvadore.hamkorbank.archi.interchange.application.dto.result;

import java.util.UUID;

/**
 * Состояние модели в Git.
 *
 * @param state             {@code SYNCED}, {@code PENDING} или {@code DIVERGED}
 * @param pending           сколько версий ждёт публикации
 * @param versionNo         последняя опубликованная версия; {@code null} — публикаций не было
 * @param gitSha            её коммит; {@code null} — публикаций не было
 * @param divergedVersionNo версия, вставшая на расхождении; {@code null} — расхождения нет
 */
public record ModelGitStatusResponse(UUID modelId, String state, int pending, Long versionNo, String gitSha,
                                     Long divergedVersionNo) {
}
