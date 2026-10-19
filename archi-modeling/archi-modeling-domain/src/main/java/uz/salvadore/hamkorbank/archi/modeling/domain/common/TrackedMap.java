package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Сущности агрегата с учётом изменений: что добавлено или изменено и что удалено
 * с момента загрузки. Хранилище пишет только изменённое — модель на тысячи строк
 * не переписывается целиком ради одного переименования.
 *
 * <p>Порядок — порядок вставки: в нём сущности загружены из хранилища.
 */
public final class TrackedMap<K, V> implements TrackedEntities<K, V> {

    private final Map<K, V> values = new LinkedHashMap<>();
    private final Set<K> persisted = new HashSet<>();
    private final Set<K> upserted = new LinkedHashSet<>();
    private final Set<K> removed = new LinkedHashSet<>();

    /** Загрузка из хранилища: изменением не считается. */
    public void load(K key, V value) {
        values.put(key, value);
        persisted.add(key);
    }

    /** Добавление или замена: запись помечается изменённой, отметка об удалении снимается. */
    public void put(K key, V value) {
        values.put(key, value);
        upserted.add(key);
        removed.remove(key);
    }

    /** Удаление; хранилище узнает о нём, только если сущность была в нём сохранена. */
    public void remove(K key) {
        if (values.remove(key) != null) {
            upserted.remove(key);
            if (persisted.contains(key)) {
                removed.add(key);
            }
        }
    }

    /** Сущность по ключу; пусто — её нет или она удалена. */
    @Override
    public Optional<V> get(K key) {
        return Optional.ofNullable(values.get(key));
    }

    /** Есть ли сущность с таким ключом сейчас. */
    @Override
    public boolean contains(K key) {
        return values.containsKey(key);
    }

    /** Текущие сущности в порядке вставки. */
    @Override
    public Collection<V> values() {
        return Collections.unmodifiableCollection(values.values());
    }

    /** Число текущих сущностей. */
    @Override
    public int size() {
        return values.size();
    }

    /** Добавленные и изменённые после загрузки, в порядке изменения. */
    @Override
    public Map<K, V> upserts() {
        Map<K, V> result = new LinkedHashMap<>();
        upserted.forEach(k -> result.put(k, values.get(k)));
        return result;
    }

    /** Те, что были в хранилище и удалены. */
    @Override
    public Set<K> removals() {
        return Set.copyOf(removed);
    }

    /** Есть ли ещё в хранилище: обновлять, а не вставлять. */
    @Override
    public boolean isPersisted(K key) {
        return persisted.contains(key);
    }

    /** Есть ли что записать: добавленные, изменённые или удалённые с момента загрузки. */
    @Override
    public boolean changed() {
        return !upserted.isEmpty() || !removed.isEmpty();
    }

    /** Хранилище записало изменения: всё текущее теперь сохранено. */
    public void markPersisted() {
        persisted.removeAll(removed);
        persisted.addAll(upserted);
        upserted.clear();
        removed.clear();
    }
}
