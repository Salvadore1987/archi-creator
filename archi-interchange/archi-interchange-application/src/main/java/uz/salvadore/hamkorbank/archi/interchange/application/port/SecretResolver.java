package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.SecretRef;

/** Значение секрета по ссылке — в момент обращения; не кэшируется и не пишется в журнал. */
public interface SecretResolver {

    /** Значение секрета; пусто, если переменная окружения не задана. */
    Optional<String> resolve(SecretRef ref);
}
