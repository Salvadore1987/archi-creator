package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.ModelRows;

/** Собирается ли модель из строк — до того, как их записывать. */
public record VerifyModelRowsQuery(ModelRows rows) {

    public VerifyModelRowsQuery {
        Objects.requireNonNull(rows, "rows");
    }
}
