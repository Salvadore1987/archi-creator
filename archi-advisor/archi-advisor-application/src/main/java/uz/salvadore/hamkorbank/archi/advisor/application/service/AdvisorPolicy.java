package uz.salvadore.hamkorbank.archi.advisor.application.service;

import java.time.ZoneId;
import java.util.Objects;

/**
 * Пределы помощника, которые задаёт конфигурация.
 *
 * @param maxContextTokens         верхняя граница контекста одного запроса
 * @param minCoverage              доля элементов области, ниже которой усечённый контекст
 *                                 не отправляется: ревью части модели читалось бы как ревью модели
 * @param defaultMonthlyTokenLimit лимит пространства, которому администратор его не задал
 * @param maxOutputTokens          предел ответа одного обращения
 * @param userRequestsPerMinute    частота обращений автора
 * @param workspaceRequestsPerMinute частота обращений пространства
 * @param budgetZone               часовой пояс, по которому месяц начинается с первого числа
 */
public record AdvisorPolicy(int maxContextTokens, double minCoverage, long defaultMonthlyTokenLimit,
                            int maxOutputTokens, int userRequestsPerMinute, int workspaceRequestsPerMinute,
                            ZoneId budgetZone) {

    public AdvisorPolicy {
        Objects.requireNonNull(budgetZone, "budgetZone");
    }
}
