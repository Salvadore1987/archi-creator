package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AuditEntryId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Общие заготовки тестов сессии. */
final class Sessions {

    static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    static final UuidV7 UUIDS = new UuidV7(Clock.fixed(NOW, ZoneOffset.UTC));
    static final WorkspaceId WORKSPACE = WorkspaceId.of(UUIDS.next());
    static final ModelId MODEL = ModelId.of(UUIDS.next());

    private Sessions() {
    }

    static AdvisorSession requested(AdvisorRequest request) {
        return AdvisorSession.request(AdvisorSessionId.next(UUIDS), WORKSPACE, request, "key-1", "alice", NOW);
    }

    static AdvisorSession review() {
        return requested(AdvisorRequest.review(MODEL, null));
    }

    static AuditEntryId audit() {
        return AuditEntryId.next(UUIDS);
    }

    static Finding finding(String archiId) {
        return new Finding(FindingId.next(UUIDS), Severity.WARNING, "ORPHAN_ELEMENT", "message", TargetKind.ELEMENT,
                ElementRef.of(archiId, "CRM"), "fix");
    }
}
