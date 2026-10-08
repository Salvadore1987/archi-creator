package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSessionLookup;

/**
 * Сессии импорта с находками. Ключ идемпотентности уникален в пространстве: второй
 * параллельный импорт с тем же ключом получает конфликт ключа, а не вторую модель.
 */
public interface ImportSessionRepository extends ImportSessionLookup {

    Optional<ImportSession> find(ImportSessionId id);

    void save(ImportSession session);
}
