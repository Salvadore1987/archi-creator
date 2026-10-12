/**
 * Кодек {@code .archimate}: чтение в {@link uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument}
 * и запись обратно.
 *
 * <p>Лежит в доменном модуле: StAX — часть JDK, и правило enforcer'а
 * этим не нарушается. Порты — {@link uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader}
 * и {@link uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentWriter};
 * реализации детерминированы и не держат DOM.
 *
 * <p>Главный инвариант — lossless round-trip: документ переживает запись и чтение
 * без изменений.
 */
package uz.salvadore.hamkorbank.archi.interchange.domain.codec;
