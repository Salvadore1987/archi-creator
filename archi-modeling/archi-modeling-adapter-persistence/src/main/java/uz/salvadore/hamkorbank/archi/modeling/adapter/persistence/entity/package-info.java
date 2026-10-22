/**
 * JPA-сущности modeling — плоские строки без ассоциаций: ссылки хранятся ключами,
 * граф собирает маппер. Ассоциации JPA дали бы ленивые загрузки и каскады, которых
 * домен не просил, а дерево модели читается иначе — по таблице на запрос.
 */
@NullMarked
package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import org.jspecify.annotations.NullMarked;
