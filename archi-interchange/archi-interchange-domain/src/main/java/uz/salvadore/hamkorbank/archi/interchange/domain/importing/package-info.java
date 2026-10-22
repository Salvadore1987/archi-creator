/**
 * Импорт файла: сессия как единица отчётности.
 *
 * <p>{@link uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession}
 * хранит отчёт целиком, в том числе у отклонённых попыток: отказ без объяснения
 * превращает строгий импорт в чёрный ящик.
 *
 * <p>Сессия держит допустимые переходы, идемпотентность приёма и отношение
 * к строгости и повреждениям.
 */
@NullMarked
package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import org.jspecify.annotations.NullMarked;
