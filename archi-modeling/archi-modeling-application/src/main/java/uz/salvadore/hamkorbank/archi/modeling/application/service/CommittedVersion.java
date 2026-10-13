package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.Arrays;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Версия вместе со снимком — то, что публикуется в Git: автор и комментарий
 * становятся автором и сообщением коммита.
 *
 * @param xml несжатый {@code .archimate}
 */
public record CommittedVersion(ModelHeader model, ModelVersion version, byte[] xml) {

    public CommittedVersion {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(version, "version");
        xml = Arrays.copyOf(xml, xml.length);
    }

    @Override
    public byte[] xml() {
        return Arrays.copyOf(xml, xml.length);
    }
}
