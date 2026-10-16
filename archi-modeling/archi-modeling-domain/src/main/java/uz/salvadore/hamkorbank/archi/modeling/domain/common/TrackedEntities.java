package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Сущности агрегата глазами остального кода: чтение и то, что записать в хранилище.
 * Добавляет, заменяет и удаляет сущности только сам агрегат — через свои бизнес-методы,
 * с проверкой своих правил.
 *
 * @param <K> ключ сущности
 * @param <V> сущность
 */
public interface TrackedEntities<K, V> {

    /** Сущность по ключу; пусто — её нет или она удалена. */
    Optional<V> get(K key);

    /** Есть ли сущность с таким ключом сейчас. */
    boolean contains(K key);

    /** Текущие сущности в порядке вставки; только для чтения. */
    Collection<V> values();

    /** Число текущих сущностей. */
    int size();

    /** Добавленные и изменённые после загрузки, в порядке изменения. */
    Map<K, V> upserts();

    /** Те, что были в хранилище и удалены. */
    Set<K> removals();

    /** Есть ли ещё в хранилище: обновлять, а не вставлять. */
    boolean isPersisted(K key);

    /** Есть ли что записать: добавленные, изменённые или удалённые с момента загрузки. */
    boolean changed();
}
