package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.Arrays;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;

/**
 * Содержимое зафиксированной версии — то, что выгружается: не текущее
 * состояние, а снимок, сделанный при сохранении.
 *
 * @param xml несжатый {@code .archimate}
 */
public record VersionSnapshot(ModelHeader model, long versionNo, byte[] xml) {

    public VersionSnapshot {
        Objects.requireNonNull(model, "model");
        xml = Arrays.copyOf(xml, xml.length);
    }

    @Override
    public byte[] xml() {
        return Arrays.copyOf(xml, xml.length);
    }
}
