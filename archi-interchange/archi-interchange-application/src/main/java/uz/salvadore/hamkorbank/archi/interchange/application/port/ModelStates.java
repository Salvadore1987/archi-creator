package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.util.Arrays;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Содержимое модели глазами импорта состояния: что в базе сейчас и замена его новой
 * версией. Команда — в modeling: список доступа, состояние модели и блокировка
 * проверяются там. Вызовы идут в транзакции вызывающего, если она открыта.
 */
public interface ModelStates {

    /** Модели нет, скрыта или удалена — отказ modeling. */
    Current current(EditorIdentity actor, ModelId model);

    /** Без блокировки автора — отказ modeling; содержимое совпало с текущим — версия прежняя. */
    Replaced replace(EditorIdentity actor, ModelId model, byte[] xml, String comment);

    /**
     * @param versionNo последняя версия; у модели без версий пусто
     * @param xml       текущее содержимое тем же снимком, который соберёт сохранение
     */
    record Current(WorkspaceId workspace, String modelName, Optional<Long> versionNo, byte[] xml) {

        public Current {
            xml = Arrays.copyOf(xml, xml.length);
        }

        @Override
        public byte[] xml() {
            return Arrays.copyOf(xml, xml.length);
        }

        public String hash() {
            return ContentHash.of(xml).value();
        }
    }

    /** @param created ложь — содержимое совпало с текущим, версия прежняя */
    record Replaced(long versionNo, boolean created, int opaqueObjects) {
    }
}
