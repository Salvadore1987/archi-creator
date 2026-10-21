package uz.salvadore.hamkorbank.archi.advisor.adapter.rest;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.TokenDto;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorAnswerResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.FindingResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.ModelPlanResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.SessionMetaResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.StreamSink;

/**
 * Кадры SSE: {@code event:<тип>} и одна строка {@code data:} с JSON, каждый кадр —
 * сразу в сеть. {@link SseEmitter}, а не {@code StreamingResponseBody}: тот в Spring 7
 * получает поток, чей {@code flush()} ничего не делает, и ответ уходит одним куском
 * в конце. Ошибка записи значит, что клиент ушёл: она прерывает поток, и сценарий
 * завершает сессию.
 */
final class SseWriter implements StreamSink {

    private final SseEmitter emitter;
    private final JsonMapper json;

    SseWriter(SseEmitter emitter, JsonMapper json) {
        this.emitter = emitter;
        this.json = json;
    }

    @Override
    public void token(String delta) {
        frame("token", new TokenDto(delta));
    }

    @Override
    public void finding(FindingResponse finding) {
        frame("finding", DtoMapper.finding(finding));
    }

    @Override
    public void plan(ModelPlanResponse plan) {
        frame("plan", DtoMapper.plan(plan));
    }

    @Override
    public void answer(AdvisorAnswerResponse answer) {
        frame("answer", DtoMapper.answer(answer));
    }

    @Override
    public void meta(SessionMetaResponse meta) {
        frame("meta", DtoMapper.meta(meta));
    }

    private void frame(String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event)
                    .data(json.writeValueAsString(data), MediaType.APPLICATION_JSON));
        } catch (IOException gone) {
            throw new UncheckedIOException(gone);
        }
    }
}
