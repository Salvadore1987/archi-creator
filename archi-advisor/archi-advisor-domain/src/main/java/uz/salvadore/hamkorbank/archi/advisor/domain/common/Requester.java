package uz.salvadore.hamkorbank.archi.advisor.domain.common;

import java.util.Objects;
import java.util.Set;

/**
 * Автор обращения к помощнику. Персональных данных нет: {@code subject} из JWT,
 * а не имя и почта. Группы не нужны: доступ к модели решает modeling, когда
 * отдаёт контекст.
 */
public record Requester(String subject, Set<Role> roles) {

    public Requester {
        Objects.requireNonNull(subject, "subject");
        roles = Set.copyOf(roles);
        if (subject.isBlank()) {
            throw new InvalidValueException(AdvisorMessages.REQUESTER_WITHOUT_SUBJECT);
        }
    }

    public static Requester of(String subject, Role... roles) {
        return new Requester(subject, Set.of(roles));
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
