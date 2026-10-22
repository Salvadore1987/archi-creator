package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
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
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EnsureDefaultWorkspaceCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ImportModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RecordGitShaCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceModelContentCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetCommittedVersionQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetLastPublishedVersionQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelContentQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelSummaryQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetVersionSnapshotQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ResolveWorkspaceQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.VerifyModelRowsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CommittedVersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ImportModelResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ReplaceModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ResolveWorkspaceResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionSnapshotResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.WorkspaceResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.WorkspaceApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/** Фейки портов импорта: транзакции с видом, сессии в памяти, вход modeling только на приём модели. */
final class ImportFakes {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);
    static final EditorIdentity ARCHITECT = EditorIdentity.of("architect", Role.ARCHITECT);
    static final UUID WORKSPACE = UUIDUtil.constructUUID(UUIDType.TIME_BASED_EPOCH, 1L, 1L);
    /** Ключи фейков — версии 7, из той же библиотеки, что у приложения. */
    static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

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

    /** Файл со связью, которую не допускает матрица: назначение от приложения к актору. */
    static byte[] withMatrixViolation() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="Нарушение" id="id-m1" version="5.0.0">
                  <folder name="Business" id="id-f2" type="business">
                    <element xsi:type="archimate:BusinessActor" name="Клиент" id="id-e1"/>
                  </folder>
                  <folder name="Application" id="id-f3" type="application">
                    <element xsi:type="archimate:ApplicationComponent" name="АБС" id="id-e2"/>
                  </folder>
                  <folder name="Relations" id="id-f8" type="relations">
                    <element xsi:type="archimate:AssignmentRelationship" id="id-r1" source="id-e2" target="id-e1"/>
                  </folder>
                </archimate:model>
                """.getBytes(StandardCharsets.UTF_8);
    }

    /** Сценарий импорта на фейках; строгость читается только в транзакции. */
    static ArchimateFileImportCommandHandler handler(ArchiDocumentReader reader, Transactions transactions,
                                                     Sessions sessions, Models models, Policy policy) {
        policy.transactions = transactions;
        return new ArchimateFileImportCommandHandler(new InterchangeKernel(transactions, events -> { },
                InterchangeMetrics.NONE, message -> message.key(), CLOCK), sessions, policy, models,
                new Workspaces(), reader, 2);
    }

    /** То же при выключенной строгости. */
    static ArchimateFileImportCommandHandler handler(ArchiDocumentReader reader, Transactions transactions,
                                                     Sessions sessions, Models models) {
        return handler(reader, transactions, sessions, models, new Policy());
    }

    /** Строгость пространства; читается только в транзакции. */
    static final class Policy implements ImportPolicy {

        boolean strict;
        private Transactions transactions;

        @Override
        public boolean strictImport(WorkspaceId workspaceId) {
            assertEquals("read", transactions.current(), "строгость читается в транзакции");
            return strict;
        }

        @Override
        public void setStrictImport(WorkspaceId workspaceId, boolean strict) {
            this.strict = strict;
        }
    }

    /** Пространство по умолчанию — {@link #WORKSPACE}; названное клиентом отдаётся как есть. */
    static final class Workspaces implements WorkspaceApplicationService {

        @Override
        public List<WorkspaceResponse> listWorkspaces() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResolveWorkspaceResponse resolveWorkspace(ResolveWorkspaceQuery query) {
            return new ResolveWorkspaceResponse(query.workspaceId() == null ? WORKSPACE : query.workspaceId());
        }

        @Override
        public WorkspaceResponse ensureDefaultWorkspace(EnsureDefaultWorkspaceCommand command) {
            throw new UnsupportedOperationException();
        }
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

        final List<String> importedIn = new ArrayList<>();

        /** Отказ, которым ответит запись модели; {@code null} — запись проходит. */
        RuntimeException refusal;

        /** Отказ, которым ответит проверка строк; {@code null} — строки принимаются. */
        RuntimeException verifyRefusal;

        private final Transactions transactions;

        Models(Transactions transactions) {
            this.transactions = transactions;
        }

        @Override
        public ImportModelResponse importModel(EditorIdentity actor, ImportModelCommand command) {
            importedIn.add(transactions.current());
            if (refusal != null) {
                throw refusal;
            }
            return new ImportModelResponse(IDS.generate(), 1, 0);
        }

        @Override
        public void verifyModelRows(VerifyModelRowsQuery query) {
            if (verifyRefusal != null) {
                throw verifyRefusal;
            }
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
