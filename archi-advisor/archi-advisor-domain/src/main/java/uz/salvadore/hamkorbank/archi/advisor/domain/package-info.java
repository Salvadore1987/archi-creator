/**
 * Доменная модель bounded context'а advisor (ADV).
 *
 * <p>Агрегаты, сущности и value objects. Инварианты — INV-ADV-NNN
 * в spec/domain/advisor/invariants.md; каждый имеет тест, названный по коду.
 *
 * <p>Фреймворка здесь нет по ADR-0001: ни Spring, ни JPA. Граница держится
 * сборкой — в pom.xml модуля таких зависимостей нет, а enforcer не даёт их вернуть.
 *
 * <p>Спецификация: spec/domain/advisor/aggregates.yaml
 */
package uz.salvadore.hamkorbank.archi.advisor.domain;
