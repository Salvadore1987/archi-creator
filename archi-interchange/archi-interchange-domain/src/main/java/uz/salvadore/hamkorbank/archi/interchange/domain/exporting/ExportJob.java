package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.AggregateRoot;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ExportJobId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Одна выгрузка модели или представления — агрегат.
 *
 * <p>Автомат: {@code REQUESTED → RENDERED → DELIVERED}, сбой — из любого нетерминального.
 * <ul>
 *   <li>в {@code RENDERED} и {@code DELIVERED} отчёт о потерях есть всегда;
 *       для форматов без потерь он обязан быть пуст.</li>
 *   <li>результат построен по версии, зафиксированной при запросе;
 *       артефакт другой версии не принимается.</li>
 * </ul>
 */
public final class ExportJob extends AggregateRoot<ExportJobId> {

    private final WorkspaceId workspaceId;
    private final ModelId modelId;
    private final ViewId viewId;
    private final ExportFormat format;
    private final ExportOptions options;
    private final long sourceVersionNo;
    private final String requestedBy;
    private final Instant requestedAt;

    private ExportStatus status = ExportStatus.REQUESTED;
    private Artifact artifact;
    private LossReport lossReport;
    private String failureReason;
    private final List<DomainEvent> events = new ArrayList<>();

    private ExportJob(ExportJobId id, WorkspaceId workspaceId, ModelId modelId, ViewId viewId,
                      ExportFormat format, ExportOptions options, long sourceVersionNo, String requestedBy,
                      Instant requestedAt) {
        super(id);
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.viewId = viewId;
        this.format = Objects.requireNonNull(format, "format");
        this.options = Objects.requireNonNull(options, "options");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        if (sourceVersionNo <= 0) {
            throw new InvalidValueException(InterchangeMessages.EXPORT_VERSION_POSITIVE);
        }
        this.sourceVersionNo = sourceVersionNo;
    }

    /** Версия фиксируется здесь, при запросе, а не при рендере; {@code viewId} — {@code null} у выгрузки модели. */
    public static ExportJob request(ExportJobId id, WorkspaceId workspaceId, ModelId modelId, ViewId viewId,
                                    ExportFormat format, ExportOptions options, long sourceVersionNo,
                                    String requestedBy, Instant requestedAt) {
        return new ExportJob(id, workspaceId, modelId, viewId, format, options, sourceVersionNo, requestedBy,
                requestedAt);
    }

    /**
     * {@code REQUESTED → RENDERED}.
     *
     * @param renderedFromVersionNo версия, из которой собран артефакт
     * @throws ExportRuleViolationException артефакт другой версии, нет отчёта
     *         или непустой отчёт у формата без потерь
     */
    public void render(long renderedFromVersionNo, Artifact artifact, LossReport lossReport) {
        require(ExportStatus.REQUESTED, Message.of(InterchangeMessages.COMMAND_RENDER_EXPORT));
        Objects.requireNonNull(artifact, "artifact");
        if (renderedFromVersionNo != sourceVersionNo) {
            throw new ExportRuleViolationException(InterchangeCodes.EXPORT_VERSION,
                    Message.of(InterchangeMessages.EXPORT_VERSION_MISMATCH, sourceVersionNo, renderedFromVersionNo));
        }
        if (lossReport == null) {
            throw new ExportRuleViolationException(InterchangeCodes.LOSS_REPORT,
                    Message.of(InterchangeMessages.EXPORT_LOSS_REPORT_MISSING));
        }
        if (format.lossless() && !lossReport.empty()) {
            throw new ExportRuleViolationException(InterchangeCodes.LOSS_REPORT,
                    Message.of(InterchangeMessages.EXPORT_LOSSLESS_WITH_LOSSES, format.label(),
                               lossReport.entries().size()));
        }
        this.artifact = artifact;
        this.lossReport = lossReport;
        this.status = ExportStatus.RENDERED;
    }

    /** {@code RENDERED → DELIVERED}: выгрузка отдана, событие о ней — в {@link #pullEvents()}. */
    public void deliver(Instant now) {
        require(ExportStatus.RENDERED, Message.of(InterchangeMessages.COMMAND_DELIVER_EXPORT));
        status = ExportStatus.DELIVERED;
        events.add(new ExportCompletedEvent(id(), workspaceId, modelId, viewId, format, sourceVersionNo,
                artifact.hash(), artifact.size(), lossReport.entries().size(), now));
    }

    /** События, накопленные с последнего вызова; публикует их слой приложения после коммита. */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    /** Сбой из любого нетерминального состояния. */
    public void fail(String reason) {
        if (status == ExportStatus.DELIVERED || status == ExportStatus.FAILED) {
            throw new IllegalExportTransitionException(Message.of(InterchangeMessages.EXPORT_TRANSITION,
                    Message.of(InterchangeMessages.COMMAND_FAIL_EXPORT), status.label()));
        }
        failureReason = Objects.requireNonNull(reason, "reason");
        status = ExportStatus.FAILED;
    }

    private void require(ExportStatus expected, Message command) {
        if (status != expected) {
            throw new IllegalExportTransitionException(
                    Message.of(InterchangeMessages.EXPORT_TRANSITION, command, status.label()));
        }
    }


    /** Пространство, в котором лежит модель. */
    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    /** Выгружаемая модель. */
    public ModelId modelId() {
        return modelId;
    }

    /** Представление — у выгрузки картинки; у выгрузки модели {@code null}. */
    public ViewId viewId() {
        return viewId;
    }

    /** Формат выгрузки. */
    public ExportFormat format() {
        return format;
    }

    /** Параметры выгрузки. */
    public ExportOptions options() {
        return options;
    }

    /** Номер версии, по которой строится результат; фиксируется при запросе. */
    public long sourceVersionNo() {
        return sourceVersionNo;
    }

    /** Состояние выгрузки. */
    public ExportStatus status() {
        return status;
    }

    /** Готовый файл; есть после построения. */
    public Optional<Artifact> artifact() {
        return Optional.ofNullable(artifact);
    }

    /** Отчёт о потерях; есть после построения. */
    public Optional<LossReport> lossReport() {
        return Optional.ofNullable(lossReport);
    }

    /** Причина сбоя; есть у упавшей выгрузки. */
    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    /** Кто запросил выгрузку. */
    public String requestedBy() {
        return requestedBy;
    }

    /** Когда выгрузка запрошена. */
    public Instant requestedAt() {
        return requestedAt;
    }
}
