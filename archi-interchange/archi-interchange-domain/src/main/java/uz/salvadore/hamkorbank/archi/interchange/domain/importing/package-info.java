/**
 * Импорт файла: сессия как единица отчётности (UC-IXC-001).
 *
 * <p>{@link uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession}
 * хранит отчёт целиком, в том числе у отклонённых попыток: отказ без объяснения
 * превращает строгий импорт в чёрный ящик.
 *
 * <p>Инварианты: INV-IXC-002 (переходы), INV-IXC-003 (идемпотентность),
 * INV-IXC-007 (строгость и повреждения).
 */
package uz.salvadore.hamkorbank.archi.interchange.domain.importing;
