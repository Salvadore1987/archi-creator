package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.Arrays;
import java.util.Objects;

/**
 * Версия вместе со снимком — то, что публикуется в Git: автор и комментарий становятся
 * автором и сообщением коммита.
 *
 * @param xml несжатый {@code .archimate}
 */
public record CommittedVersionResponse(ModelSummaryResponse model, VersionResponse version, byte[] xml) {

    public CommittedVersionResponse {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(version, "version");
        xml = Arrays.copyOf(xml, xml.length);
    }

    @Override
    public byte[] xml() {
        return Arrays.copyOf(xml, xml.length);
    }
}
