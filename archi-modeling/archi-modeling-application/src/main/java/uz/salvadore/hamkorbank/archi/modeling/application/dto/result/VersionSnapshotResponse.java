package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.Arrays;
import java.util.Objects;

/**
 * Содержимое зафиксированной версии — то, что выгружается: не текущее состояние, а снимок,
 * сделанный при сохранении.
 *
 * @param xml несжатый {@code .archimate}
 */
public record VersionSnapshotResponse(ModelSummaryResponse model, long versionNo, byte[] xml) {

    public VersionSnapshotResponse {
        Objects.requireNonNull(model, "model");
        xml = Arrays.copyOf(xml, xml.length);
    }

    @Override
    public byte[] xml() {
        return Arrays.copyOf(xml, xml.length);
    }
}
