/**
 * Доменная модель bounded context'а interchange (IXC).
 *
 * <p>Агрегаты, сущности и value objects. Каждый инвариант имеет тест, названный
 * по его коду.
 *
 * <p>Фреймворка здесь нет: ни Spring, ни JPA. Граница держится сборкой —
 * в pom.xml модуля таких зависимостей нет, а enforcer не даёт их вернуть.
 */
package uz.salvadore.hamkorbank.archi.interchange.domain;
