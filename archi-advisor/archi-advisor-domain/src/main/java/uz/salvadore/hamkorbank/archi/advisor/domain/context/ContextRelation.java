package uz.salvadore.hamkorbank.archi.advisor.domain.context;

import java.util.Objects;

/** Связь модели в контексте: тип и концы по {@code archiId}. */
public record ContextRelation(String archiId, String archiType, String sourceArchiId, String targetArchiId) {

    public ContextRelation {
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(sourceArchiId, "sourceArchiId");
        Objects.requireNonNull(targetArchiId, "targetArchiId");
    }
}
