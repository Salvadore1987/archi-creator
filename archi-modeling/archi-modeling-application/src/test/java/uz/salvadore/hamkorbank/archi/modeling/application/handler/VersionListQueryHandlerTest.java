package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;

class VersionListQueryHandlerTest {

    @Test
    @DisplayName("FR-06: страница истории — 50 по умолчанию, не больше 200, меньше одной — отказ")
    void pageSizeIsBounded() {
        assertEquals(50, VersionListQueryHandler.pageSize(null));
        assertEquals(7, VersionListQueryHandler.pageSize(7));
        assertEquals(200, VersionListQueryHandler.pageSize(10_000));
        ModelingDomainException zero = assertThrows(ModelingDomainException.class,
                () -> VersionListQueryHandler.pageSize(0));
        assertEquals(ModelingCodes.INVALID_INPUT, zero.code());
    }
}
