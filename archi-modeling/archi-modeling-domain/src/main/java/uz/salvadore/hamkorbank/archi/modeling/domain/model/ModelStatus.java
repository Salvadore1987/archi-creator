package uz.salvadore.hamkorbank.archi.modeling.domain.model;

/** Жизненный цикл модели: {@code ACTIVE ⇄ DELETED → PURGED}. */
public enum ModelStatus {
    ACTIVE,
    DELETED,
    PURGED
}
