package uz.salvadore.hamkorbank.archi.advisor.adapter.rest;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.json.JsonMapper;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.DocumentRequest;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.ExplainRequest;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.PlanRequest;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.ReviewRequest;
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorService;
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorStream;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorException;
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

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final AdvisorService advisor;
    private final JsonMapper json;

    public AdvisorController(AdvisorService advisor, JsonMapper json) {
        this.advisor = advisor;
        this.json = json;
    }

    @PostMapping("/review")
    public ResponseEntity<StreamingResponseBody> review(Requester requester, @RequestBody ReviewRequest body,
                                                        @RequestHeader(IDEMPOTENCY_KEY) Optional<String> key,
                                                        Locale locale) {
        return stream(advisor.ask(requester, AdvisorRequest.review(model(body.modelId()), view(body.viewId())),
                key(key), locale));
    }

    @PostMapping("/explain")
    public ResponseEntity<StreamingResponseBody> explain(Requester requester, @RequestBody ExplainRequest body,
                                                         @RequestHeader(IDEMPOTENCY_KEY) Optional<String> key,
                                                         Locale locale) {
        return stream(advisor.ask(requester, AdvisorRequest.explain(model(body.modelId()), body.elementId()),
                key(key), locale));
    }

    @PostMapping("/plan")
    public ResponseEntity<StreamingResponseBody> plan(Requester requester, @RequestBody PlanRequest body,
                                                      @RequestHeader(IDEMPOTENCY_KEY) Optional<String> key,
                                                      Locale locale) {
        return stream(advisor.ask(requester, AdvisorRequest.plan(model(body.modelId()), body.prompt()), key(key),
                locale));
    }

    @PostMapping("/document")
    public ResponseEntity<StreamingResponseBody> document(Requester requester, @RequestBody DocumentRequest body,
                                                          @RequestHeader(IDEMPOTENCY_KEY) Optional<String> key,
                                                          Locale locale) {
        return stream(advisor.ask(requester, AdvisorRequest.document(model(body.modelId()), view(body.viewId()),
                Optional.ofNullable(body.elementId())), key(key), locale));
    }

    private ResponseEntity<StreamingResponseBody> stream(AdvisorStream stream) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(out -> stream.run(new SseWriter(out, json)));
    }

    private static ModelId model(UUID modelId) {
        if (modelId == null) {
            throw AdvisorException.invalid(Message.of(AdvisorMessages.FIELD_REQUIRED, "modelId"));
        }
        return ModelId.of(modelId);
    }

    private static Optional<ViewId> view(UUID viewId) {
        return Optional.ofNullable(viewId).map(ViewId::of);
    }

    private static String key(Optional<String> key) {
        return key.filter(k -> !k.isBlank()).orElseThrow(
                () -> AdvisorException.invalid(Message.of(AdvisorMessages.FIELD_REQUIRED, IDEMPOTENCY_KEY)));
    }
}
