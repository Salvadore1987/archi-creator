package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Входящий порт для interchange: применить импортированный документ как новую модель
 * (UC-IXC-001, п. 5, порт {@code ModelWriter}). Роль проверяет вызывающий — у импорта
 * своя строка в таблице доступа (spec/nfr/interchange.yaml).
 */
public final class ModelImportService {

    private final ModelingKernel kernel;
    private final WorkspaceRepository workspaces;
    private final VersionService versions;

    public ModelImportService(ModelingKernel kernel, WorkspaceRepository workspaces, VersionService versions) {
        this.kernel = kernel;
        this.workspaces = workspaces;
        this.versions = versions;
    }

    /** Модель и представления пишутся одной транзакцией; версия 1 — снимок, собранный из базы. */
    public ModelVersion store(ModelContent content, EditorIdentity actor, String comment) {
        return kernel.unitOfWork.write(() -> {
            workspaces.find(content.model().workspaceId()).orElseThrow(
                    () -> ModelingException.notFound("рабочее пространство " + content.model().workspaceId()));
            kernel.save(content.model());
            content.views().forEach(kernel.views::save);
            return versions.commit(content.model(), actor, Optional.of(comment));
        });
    }
}
