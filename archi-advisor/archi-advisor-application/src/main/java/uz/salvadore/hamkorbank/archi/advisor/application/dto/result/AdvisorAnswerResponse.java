package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

import java.util.List;

/** Ответ подсказки или документации: Markdown и объекты модели, на которые он ссылается. */
public record AdvisorAnswerResponse(String markdown, List<ElementRefResponse> references) {
}
