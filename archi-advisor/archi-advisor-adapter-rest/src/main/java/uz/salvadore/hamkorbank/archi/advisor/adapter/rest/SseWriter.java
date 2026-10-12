package uz.salvadore.hamkorbank.archi.advisor.adapter.rest;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.TokenDto;
import uz.salvadore.hamkorbank.archi.advisor.application.service.SessionMeta;
import uz.salvadore.hamkorbank.archi.advisor.application.service.StreamSink;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorAnswer;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.Finding;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.ModelPlan;

/**
 * Кадры SSE в тело ответа: {@code event: <тип>} и одна строка {@code data:} с JSON,
 * каждый кадр — сразу в сеть. Ошибка записи значит, что клиент ушёл: она
 * прерывает поток, и сценарий завершает сессию.
 */
final class SseWriter implements StreamSink {

    private final OutputStream out;
    private final JsonMapper json;

    SseWriter(OutputStream out, JsonMapper json) {
        this.out = out;
        this.json = json;
    }

    @Override
    public void token(String delta) {
        frame("token", new TokenDto(delta));
    }

    @Override
    public void finding(Finding finding) {
        frame("finding", DtoMapper.finding(finding));
    }

    @Override
    public void plan(ModelPlan plan) {
        frame("plan", DtoMapper.plan(plan));
    }

    @Override
    public void answer(AdvisorAnswer answer) {
        frame("answer", DtoMapper.answer(answer));
    }

    @Override
    public void meta(SessionMeta meta) {
        frame("meta", DtoMapper.meta(meta));
    }

    private void frame(String event, Object data) {
        try {
            out.write(("event: " + event + "\ndata: " + json.writeValueAsString(data) + "\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException gone) {
            throw new UncheckedIOException(gone);
        }
    }
}
