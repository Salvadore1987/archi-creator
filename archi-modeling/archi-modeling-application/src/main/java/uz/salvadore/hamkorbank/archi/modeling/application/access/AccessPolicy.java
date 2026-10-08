package uz.salvadore.hamkorbank.archi.modeling.application.access;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;

/**
 * Проверка роли на границе use case'а (FR-28). Список доступа модели (INV-MDL-011)
 * проверяется после, над конкретной моделью: роль отвечает «какие операции», список —
 * «над какими моделями».
 */
public final class AccessPolicy {

    private AccessPolicy() {
    }

    public static void require(EditorIdentity actor, Operation operation) {
        if (actor.roles().stream().noneMatch(operation.roles()::contains)) {
            throw new ModelingException(ModelingException.Codes.ACCESS_DENIED, Failure.FORBIDDEN,
                    operation.useCase() + " доступна ролям " + operation.roles(),
                    Map.of("operation", operation.useCase()));
        }
    }
}
