package uz.salvadore.hamkorbank.archi.advisor.application.service;

import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorAnswer;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.Finding;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.ModelPlan;

/**
 * Куда сценарий отдаёт поток ответа — адаптер переводит события в кадры SSE.
 * Ошибка записи (клиент ушёл) прерывает поток: сессия завершается неуспехом,
 * израсходованное списывается.
 */
public interface StreamSink {

    /** Очередной кусок текста ответа. */
    void token(String delta);

    /** Замечание ревью, как только оно разобрано. */
    void finding(Finding finding);

    /** План целиком — у функции плана. */
    void plan(ModelPlan plan);

    /** Ответ подсказки или документации целиком. */
    void answer(AdvisorAnswer answer);

    /** Последний кадр — итог сессии и её стоимость. */
    void meta(SessionMeta meta);
}
