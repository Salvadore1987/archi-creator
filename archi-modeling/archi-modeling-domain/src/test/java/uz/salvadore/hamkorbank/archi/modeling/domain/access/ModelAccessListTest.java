package uz.salvadore.hamkorbank.archi.modeling.domain.access;

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
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;

class ModelAccessListTest {

    private static final ModelId MODEL = ModelId.next(Models.UUIDS);

    @Test
    @DisplayName("INV-MDL-011: список сужает роли, не расширяет; чужим модель не существует")
    void listedPrincipalsOnlyAndRolesStillApply() {
        ModelAccessList acl = new ModelAccessList(MODEL, List.of(
                new AclEntry(PrincipalType.GROUP, "arch-retail", AclAccess.WRITE),
                new AclEntry(PrincipalType.USER, "auditor-1", AclAccess.READ)));
        EditorIdentity retail = new EditorIdentity("anna", Set.of(Role.ARCHITECT), Set.of("arch-retail"));
        EditorIdentity auditor = EditorIdentity.of("auditor-1", Role.ARCHITECT);
        EditorIdentity stranger = EditorIdentity.of("ivan", Role.ARCHITECT);
        EditorIdentity admin = EditorIdentity.of("root", Role.ADMIN);

        assertTrue(acl.permits(retail, AclAccess.WRITE));
        assertTrue(acl.permits(auditor, AclAccess.READ));
        assertFalse(acl.permits(auditor, AclAccess.WRITE));
        assertTrue(acl.permits(admin, AclAccess.WRITE), "ADMIN — независимо от списка");

        assertEquals(Failure.NOT_FOUND, assertThrows(ModelingException.class,
                () -> acl.require(stranger, AclAccess.READ)).failure(), "существование модели не раскрывается");
        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingException.class,
                () -> acl.require(auditor, AclAccess.WRITE)).failure());
        assertTrue(ModelAccessList.open(MODEL).permits(stranger, AclAccess.WRITE), "пустой список — одни роли");
    }

    @Test
    @DisplayName("UC-MDL-007: список меняют ADMIN и автор модели")
    void onlyAdminOrAuthorManagesList() {
        ModelAccessList.requireManager(EditorIdentity.of("author", Role.ARCHITECT), "author");
        ModelAccessList.requireManager(EditorIdentity.of("root", Role.ADMIN), "author");
        assertThrows(ModelingException.class,
                () -> ModelAccessList.requireManager(EditorIdentity.of("ivan", Role.ARCHITECT), "author"));
    }
}
