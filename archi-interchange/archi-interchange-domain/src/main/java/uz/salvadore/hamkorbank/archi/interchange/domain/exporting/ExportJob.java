package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ExportJobId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Одна выгрузка модели или представления — агрегат
 * (spec/domain/interchange/aggregates.yaml#ExportJob).
 *
 * <p>Автомат: {@code REQUESTED → RENDERED → DELIVERED}, сбой — из любого нетерминального.
 * <ul>
 *   <li>INV-IXC-006: в {@code RENDERED} и {@code DELIVERED} отчёт о потерях есть всегда;
 *       для форматов без потерь он обязан быть пуст.</li>
 *   <li>INV-IXC-008: результат построен по версии, зафиксированной при запросе;
 *       артефакт другой версии не принимается.</li>
 * </ul>
 */
public final class ExportJob {

    private final ExportJobId id;
    private final WorkspaceId workspaceId;
    private final ModelId modelId;
    private final Optional<ViewId> viewId;
    private final ExportFormat format;
    private final ExportOptions options;
    private final long sourceVersionNo;
    private final String requestedBy;
    private final Instant requestedAt;

    private ExportStatus status = ExportStatus.REQUESTED;
    private Artifact artifact;
    private LossReport lossReport;
    private String failureReason;

    private ExportJob(ExportJobId id, WorkspaceId workspaceId, ModelId modelId, Optional<ViewId> viewId,
                      ExportFormat format, ExportOptions options, long sourceVersionNo, String requestedBy,
                      Instant requestedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.viewId = Objects.requireNonNull(viewId, "viewId");
        this.format = Objects.requireNonNull(format, "format");
        this.options = Objects.requireNonNull(options, "options");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        if (sourceVersionNo <= 0) {
            throw new IllegalArgumentException("номер версии обязан быть положительным");
        }
        this.sourceVersionNo = sourceVersionNo;
    }

    /** Версия фиксируется здесь, при запросе, а не при рендере (INV-IXC-008). */
    public static ExportJob request(ExportJobId id, WorkspaceId workspaceId, ModelId modelId, Optional<ViewId> viewId,
                                    ExportFormat format, ExportOptions options, long sourceVersionNo,
                                    String requestedBy, Instant requestedAt) {
        return new ExportJob(id, workspaceId, modelId, viewId, format, options, sourceVersionNo, requestedBy,
                requestedAt);
    }

    /**
     * {@code REQUESTED → RENDERED}.
     *
     * @param renderedFromVersionNo версия, из которой собран артефакт
     * @throws ExportRuleViolationException артефакт другой версии (INV-IXC-008), нет отчёта
     *         или непустой отчёт у формата без потерь (INV-IXC-006)
     */
    public void render(long renderedFromVersionNo, Artifact artifact, LossReport lossReport) {
        require(ExportStatus.REQUESTED, "render");
        Objects.requireNonNull(artifact, "artifact");
        if (renderedFromVersionNo != sourceVersionNo) {
            throw new ExportRuleViolationException("INV-IXC-008", "задание зафиксировало версию "
                    + sourceVersionNo + ", а артефакт собран из версии " + renderedFromVersionNo);
        }
        if (lossReport == null) {
            throw new ExportRuleViolationException("INV-IXC-006", "выгрузка без отчёта о потерях — тихая потеря данных");
        }
        if (format.lossless() && !lossReport.empty()) {
            throw new ExportRuleViolationException("INV-IXC-006", format + " обещает round-trip без потерь, а отчёт "
                    + "перечисляет " + lossReport.entries().size() + " потерь");
        }
        this.artifact = artifact;
        this.lossReport = lossReport;
        this.status = ExportStatus.RENDERED;
    }

    /** {@code RENDERED → DELIVERED}. */
    public void deliver() {
        require(ExportStatus.RENDERED, "deliver");
        status = ExportStatus.DELIVERED;
    }

    /** Сбой из любого нетерминального состояния. */
    public void fail(String reason) {
        if (status == ExportStatus.DELIVERED || status == ExportStatus.FAILED) {
            throw new IllegalStateException("fail недопустим из состояния " + status);
        }
        failureReason = Objects.requireNonNull(reason, "reason");
        status = ExportStatus.FAILED;
    }

    private void require(ExportStatus expected, String command) {
        if (status != expected) {
            throw new IllegalStateException(command + " недопустим из состояния " + status);
        }
    }

    public ExportJobId id() {
        return id;
    }

    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    public ModelId modelId() {
        return modelId;
    }

    public Optional<ViewId> viewId() {
        return viewId;
    }

    public ExportFormat format() {
        return format;
    }

    public ExportOptions options() {
        return options;
    }

    public long sourceVersionNo() {
        return sourceVersionNo;
    }

    public ExportStatus status() {
        return status;
    }

    public Optional<Artifact> artifact() {
        return Optional.ofNullable(artifact);
    }

    public Optional<LossReport> lossReport() {
        return Optional.ofNullable(lossReport);
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    public String requestedBy() {
        return requestedBy;
    }

    public Instant requestedAt() {
        return requestedAt;
    }
}
