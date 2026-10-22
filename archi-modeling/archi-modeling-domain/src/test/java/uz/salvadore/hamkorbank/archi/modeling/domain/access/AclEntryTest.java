package uz.salvadore.hamkorbank.archi.modeling.domain.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

class AclEntryTest {

    @ParameterizedTest
    @MethodSource("invalidPrincipals")
    @DisplayName("INV-MDL-011: в записи списка доступа — непустое имя пользователя или группы не длиннее предела")
    void invalidPrincipalIsRejected(String principal) {
        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> new AclEntry(PrincipalType.USER, principal, AclAccess.READ));

        assertEquals(ModelingCodes.INVALID_INPUT, rejected.code());
        assertEquals(ModelingMessages.ACL_ENTRY_INVALID, rejected.reason().key());
    }

    static Stream<String> invalidPrincipals() {
        return Stream.of(null, "", "   ", "u".repeat(AclEntry.PRINCIPAL_MAX + 1));
    }
}
