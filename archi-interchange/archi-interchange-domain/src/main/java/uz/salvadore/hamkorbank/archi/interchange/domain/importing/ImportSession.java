package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.CorruptDocumentException;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Одна попытка внести файл в систему — агрегат (spec/domain/interchange/aggregates.yaml#ImportSession).
 *
 * <p>Автомат INV-IXC-002: {@code RECEIVED → PARSED → VALIDATED → APPLIED}, отказ
 * из любого нетерминального. Применить можно только проверенное; {@code APPLIED}
 * и {@code REJECTED} терминальны, повторное применение отклоняется.
 *
 * <p>INV-IXC-007: строгость меняет порог отказа, но не отношение к повреждённым
 * данным. Повреждённый файл отклоняется на разборе в любом режиме; находка
 * {@code ERROR} на проверке отклоняет только в строгом.
 */
public final class ImportSession {

    private final ImportSessionId id;
    private final WorkspaceId workspaceId;
    private final Optional<ModelId> targetModelId;
    private final String sourceName;
    private final ContentHash sourceHash;
    private final long sourceSize;
    private final String idempotencyKey;
    private final boolean strictMode;
    private final String startedBy;
    private final Instant startedAt;

    private ImportStatus status = ImportStatus.RECEIVED;
    private ModelDocument document;
    private final List<ImportFinding> findings = new ArrayList<>();
    private Instant finishedAt;
    private final List<ImportApplied> events = new ArrayList<>();

    private ImportSession(ImportSessionId id, ImportRequest request, Instant startedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.workspaceId = request.workspaceId();
        this.targetModelId = request.targetModelId();
        this.sourceName = request.sourceName();
        this.sourceHash = request.sourceHash();
        this.sourceSize = request.sourceSize();
        this.idempotencyKey = request.idempotencyKey();
        this.strictMode = request.strictMode();
        this.startedBy = request.startedBy();
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }

    /** Файл принят к рассмотрению: {@code RECEIVED}, ещё ничего не прочитано. */
    public static ImportSession receive(ImportSessionId id, ImportRequest request, Instant now) {
        return new ImportSession(id, Objects.requireNonNull(request, "request"), now);
    }

    /**
     * Разбор: {@code RECEIVED → PARSED}. Повреждённые данные (FR-50) переводят сессию
     * в {@code REJECTED} сразу, в любом режиме — со всеми дефектами в отчёте.
     */
    public void parse(ArchiDocumentReader reader, InputStream content, Supplier<FindingId> findingIds, Instant now) {
        require(ImportStatus.RECEIVED, "parse");
        try {
            document = reader.read(content);
            status = ImportStatus.PARSED;
        } catch (CorruptDocumentException corrupt) {
            corrupt.defects().forEach(defect -> findings.add(ImportFinding.of(findingIds.get(), defect)));
            finish(ImportStatus.REJECTED, now);
        }
    }

    /**
     * Проверка методологии: {@code PARSED → VALIDATED}. Находки попадают в отчёт;
     * в строгом режиме хотя бы одна {@link Severity#ERROR} даёт {@code REJECTED} (INV-IXC-007).
     */
    public void validate(List<ImportFinding> methodologyFindings, Instant now) {
        require(ImportStatus.PARSED, "validate");
        findings.addAll(methodologyFindings);
        boolean hasErrors = methodologyFindings.stream().anyMatch(f -> f.severity() == Severity.ERROR);
        if (strictMode && hasErrors) {
            finish(ImportStatus.REJECTED, now);
        } else {
            status = ImportStatus.VALIDATED;
        }
    }

    /** Отказ из любого нетерминального состояния — с причинами в отчёте. */
    public void reject(List<ImportFinding> reasons, Instant now) {
        if (status.terminal()) {
            throw new IllegalImportTransitionException(status, "RejectImport");
        }
        findings.addAll(reasons);
        finish(ImportStatus.REJECTED, now);
    }

    /**
     * {@code VALIDATED → APPLIED}: документ записан в модель. Только из {@code VALIDATED} —
     * разбор без проверки не даёт права писать в модель, а повтор терминальной сессии
     * удвоил бы модель (INV-IXC-002).
     */
    public ImportApplied apply(ModelId modelId, long versionNo, int opaqueObjectCount, Instant now) {
        require(ImportStatus.VALIDATED, "ApplyImport");
        finish(ImportStatus.APPLIED, now);
        ImportApplied applied = new ImportApplied(id, workspaceId, modelId, versionNo, sourceName, sourceHash,
                strictMode, findingCounts(), opaqueObjectCount, now);
        events.add(applied);
        return applied;
    }

    /** События, накопленные с последнего вызова; публикует их слой приложения после коммита. */
    public List<ImportApplied> pullEvents() {
        List<ImportApplied> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    public Map<Severity, Integer> findingCounts() {
        Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            counts.put(severity, 0);
        }
        findings.forEach(f -> counts.merge(f.severity(), 1, Integer::sum));
        return counts;
    }

    private void require(ImportStatus expected, String command) {
        if (status != expected) {
            throw new IllegalImportTransitionException(status, command);
        }
    }

    private void finish(ImportStatus terminal, Instant now) {
        status = terminal;
        finishedAt = Objects.requireNonNull(now, "now");
    }

    public ImportSessionId id() {
        return id;
    }

    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    public Optional<ModelId> targetModelId() {
        return targetModelId;
    }

    public String sourceName() {
        return sourceName;
    }

    public ContentHash sourceHash() {
        return sourceHash;
    }

    public long sourceSize() {
        return sourceSize;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public boolean strictMode() {
        return strictMode;
    }

    public ImportStatus status() {
        return status;
    }

    /** Документ есть в {@code PARSED}, {@code VALIDATED} и {@code APPLIED}. */
    public Optional<ModelDocument> document() {
        return Optional.ofNullable(document);
    }

    public List<ImportFinding> findings() {
        return List.copyOf(findings);
    }

    public String startedBy() {
        return startedBy;
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** Есть у терминальных сессий. */
    public Optional<Instant> finishedAt() {
        return Optional.ofNullable(finishedAt);
    }
}
