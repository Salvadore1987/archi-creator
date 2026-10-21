package uz.salvadore.hamkorbank.archi.advisor.application.port.in;

import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ExplainElementCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.GenerateDocumentationCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.PlanModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ReviewModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;

/**
 * Четыре функции помощника. Все отказы — исключением отсюда, до потока; полученный поток
 * отказом уже не начнётся. Ни одна функция не изменяет модель.
 */
public interface AdvisorApplicationService {

    /** Ревью модели или представления — замечания потоком. */
    AdvisorStream reviewModel(Requester actor, ReviewModelCommand command);

    /** Подсказка по элементу — ответ потоком. */
    AdvisorStream explainElement(Requester actor, ExplainElementCommand command);

    /** План фрагмента архитектуры — предложение элементов и связей. */
    AdvisorStream planModel(Requester actor, PlanModelCommand command);

    /** Документация по модели, представлению или элементу — текст потоком. */
    AdvisorStream generateDocumentation(Requester actor, GenerateDocumentationCommand command);
}
