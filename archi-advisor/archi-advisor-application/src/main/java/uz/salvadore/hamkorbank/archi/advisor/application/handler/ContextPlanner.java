package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ModelContext;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ModelGraph;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;

/**
 * Область модели под функцию и её усечение до предела. Подсказке — элемент и соседи
 * на два шага, при переполнении на один; анализу влияния — на три; плану — каталог
 * имён; ревью и документации — модель или представление.
 */
final class ContextPlanner {

    static final int EXPLAIN_HOPS = 2;
    static final int IMPACT_HOPS = 3;

    private final AdvisorPolicy policy;

    ContextPlanner(AdvisorPolicy policy) {
        this.policy = policy;
    }

    /**
     * Контекст запроса в пределах размера. Пусто — он не помещается в предел даже без
     * элементов или, усечённый, покрывает меньше допустимой доли области: нужно сузить область.
     */
    Optional<ModelContext> plan(AdvisorRequest request, ModelGraph graph) {
        ModelContext scoped = switch (request.function()) {
            case REVIEW -> Optional.ofNullable(request.viewId()).map(v -> graph.ofView(request.function(), v)
                    .orElseThrow(() -> AdvisorDomainException.notFound(Message.of(AdvisorMessages.VIEW, v))))
                    .orElseGet(() -> graph.whole(request.function()));
            case EXPLAIN -> {
                String element = Objects.requireNonNull(request.elementId());
                ModelContext wide = around(request, graph, element, EXPLAIN_HOPS);
                yield wide.fit(policy.maxContextTokens()).map(ModelContext::truncated).orElse(true)
                        ? around(request, graph, element, 1) : wide;
            }
            case PLAN -> graph.catalog(request.function());
            case DOCUMENT -> {
                if (request.viewId() != null) {
                    yield graph.ofView(request.function(), request.viewId()).orElseThrow(
                            () -> AdvisorDomainException.notFound(
                                    Message.of(AdvisorMessages.VIEW, request.viewId())));
                }
                yield Optional.ofNullable(request.elementId()).map(e -> around(request, graph, e, IMPACT_HOPS))
                        .orElseGet(() -> graph.whole(request.function()));
            }
        };
        return scoped.fit(policy.maxContextTokens()).filter(fitted -> fitted.coverage() >= policy.minCoverage());
    }

    private static ModelContext around(AdvisorRequest request, ModelGraph graph, String element, int hops) {
        return graph.around(request.function(), element, hops)
                .orElseThrow(() -> AdvisorDomainException.notFound(Message.of(AdvisorMessages.ELEMENT, element)));
    }
}
