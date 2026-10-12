package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки modeling ({@code archi.modeling.*}).
 *
 * @param lockTtl              срок блокировки; клиент продлевает её повторным захватом
 * @param defaultWorkspaceName имя пространства, которое заводится при пустой базе; данные,
 *                             а не текст интерфейса, — задаётся в конфигурации
 * @param retentionEnabled     фоновая очистка снимков. Выключена до 7a:
 *                             без Git снимок — единственная копия
 * @param retentionCron        расписание очистки
 */
@ConfigurationProperties("archi.modeling")
public record ModelingSettings(@DefaultValue("30m") Duration lockTtl,
                               String defaultWorkspaceName,
                               @DefaultValue("false") boolean retentionEnabled,
                               @DefaultValue("0 30 3 * * *") String retentionCron) {
}
