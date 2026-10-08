package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Objects;
import java.util.Set;

/**
 * Автор команды — из JWT Keycloak. Персональных данных нет: {@code subject},
 * а не имя и почта (spec/nfr/modeling.yaml#security.pii_fields).
 *
 * @param groups группы Keycloak; нужны списку доступа модели (INV-MDL-011)
 */
public record EditorIdentity(String subject, Set<Role> roles, Set<String> groups) {

    public EditorIdentity {
        Objects.requireNonNull(subject, "subject");
        roles = Set.copyOf(roles);
        groups = Set.copyOf(groups);
        if (subject.isBlank()) {
            throw new IllegalArgumentException("автор команды без subject");
        }
    }

    public static EditorIdentity of(String subject, Role... roles) {
        return new EditorIdentity(subject, Set.of(roles), Set.of());
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return has(Role.ADMIN);
    }
}
