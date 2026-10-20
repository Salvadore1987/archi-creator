package uz.salvadore.hamkorbank.archi.interchange.application.dto.result;

/**
 * Находка импорта с текстом на языке запроса.
 *
 * @param severity {@code ERROR}, {@code WARNING} или {@code INFO}
 * @param archiId  объект, к которому относится находка; {@code null} — файл в целом
 * @param xmlLine  строка XML; {@code null} — неизвестна
 */
public record ImportFindingResponse(String severity, String code, String message, String archiId, Integer xmlLine) {
}
