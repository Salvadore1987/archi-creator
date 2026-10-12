package uz.salvadore.hamkorbank.archi.advisor.domain.context;

/**
 * Слой ArchiMate элемента в контексте. Порядок — приоритет при усечении: первыми
 * остаются стратегия, мотивация и бизнес, последними — технологии, физика
 * и реализация. Ревью без бизнес-слоя бессмысленно, без инфраструктуры — беднее,
 * но осмысленно.
 */
public enum ContextLayer {
    STRATEGY,
    MOTIVATION,
    BUSINESS,
    APPLICATION,
    TECHNOLOGY,
    PHYSICAL,
    IMPLEMENTATION,
    OTHER
}
