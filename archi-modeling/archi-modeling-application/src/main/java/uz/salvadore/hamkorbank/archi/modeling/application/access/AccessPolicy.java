package uz.salvadore.hamkorbank.archi.modeling.application.access;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Проверка роли на границе use case'а. Список доступа модели
 * проверяется после, над конкретной моделью: роль отвечает «какие операции», список —
 * «над какими моделями».
 */
public final class AccessPolicy {

    private AccessPolicy() {
    }

    /** Отказывает с {@code 403}, если ни одна роль автора не допускает операцию. */
    public static void require(EditorIdentity actor, Operation operation) {
        if (actor.roles().stream().noneMatch(operation.roles()::contains)) {
            throw new ModelingDomainException(ModelingCodes.ACCESS_DENIED, Failure.FORBIDDEN,
                    Message.of(ModelingMessages.OPERATION_DENIED, operation.useCase(), operation.roles()),
                    Map.of("operation", operation.useCase()));
        }
    }
}
