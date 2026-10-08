package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.List;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;

/** Модель со всеми представлениями — содержимое снимка версии и экспорта. */
public record ModelContent(ArchitectureModel model, List<View> views) {

    public ModelContent {
        Objects.requireNonNull(model, "model");
        views = List.copyOf(views);
    }
}
