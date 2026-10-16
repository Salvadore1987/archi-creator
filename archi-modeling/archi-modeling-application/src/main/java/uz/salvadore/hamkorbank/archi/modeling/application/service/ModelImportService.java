package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.Arrays;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Входящий порт для interchange: применить импортированный документ как новую модель
 * (порт {@code ModelWriter}) или как новую версию существующей — импорт состояния извне.
 * Роль проверяет вызывающий — у импорта своя строка в таблице доступа; список доступа,
 * состояние модели и блокировка проверяются здесь, как у любой записи.
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
                    () -> ModelingDomainException.notFound(
                            Message.of(ModelingMessages.WORKSPACE, content.model().workspaceId())));
            kernel.save(content.model());
            content.views().forEach(kernel.views::save);
            return versions.commit(content.model(), actor, comment);
        });
    }

    /**
     * Содержимое модели, как его соберёт сохранение, и последняя версия. Модель видна
     * актору на запись и активна — читать её для замены иначе незачем.
     *
     * @param versionNo последняя версия; у модели без версий {@code null}
     */
    public record CurrentContent(ModelHeader header, Long versionNo, byte[] xml) {

        public CurrentContent {
            xml = Arrays.copyOf(xml, xml.length);
        }

        @Override
        public byte[] xml() {
            return Arrays.copyOf(xml, xml.length);
        }
    }

    /**
     * @param created ложь — содержимое совпало с текущим, версия прежняя
     */
    public record Replaced(ModelVersion version, boolean created, int opaqueObjects) {
    }

    /**
     * Текущее содержимое модели в формате {@code .archimate} и номер последней версии — основа для замены и сравнения.
     * Удалённая модель — {@code 409}.
     */
    public CurrentContent current(EditorIdentity actor, ModelId modelId) {
        return kernel.unitOfWork.read(() -> {
            ModelHeader header = kernel.visibleHeader(modelId, actor, AclAccess.WRITE);
            header.requireExportable();
            return new CurrentContent(header, kernel.versions.last(modelId).map(ModelVersion::versionNo).orElse(null),
                    versions.currentXml(modelId));
        });
    }

    /**
     * Содержимое модели заменяется снимком и фиксируется новой версией — одной транзакцией
     * с вызывающим. Нужна блокировка автора. Снимок совпал с текущим содержимым — база
     * не трогается и версия остаётся прежней.
     */
    public Replaced replace(EditorIdentity actor, ModelId modelId, byte[] xml, String comment) {
        return kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.writableModel(modelId, actor);
            Optional<ModelVersion> last = kernel.versions.last(modelId);
            if (last.isPresent() && last.get().contentHash().equals(Snapshots.sha256(xml))
                    && Arrays.equals(versions.currentXml(modelId), xml)) {
                return new Replaced(last.get(), false, 0);
            }
            VersionService.Replacement replacement = versions.replace(model, xml, actor, comment);
            boolean created = last.map(v -> replacement.version().versionNo() > v.versionNo()).orElse(true);
            return new Replaced(replacement.version(), created, replacement.opaqueObjects());
        });
    }
}
