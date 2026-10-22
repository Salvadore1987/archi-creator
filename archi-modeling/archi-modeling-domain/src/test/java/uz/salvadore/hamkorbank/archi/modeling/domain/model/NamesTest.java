package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

class NamesTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Обязательное имя не бывает пустым")
    void requiredNameMustNotBeBlank(String name) {
        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> Names.required(name, Names.MODEL));

        assertEquals(ModelingCodes.INVALID_INPUT, rejected.code());
        assertEquals(ModelingMessages.NAME_EMPTY, rejected.reason().key());
    }

    @Test
    @DisplayName("Имя, которое бывает пустым, всё равно задаётся — хотя бы пустой строкой")
    void limitedNameMustBePresent() {
        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> Names.limited(null, Names.ELEMENT));

        assertEquals(ModelingCodes.INVALID_INPUT, rejected.code());
        assertEquals(ModelingMessages.NAME_MISSING, rejected.reason().key());
    }
}
