package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.util.List;
import java.util.Objects;

/**
 * Ответ подсказки и документации: Markdown и ссылки на объекты модели отдельно,
 * чтобы интерфейс сделал их кликабельными.
 */
public record AdvisorAnswer(String markdown, List<ElementRef> references) {

    public AdvisorAnswer {
        Objects.requireNonNull(markdown, "markdown");
        references = List.copyOf(references);
    }
}
