package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;

/** Значения команд, присланные строкой, — в доменные типы; неизвестное значение — отказ во вводе. */
public final class RequestValues {

    private RequestValues() {
    }

    /** Тип ArchiMate по имени Archi; пустой или неизвестный — отказ. */
    public static ArchiType archiType(@Nullable String value) {
        if (value == null) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.ARCHI_TYPE_REQUESTED, value));
        }
        try {
            return ArchiType.of(value);
        } catch (InvalidValueException invalid) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.ARCHI_TYPE_REQUESTED, value));
        }
    }

    /** Вид доступа; {@code null} — не задан, неизвестный — отказ. */
    public static @Nullable AccessType accessType(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return AccessType.valueOf(value);
        } catch (IllegalArgumentException invalid) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.ACCESS_TYPE_REQUESTED, value));
        }
    }
}
