package uz.salvadore.hamkorbank.archi.advisor.adapter.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;
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
 * Результат сессии JSON-текстом: находки, план или ответ. Формат — свои плоские
 * записи, а не типы домена: домен меняется, сохранённое должно читаться и после.
 */
final class ResultCodec {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ResultCodec() {
    }

    static @Nullable String encode(List<Finding> findings, @Nullable ModelPlan plan, @Nullable AdvisorAnswer answer) {
        if (findings.isEmpty() && plan == null && answer == null) {
            return null;
        }
        return JSON.writeValueAsString(new Stored(
                findings.stream().map(f -> new StoredFinding(f.id().value(), f.severity().name(), f.code(),
                        f.message(), f.targetKind().name(), f.target().archiId(), f.target().name(),
                        f.suggestion())).toList(),
                Optional.ofNullable(plan).map(p -> new StoredPlan(
                        p.elements().stream().map(e -> new StoredElement(e.archiType(), e.name(),
                                e.existingRef() == null ? null : StoredRef.of(e.existingRef()))).toList(),
                        p.relationships().stream().map(r -> new StoredRelation(r.archiType(), r.sourceName(),
                                r.targetName(), r.permitted())).toList(),
                        p.notes())).orElse(null),
                Optional.ofNullable(answer).map(a -> new StoredAnswer(a.markdown(),
                        a.references().stream().map(StoredRef::of).toList())).orElse(null)));
    }

    static Decoded decode(@Nullable String json) {
        if (json == null) {
            return new Decoded(List.of(), null, null);
        }
        Stored stored = JSON.readValue(json, Stored.class);
        List<Finding> findings = stored.findings() == null ? List.of() : stored.findings().stream()
                .map(f -> new Finding(FindingId.of(f.id()), Severity.valueOf(f.severity()), f.code(), f.message(),
                        TargetKind.valueOf(f.targetKind()), ElementRef.of(f.targetArchiId(), f.targetName()),
                        f.suggestion()))
                .toList();
        ModelPlan plan = Optional.ofNullable(stored.plan()).map(p -> new ModelPlan(
                p.elements().stream().map(e -> new PlannedElement(e.archiType(), e.name(),
                        e.existingRef() == null ? null : e.existingRef().toDomain())).toList(),
                p.relationships().stream().map(r -> new PlannedRelation(r.archiType(), r.sourceName(),
                        r.targetName(), r.permitted())).toList(),
                p.notes())).orElse(null);
        AdvisorAnswer answer = Optional.ofNullable(stored.answer()).map(a -> new AdvisorAnswer(a.markdown(),
                a.references().stream().map(StoredRef::toDomain).toList())).orElse(null);
        return new Decoded(findings, plan, answer);
    }

    /** Прочитанный результат; {@code plan} и {@code answer} — {@code null}, если их не было. */
    record Decoded(List<Finding> findings, @Nullable ModelPlan plan, @Nullable AdvisorAnswer answer) {
    }

    record Stored(@Nullable List<StoredFinding> findings, @Nullable StoredPlan plan, @Nullable StoredAnswer answer) {
    }

    record StoredFinding(UUID id, String severity, String code, String message, String targetKind,
                         String targetArchiId, String targetName, @Nullable String suggestion) {
    }

    record StoredPlan(List<StoredElement> elements, List<StoredRelation> relationships, @Nullable String notes) {
    }

    record StoredElement(String archiType, String name, @Nullable StoredRef existingRef) {
    }

    record StoredRelation(String archiType, String sourceName, String targetName, boolean permitted) {
    }

    record StoredAnswer(String markdown, List<StoredRef> references) {
    }

    record StoredRef(String archiId, String name) {

        static StoredRef of(ElementRef ref) {
            return new StoredRef(ref.archiId(), ref.name());
        }

        ElementRef toDomain() {
            return ElementRef.of(archiId, name);
        }
    }
}
