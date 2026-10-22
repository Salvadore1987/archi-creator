/**
 * Сценарии modeling — входящие порты. Каждый метод: проверка роли,
 * транзакция через {@code UnitOfWork}, список доступа модели, блокировка автора
 * для записи, события после коммита.
 */
@NullMarked
package uz.salvadore.hamkorbank.archi.modeling.application.service;

import org.jspecify.annotations.NullMarked;
