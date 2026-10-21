package uz.salvadore.hamkorbank.archi.advisor.application.service;

import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ExplainElementCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.GenerateDocumentationCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.PlanModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ReviewModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.AdvisorSessionStartCommandHandler;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorApplicationService;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorStream;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;

/** Входной порт функций помощника: делегирует сценарию обращения. */
public final class AdvisorApplicationServiceImpl implements AdvisorApplicationService {

    private final AdvisorSessionStartCommandHandler start;

    public AdvisorApplicationServiceImpl(AdvisorSessionStartCommandHandler start) {
        this.start = start;
    }

    @Override
    public AdvisorStream reviewModel(Requester actor, ReviewModelCommand command) {
        return start.handle(actor, command);
    }

    @Override
    public AdvisorStream explainElement(Requester actor, ExplainElementCommand command) {
        return start.handle(actor, command);
    }

    @Override
    public AdvisorStream planModel(Requester actor, PlanModelCommand command) {
        return start.handle(actor, command);
    }

    @Override
    public AdvisorStream generateDocumentation(Requester actor, GenerateDocumentationCommand command) {
        return start.handle(actor, command);
    }
}
