package uz.salvadore.hamkorbank.archi.interchange.application.dto.result;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Отчёт об изменениях по ссылке до применения.
 *
 * @param refKind       {@code BRANCH}, {@code TAG} или {@code COMMIT}
 * @param baseVersionNo последняя версия модели; у модели без версий {@code null}
 * @param baseHash      отпечаток текущего содержимого — его применение и подтверждает
 * @param applicable    применение пройдёт разбор и строгий режим пространства
 */
public record GitPullPreviewResponse(UUID modelId, String ref, String refKind, String commitSha,
                                     @Nullable Long baseVersionNo,
                                     String baseHash, String targetHash, boolean unchanged, boolean applicable,
                                     List<GitChangeCountResponse> summary, List<GitChangeResponse> changes,
                                     List<ImportFindingResponse> findings) {

    public GitPullPreviewResponse {
        summary = List.copyOf(summary);
        changes = List.copyOf(changes);
        findings = List.copyOf(findings);
    }
}
