/**
 * Доменная модель bounded context'а interchange (IXC).
 *
 * <p>Агрегаты, сущности и value objects. Инварианты — INV-IXC-NNN
 * в spec/domain/interchange/invariants.md; каждый имеет тест, названный по коду.
 *
 * <p>Фреймворка здесь нет по ADR-0001: ни Spring, ни JPA. Граница держится
 * сборкой — в pom.xml модуля таких зависимостей нет, а enforcer не даёт их вернуть.
 *
 * <p>Спецификация: spec/domain/interchange/aggregates.yaml
 */
package uz.salvadore.hamkorbank.archi.interchange.domain;
