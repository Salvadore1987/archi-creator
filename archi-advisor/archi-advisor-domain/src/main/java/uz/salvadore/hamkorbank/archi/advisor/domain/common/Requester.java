package uz.salvadore.hamkorbank.archi.advisor.domain.common;

import java.util.Objects;
import java.util.Set;

/**
 * Автор обращения к помощнику. Персональных данных нет: {@code subject} из JWT,
 * а не имя и почта.
 *
 * @param groups группы Keycloak — помощник их не читает, а передаёт modeling:
 *               список доступа модели решает, отдавать ли контекст
 */
public record Requester(String subject, Set<Role> roles, Set<String> groups) {

    public Requester {
        Objects.requireNonNull(subject, "subject");
        roles = Set.copyOf(roles);
        groups = Set.copyOf(groups);
        if (subject.isBlank()) {
            throw new InvalidValueException(AdvisorMessages.REQUESTER_WITHOUT_SUBJECT);
        }
    }

    /** Автор с ролями и без групп. */
    public static Requester of(String subject, Role... roles) {
        return new Requester(subject, Set.of(roles), Set.of());
    }

    /** Есть ли у автора эта роль. */
    public boolean has(Role role) {
        return roles.contains(role);
    }
}
