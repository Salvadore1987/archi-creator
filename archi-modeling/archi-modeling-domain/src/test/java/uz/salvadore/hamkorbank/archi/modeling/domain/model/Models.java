package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiIdGenerator;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/** Заготовки для тестов домена: модель с девятью корнями и элементы в папках своих слоёв. */
public final class Models {

    public static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");
    public static final UuidV7 UUIDS = new UuidV7(Clock.fixed(NOW, ZoneOffset.UTC));
    public static final ArchiIdGenerator ARCHI_IDS = new ArchiIdGenerator();
    public static final EditorIdentity ARCHITECT = EditorIdentity.of("architect-1", Role.ARCHITECT);

    private Models() {
    }

    public static ArchitectureModel empty() {
        return ArchitectureModel.create(ModelId.next(UUIDS), WorkspaceId.next(UUIDS), ARCHI_IDS.next(), "Модель",
                ARCHITECT, NOW, () -> FolderId.next(UUIDS), ARCHI_IDS::next);
    }

    public static FolderId root(ArchitectureModel model, FolderType type) {
        return model.roots().get(type).id();
    }

    public static Element element(ArchitectureModel model, String type, FolderType folder, String name) {
        return model.addElement(ArchiType.ofSimpleName(type), name, root(model, folder), ElementId.next(UUIDS),
                ARCHI_IDS.next(), NOW);
    }

    public static Relationship relate(ArchitectureModel model, String type, ConceptRef source, ConceptRef target) {
        return model.addRelationship(ArchiType.ofSimpleName(type), source, target, java.util.Optional.empty(),
                RelationshipId.next(UUIDS), ARCHI_IDS.next(), NOW).value();
    }

    public static ArchiId archiId() {
        return ARCHI_IDS.next();
    }
}
