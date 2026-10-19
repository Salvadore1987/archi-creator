package uz.salvadore.hamkorbank.archi.advisor.domain.common;

/**
 * Корень агрегата — граница согласованности: правила агрегата держит он сам,
 * изменения идут через его бизнес-методы, а хранилище читает и пишет его целиком.
 *
 * @param <T> тип идентификатора
 */
public abstract class AggregateRoot<T> extends BaseEntity<T> {

    /** Корень с идентификатором. */
    protected AggregateRoot(T id) {
        super(id);
    }
}
