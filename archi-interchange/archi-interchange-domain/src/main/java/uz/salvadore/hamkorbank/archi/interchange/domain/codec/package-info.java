/**
 * Кодек {@code .archimate}: чтение в {@link uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument}
 * и запись обратно (docs/backend.md §3.4).
 *
 * <p>Лежит в доменном модуле: StAX — часть JDK, и правило enforcer'а
 * (ADR-0001) этим не нарушается. Порты — {@link uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader}
 * и {@link uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentWriter};
 * реализации детерминированы и не держат DOM.
 *
 * <p>Главный инвариант — INV-IXC-005, lossless round-trip.
 */
package uz.salvadore.hamkorbank.archi.interchange.domain.codec;
