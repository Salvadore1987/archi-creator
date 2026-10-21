package uz.salvadore.hamkorbank.archi.bootstrap.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;

class SchemaValidationIT extends IntegrationTest {

    @Autowired
    Environment environment;

    /**
     * Сущности сверяются со схемой Flyway при старте: расхождение имени или типа столбца — отказ
     * старта, а не ошибка первого запроса. Контекст этого теста поднят — значит, сверка прошла.
     */
    @Test
    @DisplayName("схема сверяется с сущностями при старте приложения")
    void schemaIsValidatedOnStartup() {
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }
}
