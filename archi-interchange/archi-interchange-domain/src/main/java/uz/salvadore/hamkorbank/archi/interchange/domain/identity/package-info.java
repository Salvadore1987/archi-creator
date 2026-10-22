/**
 * Идентификаторы interchange на UUIDv7 и внешние идентификаторы modeling.
 *
 * <p>{@link uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId},
 * {@link uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId} и
 * {@link uz.salvadore.hamkorbank.archi.interchange.domain.identity.ViewId} принадлежат modeling
 * и выдаются там, но объявлены здесь своими: доменные
 * модули не зависят друг от друга, и общий тип потребовал бы общего модуля.
 * Совпадает подлежащее значение — UUID, — и этого достаточно для обмена.
 */
@NullMarked
package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import org.jspecify.annotations.NullMarked;
