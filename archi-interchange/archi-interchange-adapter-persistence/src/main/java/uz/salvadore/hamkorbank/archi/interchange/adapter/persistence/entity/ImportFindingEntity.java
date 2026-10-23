package uz.salvadore.hamkorbank.archi.interchange.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Строка находки сессии импорта: уровень, код, текст и место в файле. */
@Entity
@Table(name = "import_finding")
public class ImportFindingEntity {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    @Column(name = "ordinal", nullable = false)
    public int ordinal;

    @Column(name = "severity", nullable = false)
    public String severity;

    @Column(name = "code", nullable = false)
    public String code;

    @Column(name = "message", nullable = false)
    public String message;

    @Column(name = "archi_id")
    public @Nullable String archiId;

    @Column(name = "xml_line")
    public @Nullable Integer xmlLine;
}
