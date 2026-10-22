package uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * DTO interchange по контракту OpenAPI. Отсутствующее значение — {@code null},
 * а не {@code Optional}: так его видит JSON. Bean Validation проверяет только форму
 * запроса; правила значений — сценарий и домен.
 */
public final class Dtos {

    private Dtos() {
    }

    /** Находка импорта: строка XML и {@code archi_id}, если они известны. */
    public record FindingDto(String severity, String code, String message, @Nullable String archiId,
                             @Nullable Integer xmlLine) {
    }

    /** Итог импорта: сессия, модель и отчёт — у принятого и у отклонённого. */
    public record ImportResult(UUID sessionId, String status, @Nullable UUID modelId, @Nullable Long versionNo,
                               boolean replayed,
                               List<FindingDto> findings) {
    }

    /** Настройка импорта пространства: строгий режим отклоняет файл с находками уровня {@code ERROR}. */
    public record ImportSettings(boolean strictImport) {
    }

    /** Субъект Keycloak и автор коммита от его имени. */
    public record GitAuthorDto(String subject, String name, String email) {
    }

    /** Настройка привязки: ссылка на секрет, а не токен. */
    public record GitBindingSettingsDto(@Nullable String repoUrl, @Nullable String branch, @Nullable String tokenRef,
                                        @Nullable List<@NotNull GitAuthorDto> authors) {
    }

    /** Привязка пространства к репозиторию: состояние, последний известный коммит ветки и время проверки. */
    public record GitBindingDto(String repoUrl, String branch, String tokenRef, String status,
                                @Nullable String lastCommitSha, @Nullable Instant lastCheckedAt,
                                List<GitAuthorDto> authors) {
    }

    /** Ссылка, по которой строится отчёт: ветка, тег или коммит. */
    public record GitPullPreviewRequestDto(@Nullable String ref) {
    }

    /** Подтверждение применения: коммит и отпечаток содержимого — из отчёта. */
    public record GitPullRequestDto(@Nullable String ref, @Nullable String commitSha, @Nullable String baseHash,
                                    @Nullable String comment) {
    }

    /** Сколько объектов одного вида добавлено, удалено и изменено. */
    public record GitChangeCountDto(String kind, int added, int removed, int changed) {
    }

    /** Объект отчёта: вид, что с ним, тип ArchiMate, имя и изменённые поля. */
    public record GitChangeDto(String kind, String change, String archiId, @Nullable String type, String name,
                               List<String> fields) {
    }

    /** Отчёт об изменениях до применения. */
    public record GitPullPreviewDto(UUID modelId, String ref, String refKind, String commitSha,
                                    @Nullable Long baseVersionNo,
                                    String baseHash, String targetHash, boolean unchanged, boolean applicable,
                                    List<GitChangeCountDto> summary, List<GitChangeDto> changes,
                                    List<FindingDto> findings) {
    }

    /** Итог применения: сессия, версия и признак, создана ли она. */
    public record GitPullResultDto(UUID sessionId, String status, UUID modelId, long versionNo, boolean created,
                                   String commitSha, List<FindingDto> findings) {
    }

    /** Ссылка на предложение и выборы по уже показанным конфликтам: адрес — OURS или THEIRS. */
    public record GitMergePreviewRequestDto(@Nullable String ref, @Nullable Map<String, String> resolutions) {
    }

    /** Подтверждение слияния: оба коммита и отпечаток — из отчёта, выбор по каждому конфликту. */
    public record GitMergeRequestDto(@Nullable String ref, @Nullable String commitSha, @Nullable String mergeBaseSha,
                                     @Nullable String baseHash, @Nullable Map<String, String> resolutions,
                                     @Nullable String comment) {
    }

    /** Конфликт: поле или удаление против правки, значения трёх состояний и выбор. */
    public record GitMergeConflictDto(String id, String kind, String archiId, @Nullable String type, String name,
                                      String field, @Nullable String base, @Nullable String ours,
                                      @Nullable String theirs, @Nullable String resolution) {
    }

    /** Удаление как следствие разрешения: что, почему, на каком представлении. */
    public record GitMergeCascadeDto(String kind, String archiId, String name, String reason,
                                     @Nullable String viewArchiId) {
    }

    /** Отметка объекта для холста. */
    public record GitMergeMarkDto(String archiId, String mark) {
    }

    /** Узел, которого в текущем содержимом нет: абсолютная геометрия. */
    public record GitMergeAddedNodeDto(String archiId, @Nullable String elementArchiId, @Nullable String type,
                                       String name, int x, int y, int width, int height) {
    }

    /** Узлы, которые слияние добавит в представление, — для контуров на холсте. */
    public record GitMergeViewDto(String archiId, List<GitMergeAddedNodeDto> addedNodes) {
    }

    /** Отчёт слияния до применения. */
    public record GitMergePreviewDto(UUID modelId, String ref, String refKind, String commitSha, String mergeBaseSha,
                                     @Nullable Long baseVersionNo, String baseHash, String targetHash,
                                     boolean unchanged,
                                     boolean applicable, List<GitMergeConflictDto> conflicts,
                                     List<GitMergeCascadeDto> cascade, List<GitChangeCountDto> summary,
                                     List<GitChangeDto> changes, List<GitMergeMarkDto> marks,
                                     List<GitMergeViewDto> views, List<FindingDto> findings) {
    }

    /** Итог слияния: сессия, версия и признак, создана ли она. */
    public record GitMergeResultDto(UUID sessionId, String status, UUID modelId, long versionNo, boolean created,
                                    String commitSha, List<FindingDto> findings) {
    }

    /** Состояние модели в Git: опубликовано всё, ждёт или встало на расхождении. */
    public record ModelGitStatusDto(UUID modelId, String state, int pending, @Nullable Long versionNo,
                                    @Nullable String gitSha, @Nullable Long divergedVersionNo) {
    }
}
