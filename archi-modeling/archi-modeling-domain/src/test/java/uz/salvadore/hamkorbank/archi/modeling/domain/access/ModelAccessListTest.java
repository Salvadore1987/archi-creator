package uz.salvadore.hamkorbank.archi.modeling.domain.access;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;

class ModelAccessListTest {

    private static final ModelId MODEL = ModelId.next(Models.UUIDS);

    private static final ModelAccessList ACL = ModelAccessList.restore(MODEL, List.of(
            new AclEntry(PrincipalType.GROUP, "arch-retail", AclAccess.WRITE),
            new AclEntry(PrincipalType.USER, "auditor-1", AclAccess.READ)));
    private static final EditorIdentity AUDITOR = EditorIdentity.of("auditor-1", Role.ARCHITECT);
    private static final EditorIdentity STRANGER = EditorIdentity.of("ivan", Role.ARCHITECT);

    @Test
    @DisplayName("INV-MDL-011: член группы из списка получает доступ записи группы")
    void groupMemberGetsGroupAccess() {
        EditorIdentity retail = new EditorIdentity("anna", Set.of(Role.ARCHITECT), Set.of("arch-retail"));

        assertTrue(ACL.permits(retail, AclAccess.WRITE));
    }

    @Test
    @DisplayName("INV-MDL-011: запись READ даёт чтение, но не правку — список сужает роли, не расширяет")
    void readEntryGrantsReadOnly() {
        assertTrue(ACL.permits(AUDITOR, AclAccess.READ));
        assertFalse(ACL.permits(AUDITOR, AclAccess.WRITE));
    }

    @Test
    @DisplayName("INV-MDL-011: ADMIN получает доступ независимо от списка")
    void adminIsPermittedRegardlessOfList() {
        EditorIdentity admin = EditorIdentity.of("root", Role.ADMIN);

        assertTrue(ACL.permits(admin, AclAccess.WRITE), "ADMIN — независимо от списка");
    }

    @Test
    @DisplayName("INV-MDL-011: чужим модель не существует — отказ «не найдено»")
    void strangerGetsNotFound() {
        assertEquals(Failure.NOT_FOUND, assertThrows(ModelingDomainException.class,
                () -> ACL.require(STRANGER, AclAccess.READ)).failure(), "существование модели не раскрывается");
    }

    @Test
    @DisplayName("INV-MDL-011: правка при доступе READ — отказ «запрещено»")
    void writeWithReadAccessIsForbidden() {
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> ACL.require(AUDITOR, AclAccess.WRITE)).failure());
    }

    @Test
    @DisplayName("INV-MDL-011: пустой список — доступ решают одни роли")
    void openListLeavesRolesOnly() {
        assertTrue(ModelAccessList.open(MODEL).permits(STRANGER, AclAccess.WRITE), "пустой список — одни роли");
    }

    @Test
    @DisplayName("UC-MDL-007: автор модели меняет список")
    void authorManagesList() {
        assertDoesNotThrow(() -> ModelAccessList.requireManager(EditorIdentity.of("author", Role.ARCHITECT),
                "author"));
    }

    @Test
    @DisplayName("UC-MDL-007: ADMIN меняет список чужой модели")
    void adminManagesList() {
        assertDoesNotThrow(() -> ModelAccessList.requireManager(EditorIdentity.of("root", Role.ADMIN), "author"));
    }

    @Test
    @DisplayName("UC-MDL-007: архитектор, не автор модели, список не меняет")
    void otherArchitectDoesNotManageList() {
        assertThrows(ModelingDomainException.class,
                () -> ModelAccessList.requireManager(EditorIdentity.of("ivan", Role.ARCHITECT), "author"));
    }
}
