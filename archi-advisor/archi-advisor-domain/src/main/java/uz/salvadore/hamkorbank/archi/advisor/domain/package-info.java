/**
 * Доменная модель bounded context'а advisor (ADV).
 *
 * <p>Агрегаты, сущности и value objects. У каждого инварианта
 * есть код и тест, названный по этому коду.
 *
 * <p>Фреймворка здесь нет: ни Spring, ни JPA. Граница держится
 * сборкой — в pom.xml модуля таких зависимостей нет, а enforcer не даёт их вернуть.
 */
package uz.salvadore.hamkorbank.archi.advisor.domain;
