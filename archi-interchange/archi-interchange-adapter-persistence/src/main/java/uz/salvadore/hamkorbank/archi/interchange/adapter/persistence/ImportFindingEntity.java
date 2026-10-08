package uz.salvadore.hamkorbank.archi.interchange.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "import_finding")
public class ImportFindingEntity {

    @Id
    public UUID id;

    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    @Column(nullable = false)
    public int ordinal;

    @Column(nullable = false)
    public String severity;

    @Column(nullable = false)
    public String code;

    @Column(nullable = false)
    public String message;

    @Column(name = "archi_id")
    public String archiId;

    @Column(name = "xml_line")
    public Integer xmlLine;
}
