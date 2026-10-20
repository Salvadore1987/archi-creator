package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки interchange ({@code archi.interchange.*}).
 *
 * @param maxParallelImportsPerWorkspace сколько импортов идёт в одно рабочее пространство
 *                                       одновременно; следующий получает отказ, а не ждёт
 * @param pngMaxAreaPx                   предел площади растра PNG в пикселях
 * @param maxParallelRasterizations      сколько растеризаций PNG идёт одновременно на процесс
 * @param rasterizationWait              сколько картинка ждёт растеризатора; дольше — отказ «занят»
 */
@ConfigurationProperties("archi.interchange")
public record InterchangeSettings(int maxParallelImportsPerWorkspace, long pngMaxAreaPx,
                                  int maxParallelRasterizations, Duration rasterizationWait) {
}
