package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/** Общие заготовки для тестов сессии импорта. */
final class Sessions {

    static final Instant NOW = Instant.parse("2026-10-08T09:00:00Z");
    static final UuidV7 UUIDS = new UuidV7(Clock.fixed(NOW, ZoneOffset.UTC));
    static final WorkspaceId WORKSPACE = new WorkspaceId(UUID.fromString("0192f000-0000-7000-8000-000000000001"));

    static final String VALID = """
            <?xml version="1.0" encoding="UTF-8"?>
            <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
            xmlns:archimate="http://www.archimatetool.com/archimate" name="М" id="id-m1" version="5.0.0">
              <folder name="Business" id="id-f2" type="business">
                <element xsi:type="archimate:BusinessActor" name="Клиент" id="id-e1"/>
                <element xsi:type="archimate:ApplicationComponent" name="ABS" id="id-e2"/>
              </folder>
              <folder name="Relations" id="id-f7" type="relations">
                <element xsi:type="archimate:CompositionRelationship" id="id-r1" source="id-e1" target="id-e2"/>
              </folder>
            </archimate:model>
            """;

    static final String CORRUPT = VALID.replace("target=\"id-e2\"", "target=\"id-missing\"");

    private Sessions() {
    }

    static ImportSession received(String xml, boolean strict) {
        return ImportSession.receive(ImportSessionId.next(UUIDS), request(xml, "key-1", strict), NOW);
    }

    static ImportRequest request(String xml, String key, boolean strict) {
        return ImportRequest.of(WORKSPACE, Optional.empty(), "model.archimate",
                xml.getBytes(StandardCharsets.UTF_8), key, strict, "architect");
    }

    static void parse(ImportSession session, String xml) {
        session.parse(new StaxArchiDocumentReader(), new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                () -> FindingId.next(UUIDS), NOW);
    }

    /** Нарушение матрицы, каким его отдаст проверка методологии: ERROR, RELATION_NOT_PERMITTED. */
    static ImportFinding matrixViolation() {
        return new ImportFinding(FindingId.next(UUIDS), Severity.ERROR, "RELATION_NOT_PERMITTED",
                "Composition от BusinessActor к ApplicationComponent не допускается",
                Optional.of(ArchiId.of("id-r1")), Optional.of(8));
    }
}
