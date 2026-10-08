package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import java.util.List;

/**
 * Файл повреждён (FR-50): отказ независимо от строгости импорта (INV-IXC-007).
 * Несёт все найденные дефекты, а не первый: архитектор чинит файл за один проход.
 */
public final class CorruptDocumentException extends RuntimeException {

    private final transient List<DocumentDefect> defects;

    public CorruptDocumentException(List<DocumentDefect> defects) {
        super(summary(defects));
        if (defects.isEmpty()) {
            throw new IllegalArgumentException("отказ без дефектов не объясняет ничего");
        }
        this.defects = List.copyOf(defects);
    }

    public List<DocumentDefect> defects() {
        return defects;
    }

    private static String summary(List<DocumentDefect> defects) {
        if (defects.isEmpty()) {
            return "";
        }
        DocumentDefect first = defects.getFirst();
        String place = first.line().map(l -> " (строка " + l + ")").orElse("");
        String more = defects.size() > 1 ? " и ещё " + (defects.size() - 1) : "";
        return "FR-50: файл повреждён — " + first.code() + ": " + first.message() + place + more;
    }
}
