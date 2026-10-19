package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetLastPublishedVersionQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.VersionDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Последняя опубликованная в Git версия модели — системное чтение, без актора. */
public final class PublishedVersionGetQueryHandler {

    private final ModelingKernel kernel;

    public PublishedVersionGetQueryHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Самая новая версия с коммитом или пусто. */
    public Optional<VersionResponse> handle(GetLastPublishedVersionQuery query) {
        return kernel.unitOfWork().read(() -> kernel.versions().list(ModelId.of(query.modelId())).stream()
                .filter(v -> v.gitSha() != null).findFirst().map(VersionDataMapper::version));
    }
}
