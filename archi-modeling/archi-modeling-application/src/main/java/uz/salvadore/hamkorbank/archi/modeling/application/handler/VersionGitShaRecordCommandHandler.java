package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RecordGitShaCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Связать версию с коммитом, в котором её содержимое лежит в репозитории, — системная запись. */
public final class VersionGitShaRecordCommandHandler {

    private final ModelingKernel kernel;
    private final VersionContents contents;

    public VersionGitShaRecordCommandHandler(ModelingKernel kernel, VersionContents contents) {
        this.kernel = kernel;
        this.contents = contents;
    }

    /** Версия запоминает коммит публикации. */
    public void handle(RecordGitShaCommand command) {
        kernel.unitOfWork().write(() -> {
            kernel.versions().save(contents.requireVersion(ModelId.of(command.modelId()), command.versionNo())
                    .publishedAs(command.sha()));
            return null;
        });
    }
}
