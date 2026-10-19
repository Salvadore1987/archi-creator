package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.BaseId;

/** Внешний идентификатор modeling: здесь только принимается и передаётся. */
public final class ViewId extends BaseId<UUID> {

    /** Ключ над значением UUID. */
    public ViewId(UUID value) {
        super(value);
    }
}
