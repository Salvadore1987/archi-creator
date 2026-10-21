package uz.salvadore.hamkorbank.archi.advisor.application.port.in;

import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorAnswerResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.FindingResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.ModelPlanResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.SessionMetaResponse;

/**
 * Куда сценарий отдаёт поток ответа — адаптер переводит события в кадры SSE.
 * Ошибка записи (клиент ушёл) прерывает поток: сессия завершается неуспехом,
 * израсходованное списывается.
 */
public interface StreamSink {

    /** Очередной кусок текста ответа. */
    void token(String delta);

    /** Замечание ревью, как только оно разобрано. */
    void finding(FindingResponse finding);

    /** План целиком — у функции плана. */
    void plan(ModelPlanResponse plan);

    /** Ответ подсказки или документации целиком. */
    void answer(AdvisorAnswerResponse answer);

    /** Последний кадр — итог сессии и её стоимость. */
    void meta(SessionMetaResponse meta);
}
