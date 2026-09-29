/**
 * Доменная модель bounded context'а modeling (MDL).
 *
 * <p>Агрегаты, сущности и value objects. Инварианты — INV-MDL-NNN
 * в spec/domain/modeling/invariants.md; каждый имеет тест, названный по коду.
 *
 * <p>Фреймворка здесь нет по ADR-0001: ни Spring, ни JPA. Граница держится
 * сборкой — в pom.xml модуля таких зависимостей нет, а enforcer не даёт их вернуть.
 *
 * <p>Спецификация: spec/domain/modeling/aggregates.yaml
 */
package uz.salvadore.hamkorbank.archi.modeling.domain;
