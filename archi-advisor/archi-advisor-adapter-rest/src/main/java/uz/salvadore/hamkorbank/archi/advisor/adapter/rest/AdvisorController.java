package uz.salvadore.hamkorbank.archi.advisor.adapter.rest;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
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
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorService;
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorStream;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;

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
    /** Дольше самого долгого обращения: документация по модели — до пяти минут. */
    private static final long STREAM_TIMEOUT_MS = 330_000;

    private final AdvisorService advisor;
    private final JsonMapper json;
    private final AsyncTaskExecutor executor;

    public AdvisorController(AdvisorService advisor, JsonMapper json,
                             @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.advisor = advisor;
        this.json = json;
        this.executor = executor;
    }

    /** Ревью модели или одного представления — замечания потоком SSE. */
    @PostMapping("/review")
    public ResponseEntity<SseEmitter> review(Requester requester, @RequestBody ReviewRequest body,
                                                        @RequestHeader(IDEMPOTENCY_KEY) Optional<String> key,
                                                        Locale locale) {
        return stream(advisor.ask(requester, AdvisorRequest.review(model(body.modelId()), view(body.viewId())),
                key(key), locale));
    }

    /** Подсказка по выбранному элементу и его окрестности — ответ потоком SSE. */
    @PostMapping("/explain")
    public ResponseEntity<SseEmitter> explain(Requester requester, @RequestBody ExplainRequest body,
                                                         @RequestHeader(IDEMPOTENCY_KEY) Optional<String> key,
                                                         Locale locale) {
        return stream(advisor.ask(requester, AdvisorRequest.explain(model(body.modelId()), body.elementId()),
                key(key), locale));
    }

    /** План фрагмента архитектуры по описанию — предложение элементов и связей, модель не меняется. */
    @PostMapping("/plan")
    public ResponseEntity<SseEmitter> plan(Requester requester, @RequestBody PlanRequest body,
                                                      @RequestHeader(IDEMPOTENCY_KEY) Optional<String> key,
                                                      Locale locale) {
        return stream(advisor.ask(requester, AdvisorRequest.plan(model(body.modelId()), body.prompt()), key(key),
                locale));
    }

    /** Документация по модели, представлению или элементу — текст потоком SSE. */
    @PostMapping("/document")
    public ResponseEntity<SseEmitter> document(Requester requester, @RequestBody DocumentRequest body,
                                                          @RequestHeader(IDEMPOTENCY_KEY) Optional<String> key,
                                                          Locale locale) {
        return stream(advisor.ask(requester, AdvisorRequest.document(model(body.modelId()), view(body.viewId()),
                Optional.ofNullable(body.elementId())), key(key), locale));
    }

    /** Поток читается в фоне: поток запроса освобождается, кадры уходят по мере прихода. */
    private ResponseEntity<SseEmitter> stream(AdvisorStream stream) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        executor.execute(() -> {
            try {
                stream.run(new SseWriter(emitter, json));
                emitter.complete();
            } catch (RuntimeException broken) {
                LOG.error("advisor stream failed", broken);
                emitter.completeWithError(broken);
            }
        });
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    private static ModelId model(UUID modelId) {
        if (modelId == null) {
            throw AdvisorDomainException.invalid(Message.of(AdvisorMessages.FIELD_REQUIRED, "modelId"));
        }
        return ModelId.of(modelId);
    }

    private static Optional<ViewId> view(UUID viewId) {
        return Optional.ofNullable(viewId).map(ViewId::of);
    }

    private static String key(Optional<String> key) {
        return key.filter(k -> !k.isBlank()).orElseThrow(
                () -> AdvisorDomainException.invalid(Message.of(AdvisorMessages.FIELD_REQUIRED, IDEMPOTENCY_KEY)));
    }
}
