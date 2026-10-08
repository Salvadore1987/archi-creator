package uz.salvadore.hamkorbank.archi.bootstrap.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Контейнеры интеграционных тестов (§9.3) — по одному на прогон: поднимать базу
 * на каждый класс — минуты ни за что. Образ — тот же, что в docker-compose.yml.
 */
public final class Containers {

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }

    private Containers() {
    }
}
