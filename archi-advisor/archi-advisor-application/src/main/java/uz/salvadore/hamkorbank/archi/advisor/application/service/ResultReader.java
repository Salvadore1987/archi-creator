package uz.salvadore.hamkorbank.archi.advisor.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.advisor.application.port.MetamodelCatalog;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ContextElement;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ModelContext;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorAnswer;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.ElementRef;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.Finding;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.ModelPlan;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.PlannedElement;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.PlannedRelation;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.Severity;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TargetKind;

/**
 * Разбор результата модели в типы домена. Модели не верят на слово: находка, чья
 * цель не нашлась в контексте, отбрасывается — перейти к ней нельзя; тип плана вне
 * фазы 1 отбрасывается; допустимость связи плана решает матрица, а не модель; ссылка
 * ответа остаётся, только если такой объект в контексте есть.
 */
final class ResultReader {

    /** Ссылка на объект модели в Markdown ответа: {@code [имя](archi:идентификатор)}. */
    private static final Pattern REFERENCE = Pattern.compile("\\]\\(archi:([A-Za-z0-9_][A-Za-z0-9_.-]{0,254})\\)");
    private static final Pattern CODE = Pattern.compile("[^A-Z0-9_]");

    private final MetamodelCatalog metamodel;
    private final Supplier<FindingId> findingIds;

    ResultReader(MetamodelCatalog metamodel, Supplier<FindingId> findingIds) {
        this.metamodel = metamodel;
        this.findingIds = findingIds;
    }

    /**
     * Находка из вызова инструмента. Пусто — вход не разобрался или цель не в контексте.
     *
     * @param names объекты контекста с именами — {@link ModelContext#names()}, построенные один раз на сессию
     */
    Optional<Finding> finding(Map<String, Object> input, Map<String, String> names) {
        try {
            String target = text(input, "targetArchiId");
            if (!names.containsKey(target)) {
                return Optional.empty();
            }
            String code = CODE.matcher(text(input, "code").strip().toUpperCase(Locale.ROOT)).replaceAll("_");
            return Optional.of(new Finding(findingIds.get(),
                    Severity.valueOf(text(input, "severity")),
                    clip(code, Finding.CODE_MAX),
                    clip(text(input, "message").strip(), Finding.TEXT_MAX),
                    TargetKind.valueOf(text(input, "targetKind")),
                    ElementRef.of(target, names.get(target)),
                    clip(text(input, "suggestion").strip(), Finding.TEXT_MAX)));
        } catch (InvalidValueException | IllegalArgumentException | ClassCastException
                 | NullPointerException unreadable) {
            return Optional.empty();
        }
    }

    /** План из вызова инструмента. Связь, недопустимая по матрице, остаётся — с пометкой. */
    ModelPlan plan(Map<String, Object> input, ModelContext catalog) {
        Set<String> phaseOne = Set.copyOf(metamodel.elementTypes());
        Set<String> relationTypes = Set.copyOf(metamodel.relationshipTypes());
        Map<String, ContextElement> existing = new HashMap<>();
        catalog.elements().forEach(e -> existing.putIfAbsent(key(e.name()), e));

        Map<String, PlannedElement> planned = new LinkedHashMap<>();
        for (Map<String, Object> item : items(input, "elements")) {
            String type = textOrEmpty(item, "archiType");
            String name = textOrEmpty(item, "name").strip();
            if (!phaseOne.contains(type) || name.isEmpty() || planned.containsKey(key(name))) {
                continue;
            }
            ElementRef ref = Optional.ofNullable(existing.get(key(name)))
                    .filter(e -> e.archiType().equals(type))
                    .map(e -> ElementRef.of(e.archiId(), e.name()))
                    .orElse(null);
            planned.put(key(name), new PlannedElement(type, name, ref));
        }
        List<PlannedRelation> relations = new ArrayList<>();
        for (Map<String, Object> item : items(input, "relationships")) {
            String type = textOrEmpty(item, "archiType");
            String source = textOrEmpty(item, "sourceName").strip();
            String target = textOrEmpty(item, "targetName").strip();
            if (!relationTypes.contains(type) || source.isEmpty() || target.isEmpty()) {
                continue;
            }
            Optional<String> sourceType = typeOf(source, planned, existing);
            Optional<String> targetType = typeOf(target, planned, existing);
            boolean permitted = sourceType.isPresent() && targetType.isPresent()
                    && metamodel.permitted(sourceType.get(), targetType.get(), type);
            relations.add(new PlannedRelation(type, source, target, permitted));
        }
        return new ModelPlan(List.copyOf(planned.values()), relations, textOrEmpty(input, "notes"));
    }

    /** Ответ подсказки и документации: ссылки — только на объекты, которые есть в контексте. */
    AdvisorAnswer answer(String markdown, Map<String, String> names) {
        Map<String, ElementRef> references = new LinkedHashMap<>();
        Matcher matcher = REFERENCE.matcher(markdown);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (names.containsKey(id)) {
                references.putIfAbsent(id, ElementRef.of(id, names.get(id)));
            }
        }
        return new AdvisorAnswer(markdown, List.copyOf(references.values()));
    }

    private static Optional<String> typeOf(String name, Map<String, PlannedElement> planned,
                                           Map<String, ContextElement> existing) {
        return Optional.ofNullable(planned.get(key(name))).map(PlannedElement::archiType)
                .or(() -> Optional.ofNullable(existing.get(key(name))).map(ContextElement::archiType));
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private static String text(Map<String, Object> input, String field) {
        return (String) java.util.Objects.requireNonNull(input.get(field), field);
    }

    private static String textOrEmpty(Map<String, Object> input, String field) {
        return input.get(field) instanceof String s ? s : "";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> input, String field) {
        if (!(input.get(field) instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(m -> (Map<String, Object>) m).toList();
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
