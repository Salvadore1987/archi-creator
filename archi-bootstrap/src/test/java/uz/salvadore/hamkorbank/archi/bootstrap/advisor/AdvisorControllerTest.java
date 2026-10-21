package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.AdvisorController;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.ReviewRequest;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ExplainElementCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.GenerateDocumentationCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.PlanModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ReviewModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorApplicationService;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorStream;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.StreamSink;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Role;

/** Поток помощника без собственного срока: срок один — {@code spring.mvc.async.request-timeout}. */
class AdvisorControllerTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    /** Поток без кадров: контроллеру важно только, как он открыт. */
    private static final AdvisorStream EMPTY = new AdvisorStream() {
        private final UUID session = IDS.generate();

        @Override
        public UUID sessionId() {
            return session;
        }

        @Override
        public void run(StreamSink sink) {
            // кадров нет
        }
    };

    private static final AdvisorApplicationService ADVISOR = new AdvisorApplicationService() {
        @Override
        public AdvisorStream reviewModel(Requester actor, ReviewModelCommand command) {
            return EMPTY;
        }

        @Override
        public AdvisorStream explainElement(Requester actor, ExplainElementCommand command) {
            return EMPTY;
        }

        @Override
        public AdvisorStream planModel(Requester actor, PlanModelCommand command) {
            return EMPTY;
        }

        @Override
        public AdvisorStream generateDocumentation(Requester actor, GenerateDocumentationCommand command) {
            return EMPTY;
        }
    };

    @Test
    @DisplayName("NFR-04: у потока нет своего срока — его задаёт настройка асинхронного запроса")
    void streamTakesConfiguredTimeout() {
        AdvisorController controller = new AdvisorController(ADVISOR, JsonMapper.builder().build(),
                new TaskExecutorAdapter(Runnable::run));

        SseEmitter emitter = controller.review(Requester.of("architect", Role.ARCHITECT),
                new ReviewRequest(IDS.generate(), null), "key-1", Locale.ROOT).getBody();

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isNull();
    }
}
