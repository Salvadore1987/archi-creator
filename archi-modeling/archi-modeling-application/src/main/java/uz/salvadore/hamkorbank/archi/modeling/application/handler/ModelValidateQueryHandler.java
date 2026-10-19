package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.List;
import java.util.stream.Collectors;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ValidateModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ValidationFindingResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.validation.ModelValidator;
import uz.salvadore.hamkorbank.archi.modeling.domain.validation.ValidationFinding;

/** Отчёт проверки модели по метамодели, без помощника: импортированные нарушения видны здесь. */
public final class ModelValidateQueryHandler {

    private final ModelingKernel kernel;
    private final ModelValidator validator = ModelValidator.archimate32();

    public ModelValidateQueryHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Находки с текстом на языке запроса; их число по уровням уходит в метрику. */
    public List<ValidationFindingResponse> handle(EditorIdentity actor, ValidateModelQuery query) {
        ModelId modelId = ModelId.of(query.modelId());
        return kernel.run(Operation.VALIDATE_MODEL, actor, () -> kernel.unitOfWork().read(() -> {
            List<ValidationFinding> findings = validator.validate(kernel.visibleModel(modelId, actor,
                    AclAccess.READ));
            kernel.metrics().validationFindings(modelId, findings.stream()
                    .collect(Collectors.groupingBy(f -> f.severity().name(), Collectors.counting())));
            return findings.stream().map(f -> ModelDataMapper.finding(f, kernel.texts())).toList();
        }));
    }
}
