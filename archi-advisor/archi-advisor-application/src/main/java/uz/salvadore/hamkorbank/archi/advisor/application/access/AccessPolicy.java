package uz.salvadore.hamkorbank.archi.advisor.application.access;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;

/** Проверка роли на границе use case'а. Доступ к самой модели решает modeling, отдавая контекст. */
public final class AccessPolicy {

    private AccessPolicy() {
    }

    public static void require(Requester requester, AdvisorOperation operation) {
        if (requester.roles().stream().noneMatch(operation.roles()::contains)) {
            throw new AdvisorException(AdvisorCodes.ACCESS_DENIED, Failure.FORBIDDEN,
                    Message.of(AdvisorMessages.OPERATION_DENIED, operation.useCase(), operation.roles()),
                    Map.of("operation", operation.useCase()));
        }
    }
}
