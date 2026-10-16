package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.BaseId;

/** Внешний идентификатор modeling: здесь только принимается и передаётся. */
public final class ModelId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public ModelId(UUID value) {
        super(value);
    }
}
