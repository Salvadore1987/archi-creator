package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Собственные поля модели без её содержимого — то, что лежит в строке {@code model}.
 *
 * @param createdBy subject автора
 * @param version   оптимистичная блокировка строки (NFR-07)
 */
public record ModelHeader(ModelId id, WorkspaceId workspaceId, ArchiId archiId, String name,
                          Optional<String> documentation, String archiVersion, ModelStatus status,
                          List<PropertyEntry> properties, Optional<RawXml> rawXml, String createdBy,
                          Instant createdAt, Instant updatedAt, long version) {

    public static final String DEFAULT_ARCHI_VERSION = "5.0.0";
    private static final Pattern ARCHI_VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    public ModelHeader {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(documentation, "documentation");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(rawXml, "rawXml");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Names.limited(name, "модели");
        properties = List.copyOf(properties);
        if (archiVersion == null || !ARCHI_VERSION.matcher(archiVersion).matches()) {
            throw new IllegalArgumentException("версия формата Archi вида N.N.N, получено: " + archiVersion);
        }
    }

    public static boolean validArchiVersion(String value) {
        return value != null && ARCHI_VERSION.matcher(value).matches();
    }
}
