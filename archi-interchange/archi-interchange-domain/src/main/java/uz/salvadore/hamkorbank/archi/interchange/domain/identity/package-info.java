/**
 * Идентификаторы interchange на UUIDv7 и внешние идентификаторы modeling.
 *
 * <p>{@link uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId},
 * {@link uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId} и
 * {@link uz.salvadore.hamkorbank.archi.interchange.domain.identity.ViewId} принадлежат modeling
 * ({@code external_context} в aggregates.yaml), но объявлены здесь своими: доменные
 * модули не зависят друг от друга, и общий тип потребовал бы общего модуля.
 * Совпадает подлежащее значение — UUID, — и этого достаточно для обмена.
 */
package uz.salvadore.hamkorbank.archi.interchange.domain.identity;
