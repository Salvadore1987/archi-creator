package uz.salvadore.hamkorbank.archi.modeling.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;

class SnapshotsTest {

    @Test
    @DisplayName("повреждённый снимок версии — отказ с кодом и видом «дефект», а не голое исключение ввода-вывода")
    void corruptedSnapshotIsDefectWithCode() {
        ModelingDomainException corrupted = assertThrows(ModelingDomainException.class,
                () -> Snapshots.gunzip("не gzip".getBytes(StandardCharsets.UTF_8)));

        assertEquals(ModelingCodes.SNAPSHOT_CORRUPTED, corrupted.code());
        assertEquals(Failure.DEFECT, corrupted.failure());
    }

    @Test
    @DisplayName("снимок переживает сжатие и распаковку")
    void snapshotSurvivesCompression() {
        byte[] xml = "<model/>".getBytes(StandardCharsets.UTF_8);

        assertEquals(new String(xml, StandardCharsets.UTF_8),
                new String(Snapshots.gunzip(Snapshots.gzip(xml)), StandardCharsets.UTF_8));
    }
}
