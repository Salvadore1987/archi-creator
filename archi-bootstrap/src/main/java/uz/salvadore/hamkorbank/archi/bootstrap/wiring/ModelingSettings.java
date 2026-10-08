package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки modeling ({@code archi.modeling.*}).
 *
 * @param lockTtl              срок блокировки; клиент продлевает её повторным захватом (UC-MDL-005)
 * @param defaultWorkspaceName имя пространства, которое заводится при пустой базе
 * @param retentionEnabled     фоновая очистка снимков (docs/database.md §4.4). Выключена до 7a:
 *                             без Git снимок — единственная копия (FR-47)
 * @param retentionCron        расписание очистки
 */
@ConfigurationProperties("archi.modeling")
public record ModelingSettings(@DefaultValue("30m") Duration lockTtl,
                               @DefaultValue("Основное рабочее пространство") String defaultWorkspaceName,
                               @DefaultValue("false") boolean retentionEnabled,
                               @DefaultValue("0 30 3 * * *") String retentionCron) {
}
