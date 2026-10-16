package uz.salvadore.hamkorbank.archi.advisor.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

class AdvisorIdempotencyTest {

    private static final Locale RU = Locale.forLanguageTag("ru");

    @Test
    @DisplayName("INV-ADV-003: повтор с тем же ключом отдаёт результат сессии и не обращается к API второй раз")
    void replayDoesNotCallUpstreamTwice() {
        Fixture fixture = new Fixture();
        fixture.script = listener -> {
            listener.usage(new TokenUsage(300, 20));
            listener.toolCall("report_finding", Map.of("severity", "INFO", "code", "NAMING", "message", "m",
                    "targetKind", "ELEMENT", "targetArchiId", "svc", "suggestion", "s"));
        };
        AdvisorService service = fixture.service();
        AdvisorRequest review = AdvisorRequest.review(Fixture.MODEL, Optional.empty());
        Fixture.Recorded first = new Fixture.Recorded();
        service.ask(Fixture.ALICE, review, "same-key", RU).run(first);

        Fixture.Recorded replay = new Fixture.Recorded();
        service.ask(Fixture.ALICE, review, "same-key", RU).run(replay);

        assertEquals(1, fixture.upstreamCalls.size(), "второго обращения к внешнему API нет");
        assertEquals(first.findings, replay.findings);
        assertEquals(first.meta, replay.meta);
        assertEquals(320, fixture.budgets.values().iterator().next().consumedTokens(), "расход не удвоен");
    }

    @Test
    @DisplayName("INV-ADV-003: тот же ключ с другими параметрами — конфликт")
    void sameKeyDifferentRequestConflicts() {
        Fixture fixture = new Fixture();
        AdvisorService service = fixture.service();
        service.ask(Fixture.ALICE, AdvisorRequest.review(Fixture.MODEL, Optional.empty()), "k", RU)
                .run(new Fixture.Recorded());

        AdvisorDomainException conflict = assertThrows(AdvisorDomainException.class,
                () -> service.ask(Fixture.ALICE, AdvisorRequest.explain(Fixture.MODEL, "crm"), "k", RU));

        assertEquals("INV-ADV-003", conflict.code());
    }

    @Test
    @DisplayName("INV-ADV-003: ключ другого автора — другая сессия")
    void keysAreScopedByRequester() {
        Fixture fixture = new Fixture();
        AdvisorService service = fixture.service();
        AdvisorRequest review = AdvisorRequest.review(Fixture.MODEL, Optional.empty());
        service.ask(Fixture.ALICE, review, "k", RU).run(new Fixture.Recorded());
        service.ask(Fixture.VIEWER, review, "k", RU).run(new Fixture.Recorded());

        assertEquals(2, fixture.upstreamCalls.size());
    }
}
