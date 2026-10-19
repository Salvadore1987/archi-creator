package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

/**
 * Допустимый тип связи для пары в порядке, в котором его предлагать.
 *
 * @param archiType тип в нотации Archi
 * @param type      имя типа связи
 * @param byDefault выбор по умолчанию — первый в списке
 * @param nesting   тип подразумевает вложение цели в источник
 */
public record RelationTypeResponse(String archiType, String type, boolean byDefault, boolean nesting) {
}
