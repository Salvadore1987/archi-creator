package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки modeling ({@code archi.modeling.*}).
 *
 * @param lockTtl              срок блокировки; клиент продлевает её повторным захватом
 * @param defaultWorkspaceName имя пространства, которое заводится при пустой базе; не задано —
 *                             имя из ресурсов на языке приложения по умолчанию
 * @param retentionEnabled     фоновая очистка снимков; пространство без доступного
 *                             Git она не трогает — там снимок единственная копия
 * @param retentionCron        расписание очистки
 */
@ConfigurationProperties("archi.modeling")
@Validated
public record ModelingSettings(@DefaultValue("30m") @NotNull Duration lockTtl,
                               String defaultWorkspaceName,
                               @DefaultValue("true") boolean retentionEnabled,
                               @DefaultValue("0 30 3 * * *") @NotBlank String retentionCron) {
}
