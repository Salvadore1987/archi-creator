package uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO interchange по контракту OpenAPI. Отсутствующее значение — {@code null},
 * а не {@code Optional}: так его видит JSON.
 */
public final class Dtos {

    private Dtos() {
    }

    /** Находка импорта: строка XML и {@code archi_id}, если они известны. */
    public record FindingDto(String severity, String code, String message, String archiId, Integer xmlLine) {
    }

    /** Итог импорта: сессия, модель и отчёт — у принятого и у отклонённого. */
    public record ImportResult(UUID sessionId, String status, UUID modelId, Long versionNo, boolean replayed,
                               List<FindingDto> findings) {
    }

    public record ImportSettings(boolean strictImport) {
    }

    /** Субъект Keycloak и автор коммита от его имени. */
    public record GitAuthorDto(String subject, String name, String email) {
    }

    /** Настройка привязки: ссылка на секрет, а не токен. */
    public record GitBindingSettingsDto(String repoUrl, String branch, String tokenRef, List<GitAuthorDto> authors) {
    }

    public record GitBindingDto(String repoUrl, String branch, String tokenRef, String status, String lastCommitSha,
                                Instant lastCheckedAt, List<GitAuthorDto> authors) {
    }

    /** Ссылка, по которой строится отчёт: ветка, тег или коммит. */
    public record GitPullPreviewRequestDto(String ref) {
    }

    /** Подтверждение применения: коммит и отпечаток содержимого — из отчёта. */
    public record GitPullRequestDto(String ref, String commitSha, String baseHash, String comment) {
    }

    public record GitChangeCountDto(String kind, int added, int removed, int changed) {
    }

    /** Объект отчёта: вид, что с ним, тип ArchiMate, имя и изменённые поля. */
    public record GitChangeDto(String kind, String change, String archiId, String type, String name,
                               List<String> fields) {
    }

    /** Отчёт об изменениях до применения. */
    public record GitPullPreviewDto(UUID modelId, String ref, String refKind, String commitSha, Long baseVersionNo,
                                    String baseHash, String targetHash, boolean unchanged, boolean applicable,
                                    List<GitChangeCountDto> summary, List<GitChangeDto> changes,
                                    List<FindingDto> findings) {
    }

    /** Итог применения: сессия, версия и признак, создана ли она. */
    public record GitPullResultDto(UUID sessionId, String status, UUID modelId, long versionNo, boolean created,
                                   String commitSha, List<FindingDto> findings) {
    }

    /** Состояние модели в Git: опубликовано всё, ждёт или встало на расхождении. */
    public record ModelGitStatusDto(UUID modelId, String state, int pending, Long versionNo, String gitSha,
                                    Long divergedVersionNo) {
    }
}
