package uz.salvadore.hamkorbank.archi.bootstrap.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Контейнеры интеграционных тестов — по одному на прогон: поднимать базу
 * на каждый класс — минуты ни за что. Образ — тот же, что в docker-compose.yml.
 * Адрес и учётные данные базы контекст берёт из контейнера сам ({@code @ServiceConnection}).
 */
public final class Containers {

    @ServiceConnection
    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }

    private Containers() {
    }
}
