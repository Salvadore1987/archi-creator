package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ConcurrentModificationException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.TrackedEntities;

/** Запись тронутых частей агрегата в строки — общая для модели и представлений. */
final class AggregateWrites {

    private AggregateWrites() {
    }

    @FunctionalInterface
    interface Mapping<V, E> {
        void apply(V domain, E entity);
    }

    /**
     * Тронутые сущности агрегата — в строки: новые вставляются, загруженные правятся, удалённые уходят.
     * Отметку «записано» ставит агрегат: вызывающий зовёт его {@code markPersisted()} после всех таблиц.
     */
    static <K extends BaseId<UUID>, V, E> void sync(EntityManager em, TrackedEntities<K, V> tracked, Class<E> type,
                                              Supplier<E> factory, Mapping<V, E> mapping) {
        for (K key : tracked.removals()) {
            E entity = em.find(type, key.value());
            if (entity != null) {
                em.remove(entity);
            }
        }
        for (Map.Entry<K, V> change : tracked.upserts().entrySet()) {
            E entity = tracked.isPersisted(change.getKey()) ? em.find(type, change.getKey().value()) : null;
            if (entity == null) {
                E created = factory.get();
                mapping.apply(change.getValue(), created);
                em.persist(created);
            } else {
                mapping.apply(change.getValue(), entity);
            }
        }
    }

    /**
     * Сброс сейчас, а не на коммите: конфликт версии должен стать отказом домена внутри
     * сценария, а не исключением транзакционного шаблона, которого сценарий не видит.
     */
    static void flush(EntityManager em, Message what) {
        try {
            em.flush();
        } catch (OptimisticLockException e) {
            throw new ConcurrentModificationException(what);
        } catch (PersistenceException e) {
            if (e.getCause() instanceof org.hibernate.StaleStateException) {
                throw new ConcurrentModificationException(what);
            }
            throw e;
        }
    }
}
