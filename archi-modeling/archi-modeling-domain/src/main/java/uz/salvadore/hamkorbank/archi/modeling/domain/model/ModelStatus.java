package uz.salvadore.hamkorbank.archi.modeling.domain.model;

/** Жизненный цикл модели (INV-MDL-002): {@code ACTIVE ⇄ DELETED → PURGED}. */
public enum ModelStatus {
    ACTIVE,
    DELETED,
    PURGED
}
