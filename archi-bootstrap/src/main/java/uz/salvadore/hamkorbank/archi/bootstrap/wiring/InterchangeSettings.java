package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки interchange ({@code archi.interchange.*}).
 *
 * @param maxParallelImportsPerWorkspace сколько импортов идёт в одно рабочее пространство
 *                                       одновременно; следующий получает отказ, а не ждёт
 */
@ConfigurationProperties("archi.interchange")
public record InterchangeSettings(int maxParallelImportsPerWorkspace) {
}
