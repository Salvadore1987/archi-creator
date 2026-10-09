package uz.salvadore.hamkorbank.archi.modeling.domain.access;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/** Запись списка доступа: кто и с каким уровнем. */
public record AclEntry(PrincipalType principalType, String principal, AclAccess access) {

    public static final int PRINCIPAL_MAX = 200;

    public AclEntry {
        Objects.requireNonNull(principalType, "principalType");
        Objects.requireNonNull(access, "access");
        if (principal == null || principal.isBlank() || principal.length() > PRINCIPAL_MAX) {
            throw ModelingException.invalid(Message.of(ModelingMessages.ACL_ENTRY_INVALID, PRINCIPAL_MAX));
        }
    }

    public boolean names(EditorIdentity editor) {
        return switch (principalType) {
            case USER -> principal.equals(editor.subject());
            case GROUP -> editor.groups().contains(principal);
        };
    }
}
