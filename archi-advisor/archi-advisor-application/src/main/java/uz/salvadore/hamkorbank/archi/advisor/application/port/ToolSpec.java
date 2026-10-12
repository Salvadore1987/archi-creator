package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.Map;
import java.util.Objects;

/**
 * Инструмент со строгой JSON-схемой: через его вызов модель отдаёт находку или план
 * валидной структурой, без разбора свободного текста.
 *
 * @param inputSchema JSON-схема входа: вложенные {@code Map}, {@code List}, строки,
 *                    числа и булевы значения
 */
public record ToolSpec(String name, String description, Map<String, Object> inputSchema) {

    public ToolSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        inputSchema = Map.copyOf(inputSchema);
    }
}
