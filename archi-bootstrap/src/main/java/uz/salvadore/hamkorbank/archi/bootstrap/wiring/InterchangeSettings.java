package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

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
@Validated
public record InterchangeSettings(@DefaultValue("1") @Positive int maxParallelImportsPerWorkspace,
                                  @DefaultValue("64000000") @Positive long pngMaxAreaPx,
                                  @DefaultValue("1") @Positive int maxParallelRasterizations,
                                  @DefaultValue("60s") @NotNull Duration rasterizationWait) {
}
