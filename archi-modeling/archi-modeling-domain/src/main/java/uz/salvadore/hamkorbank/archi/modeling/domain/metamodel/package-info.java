/**
 * Метамодель ArchiMate 3.2: каталог типов, их слои и фазы, матрица допустимых
 * связей, правила вложенности.
 *
 * <p>Здесь знание о языке, а не о конкретной модели: какие типы бывают, какая
 * связь между ними разрешена, что подразумевает вложенность. Хранение типа
 * от каталога не зависит — {@link uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType}
 * принимает и тип, которого каталог не знает (FR-03).
 *
 * <p>Спецификация: spec/domain/modeling/aggregates.yaml#ArchiType, INV-MDL-007,
 * docs/backend.md §3.3 (пакет {@code metamodel}).
 */
package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;
