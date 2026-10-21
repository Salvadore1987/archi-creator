package uz.salvadore.hamkorbank.archi.advisor.adapter.rest;

import jakarta.validation.Valid;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.DocumentRequest;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.ExplainRequest;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.PlanRequest;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.ReviewRequest;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ExplainElementCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.GenerateDocumentationCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.PlanModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ReviewModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorApplicationService;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorStream;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;

/**
 * Четыре функции помощника — поток SSE на {@code POST}. Отказы сценарий принимает до
 * потока, и они приходят обычным {@code problem+json}; открытый поток отказом уже не
 * начнётся. Ни один обработчик не пишет в модель.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AdvisorController {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorController.class);

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final AdvisorApplicationService advisor;
    private final JsonMapper json;
    private final AsyncTaskExecutor executor;

    public AdvisorController(AdvisorApplicationService advisor, JsonMapper json,
                             @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.advisor = advisor;
        this.json = json;
        this.executor = executor;
    }

    /** Ревью модели или одного представления — замечания потоком SSE. */
    @PostMapping("/review")
    public ResponseEntity<SseEmitter> review(Requester requester, @Valid @RequestBody ReviewRequest body,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key, Locale locale) {
        return stream(advisor.reviewModel(requester,
                new ReviewModelCommand(body.modelId(), body.viewId(), key(key), locale)));
    }

    /** Подсказка по выбранному элементу и его окрестности — ответ потоком SSE. */
    @PostMapping("/explain")
    public ResponseEntity<SseEmitter> explain(Requester requester, @Valid @RequestBody ExplainRequest body,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key, Locale locale) {
        return stream(advisor.explainElement(requester,
                new ExplainElementCommand(body.modelId(), body.elementId(), key(key), locale)));
    }

    /** План фрагмента архитектуры по описанию — предложение элементов и связей, модель не меняется. */
    @PostMapping("/plan")
    public ResponseEntity<SseEmitter> plan(Requester requester, @Valid @RequestBody PlanRequest body,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key, Locale locale) {
        return stream(advisor.planModel(requester,
                new PlanModelCommand(body.modelId(), body.prompt(), key(key), locale)));
    }

    /** Документация по модели, представлению или элементу — текст потоком SSE. */
    @PostMapping("/document")
    public ResponseEntity<SseEmitter> document(Requester requester, @Valid @RequestBody DocumentRequest body,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key, Locale locale) {
        return stream(advisor.generateDocumentation(requester, new GenerateDocumentationCommand(
                body.modelId(), body.viewId(), body.elementId(), key(key), locale)));
    }

    /**
     * Поток читается в фоне: поток запроса освобождается, кадры уходят по мере прихода.
     * Своего срока у потока нет — его задаёт срок асинхронного запроса в настройках.
     */
    private ResponseEntity<SseEmitter> stream(AdvisorStream stream) {
        SseEmitter emitter = new SseEmitter();
        executor.execute(() -> {
            try {
                stream.run(new SseWriter(emitter, json));
                emitter.complete();
            } catch (RuntimeException broken) { // NOPMD - фоновый поток: отказ закрывает поток клиента
                LOG.error("advisor stream failed", broken);
                emitter.completeWithError(broken);
            }
        });
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    private static String key(String key) {
        if (key == null || key.isBlank()) {
            throw AdvisorDomainException.invalid(Message.of(AdvisorMessages.FIELD_REQUIRED, IDEMPOTENCY_KEY));
        }
        return key;
    }
}
