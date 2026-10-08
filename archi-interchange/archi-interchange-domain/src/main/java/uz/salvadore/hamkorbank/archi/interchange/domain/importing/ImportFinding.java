package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.DocumentDefect;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;

/**
 * Замечание к импортируемому файлу с привязкой к месту: объект и строка XML.
 *
 * @param code например {@code RELATION_NOT_PERMITTED} или {@code IXC_DANGLING_REFERENCE}
 */
public record ImportFinding(FindingId id, Severity severity, String code, String message,
                            Optional<ArchiId> archiId, Optional<Integer> xmlLine) {

    public ImportFinding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(xmlLine, "xmlLine");
        if (code.isBlank()) {
            throw new IllegalArgumentException("находка без кода");
        }
    }

    /**
     * Повреждение данных как находка. Всегда {@link Severity#ERROR}; идентификатор,
     * который сам и повреждён, в {@code archiId} не попадает — он остаётся в сообщении.
     */
    public static ImportFinding of(FindingId id, DocumentDefect defect) {
        Optional<ArchiId> archiId = defect.archiId().filter(ArchiId::isValid).map(ArchiId::of);
        return new ImportFinding(id, Severity.ERROR, defect.code(), defect.message(), archiId, defect.line());
    }
}
