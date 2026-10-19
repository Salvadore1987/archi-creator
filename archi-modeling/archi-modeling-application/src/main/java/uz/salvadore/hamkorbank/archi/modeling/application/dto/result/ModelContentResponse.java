package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.Arrays;

/**
 * Текущее содержимое модели и её последняя версия.
 *
 * @param versionNo последняя версия; у модели без версий {@code null}
 * @param xml       {@code .archimate}, собранный так же, как при сохранении
 */
public record ModelContentResponse(ModelSummaryResponse model, Long versionNo, byte[] xml) {

    public ModelContentResponse {
        xml = Arrays.copyOf(xml, xml.length);
    }

    @Override
    public byte[] xml() {
        return Arrays.copyOf(xml, xml.length);
    }
}
