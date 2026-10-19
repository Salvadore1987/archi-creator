package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.uuid.UUIDType;
import com.fasterxml.uuid.impl.UUIDUtil;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RecordGitShaCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceModelContentCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetCommittedVersionQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetLastPublishedVersionQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelContentQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelSummaryQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetVersionSnapshotQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CommittedVersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ReplaceModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionSnapshotResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/** Фейки портов импорта: транзакции с видом, сессии в памяти, вход modeling только на приём модели. */
final class ImportFakes {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);
    static final EditorIdentity ARCHITECT = EditorIdentity.of("architect", Role.ARCHITECT);
    static final UUID WORKSPACE = UUIDUtil.constructUUID(UUIDType.TIME_BASED_EPOCH, 1L, 1L);

    private ImportFakes() {
    }

    /** Файл с одной корневой папкой; {@code folderType} — её атрибут {@code type}. */
    static byte[] file(String folderType) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="Тест" id="id-m" version="5.0.0">
                  <folder name="Application" id="id-fa" type="%s">
                    <element xsi:type="archimate:ApplicationComponent" name="АБС" id="id-abs"/>
                  </folder>
                </archimate:model>
                """.formatted(folderType).getBytes(StandardCharsets.UTF_8);
    }

    /** Сценарий импорта на фейках; строгость выключена и читается только в транзакции. */
    static ImportService service(ArchiDocumentReader reader, Transactions transactions, Sessions sessions,
                                 Models models) {
        ImportPolicy policy = new ImportPolicy() {
            @Override
            public boolean strictImport(WorkspaceId workspaceId) {
                assertEquals("read", transactions.current(), "строгость читается в транзакции");
                return false;
            }

            @Override
            public void setStrictImport(WorkspaceId workspaceId, boolean strict) {
            }
        };
        return new ImportService(sessions, policy, models, reader, transactions, events -> { },
                InterchangeMetrics.NONE, message -> message.key(), CLOCK, 2);
    }

    /** Транзакции с видом: вложенная присоединяется к внешней, как у {@code TransactionTemplate}. */
    static final class Transactions implements UnitOfWork {

        final List<String> opened = new ArrayList<>();
        private final List<String> stack = new ArrayList<>();

        String current() {
            return stack.isEmpty() ? "none" : stack.getFirst();
        }

        @Override
        public <T> T write(Supplier<T> work) {
            return run("write", work);
        }

        @Override
        public <T> T read(Supplier<T> work) {
            return run("read", work);
        }

        private <T> T run(String kind, Supplier<T> work) {
            if (stack.isEmpty()) {
                opened.add(kind);
            }
            stack.add(kind);
            try {
                return work.get();
            } finally {
                stack.removeLast();
            }
        }
    }

    static final class Sessions implements ImportSessionRepository {

        final Map<ImportSessionId, ImportSession> rows = new LinkedHashMap<>();

        @Override
        public Optional<ImportSession> find(ImportSessionId id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void save(ImportSession session) {
            rows.put(session.id(), session);
        }

        @Override
        public Optional<ImportSession> findByIdempotencyKey(WorkspaceId workspaceId, String key) {
            return rows.values().stream().filter(s -> s.idempotencyKey().equals(key)).findFirst();
        }
    }

    /** Вход modeling: нужен только приём импортированной модели. */
    static final class Models implements ModelExchangeApplicationService {

        private final Transactions transactions;
        final List<String> importedIn = new ArrayList<>();

        Models(Transactions transactions) {
            this.transactions = transactions;
        }

        /** Отказ, которым ответит запись модели; {@code null} — запись проходит. */
        RuntimeException refusal;

        @Override
        public VersionResponse importModel(EditorIdentity actor, ModelContent content, String comment) {
            importedIn.add(transactions.current());
            if (refusal != null) {
                throw refusal;
            }
            return new VersionResponse(1, actor.subject(), comment, null, null, true, "hash", null);
        }

        @Override
        public VersionSnapshotResponse getVersionSnapshot(EditorIdentity actor, GetVersionSnapshotQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelSummaryResponse getModelSummary(EditorIdentity actor, GetModelSummaryQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelContentResponse getModelContent(EditorIdentity actor, GetModelContentQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReplaceModelContentResponse replaceModelContent(EditorIdentity actor,
                                                               ReplaceModelContentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CommittedVersionResponse> getCommittedVersion(GetCommittedVersionQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<VersionResponse> getLastPublishedVersion(GetLastPublishedVersionQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordGitSha(RecordGitShaCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
