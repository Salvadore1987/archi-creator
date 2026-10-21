package uz.salvadore.hamkorbank.archi.interchange.application.dto.result;

import java.util.List;
import java.util.UUID;

/**
 * Отчёт слияния до применения: конфликты с выборами, каскад, изменения и отметки для холста.
 *
 * @param refKind       {@code BRANCH}, {@code TAG} или {@code COMMIT}
 * @param baseVersionNo последняя версия модели; у модели без версий {@code null}
 * @param baseHash      отпечаток текущего содержимого — его применение и подтверждает
 * @param targetHash    отпечаток результата с выборами отчёта
 * @param applicable    конфликты разрешены, разбор и строгий режим пройдут
 */
public record GitMergePreviewResponse(UUID modelId, String ref, String refKind, String commitSha,
                                      String mergeBaseSha, Long baseVersionNo, String baseHash, String targetHash,
                                      boolean unchanged, boolean applicable, List<Conflict> conflicts,
                                      List<Cascade> cascade, List<GitChangeCountResponse> summary,
                                      List<GitChangeResponse> changes, List<Mark> marks, List<View> views,
                                      List<ImportFindingResponse> findings) {

    public GitMergePreviewResponse {
        conflicts = List.copyOf(conflicts);
        cascade = List.copyOf(cascade);
        summary = List.copyOf(summary);
        changes = List.copyOf(changes);
        marks = List.copyOf(marks);
        views = List.copyOf(views);
        findings = List.copyOf(findings);
    }

    /**
     * Конфликт: поле или удаление против правки, значения трёх состояний и выбор.
     *
     * @param id         адрес конфликта {@code <archiId>:<поле>}
     * @param resolution {@code OURS}, {@code THEIRS}; {@code null} — выбора нет
     */
    public record Conflict(String id, String kind, String archiId, String type, String name, String field,
                           String base, String ours, String theirs, String resolution) {
    }

    /**
     * Удаление как следствие разрешения.
     *
     * @param viewArchiId представление узла или ребра; {@code null} — не на представлении
     */
    public record Cascade(String kind, String archiId, String name, String reason, String viewArchiId) {
    }

    /** Отметка объекта для холста: {@code ADDED}, {@code REMOVED} или {@code CHANGED}. */
    public record Mark(String archiId, String mark) {
    }

    /**
     * Узел, которого в текущем содержимом нет, — с абсолютной геометрией.
     *
     * @param elementArchiId элемент модели узла; {@code null} — узел без элемента
     * @param type           тип без префикса {@code archimate:}; {@code null} — неизвестен
     */
    public record AddedNode(String archiId, String elementArchiId, String type, String name, int x, int y,
                            int width, int height) {
    }

    /** Узлы, которые слияние добавит в представление. */
    public record View(String archiId, List<AddedNode> addedNodes) {

        public View {
            addedNodes = List.copyOf(addedNodes);
        }
    }
}
