package uz.salvadore.hamkorbank.archi.modeling.domain.version;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.AggregateRoot;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelVersionCommittedEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Запись истории — агрегат. Неизменяема, кроме метки и очистки снимка.
 */
public final class ModelVersion extends AggregateRoot<VersionId> {

    /** Код отказа при попытке изменить версию сверх метки и очистки снимка. */
    public static final String INVARIANT = ModelingCodes.VERSION;
    /** Предел длины комментария версии. */
    public static final int COMMENT_MAX = 1000;
    /** Предел длины метки версии. */
    public static final int LABEL_MAX = 100;
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern GIT_SHA = Pattern.compile("^[0-9a-f]{40}$");

    private final ModelId modelId;
    private final long versionNo;
    private final String author;
    private final Optional<String> comment;
    private final Optional<String> label;
    private final Instant createdAt;
    private final Optional<byte[]> snapshot;
    private final String contentHash;
    private final Optional<String> gitSha;

    private ModelVersion(VersionId id, ModelId modelId, long versionNo, String author, Optional<String> comment,
                         Optional<String> label, Instant createdAt, Optional<byte[]> snapshot, String contentHash,
                         Optional<String> gitSha) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(gitSha, "gitSha");
        if (versionNo <= 0) {
            throw new InvalidValueException(ModelingMessages.VERSION_NUMBER_POSITIVE);
        }
        if (contentHash == null || !SHA256.matcher(contentHash).matches()) {
            throw new InvalidValueException(ModelingMessages.HASH_NOT_SHA256);
        }
        comment.ifPresent(c -> requireLength(c, COMMENT_MAX, ModelingMessages.FIELD_COMMENT));
        label.ifPresent(l -> requireLength(l, LABEL_MAX, ModelingMessages.FIELD_LABEL));
        gitSha.ifPresent(sha -> {
            if (!GIT_SHA.matcher(sha).matches()) {
                throw new InvalidValueException(ModelingMessages.GIT_SHA_INVALID);
            }
        });
        snapshot = snapshot.map(bytes -> Arrays.copyOf(bytes, bytes.length));
        super(id);
        this.modelId = modelId;
        this.versionNo = versionNo;
        this.author = author;
        this.comment = comment;
        this.label = label;
        this.createdAt = createdAt;
        this.snapshot = snapshot;
        this.contentHash = contentHash;
        this.gitSha = gitSha;
    }

    /** Сборка из сохранённых значений — для хранилища; значения проверяются, как при создании. */
    public static ModelVersion restore(VersionId id, ModelId modelId, long versionNo, String author,
                                       Optional<String> comment, Optional<String> label, Instant createdAt,
                                       Optional<byte[]> snapshot, String contentHash, Optional<String> gitSha) {
        return new ModelVersion(id, modelId, versionNo, author, comment, label, createdAt, snapshot, contentHash,
                                gitSha);
    }

    /**
     * Фиксация следующей версии: номер строго больше последнего и не переиспользуется,
     * событие о фиксации рождается вместе с версией — без него версия не создаётся.
     *
     * @param last          последняя версия модели; пусто — версий ещё не было
     * @param modelSize     сколько в снимке элементов и связей — для события
     */
    public static Committed commit(Optional<ModelVersion> last, VersionId id, ModelId modelId,
                                   WorkspaceId workspaceId, String author, Optional<String> comment, Instant now,
                                   byte[] snapshot, String contentHash, ModelSize modelSize) {
        last.ifPresent(previous -> {
            if (!previous.modelId.equals(modelId)) {
                throw new InvalidValueException(ModelingMessages.VERSION_OTHER_MODEL);
            }
        });
        long number = last.map(v -> v.versionNo + 1).orElse(1L);
        ModelVersion version = new ModelVersion(id, modelId, number, author, comment.filter(c -> !c.isBlank()),
                Optional.empty(), now, Optional.of(snapshot), contentHash, Optional.empty());
        ModelVersionCommittedEvent event = new ModelVersionCommittedEvent(modelId, workspaceId, number, author,
                version.comment, modelSize.elements(), modelSize.relationships(), now);
        return new Committed(version, event);
    }

    /** Метка релиза: снимок помеченной версии не удаляется. Пустая метка снимает пометку. */
    public ModelVersion labelled(Optional<String> newLabel) {
        Optional<String> normalized = newLabel.map(String::strip).filter(l -> !l.isEmpty());
        normalized.ifPresent(l -> requireLength(l, LABEL_MAX, ModelingMessages.FIELD_LABEL));
        return new ModelVersion(id(), modelId, versionNo, author, comment, normalized, createdAt, snapshot, contentHash,
                gitSha);
    }

    /**
     * Очистка снимка — только при настроенном и доступном Git и только для коммита,
     * из которого его можно восстановить. Без Git снимок — единственная копия.
     */
    public ModelVersion snapshotPurged(boolean gitBound) {
        if (!gitBound || gitSha.isEmpty()) {
            throw new ModelingDomainException(INVARIANT, Failure.CONFLICT,
                    Message.of(ModelingMessages.SNAPSHOT_ONLY_COPY, versionNo));
        }
        if (label.isPresent()) {
            throw new ModelingDomainException(INVARIANT, Failure.CONFLICT,
                    Message.of(ModelingMessages.SNAPSHOT_LABELLED, versionNo));
        }
        return new ModelVersion(id(), modelId, versionNo, author, comment, label, createdAt, Optional.empty(),
                contentHash, gitSha);
    }

    /**
     * Версия опубликована коммитом. Повтор с тем же коммитом ничего не меняет; другой
     * коммит для уже опубликованной версии — нарушение: связь версии с коммитом,
     * по которой восстанавливается снимок, не переписывается.
     */
    public ModelVersion publishedAs(String sha) {
        if (gitSha.isPresent() && !gitSha.get().equals(sha)) {
            throw new ModelingDomainException(INVARIANT, Failure.CONFLICT,
                    Message.of(ModelingMessages.VERSION_ALREADY_PUBLISHED, versionNo, gitSha.get()));
        }
        return new ModelVersion(id(), modelId, versionNo, author, comment, label, createdAt, snapshot, contentHash,
                Optional.of(sha));
    }

    /** Сжатый снимок — копией: массив версии не меняется снаружи; пусто — очищен по ретеншену. */
    public Optional<byte[]> snapshot() {
        return snapshot.map(bytes -> Arrays.copyOf(bytes, bytes.length));
    }

    /** Лежит ли снимок в базе — без копирования массива. */
    public boolean hasSnapshot() {
        return snapshot.isPresent();
    }

    /** Снимок на месте; пусто — очищен, и восстановить его без Git неоткуда ({@code 410}). */
    public byte[] requireSnapshot() {
        return snapshot.map(bytes -> Arrays.copyOf(bytes, bytes.length))
                .orElseThrow(() -> new ModelingDomainException(ModelingCodes.SNAPSHOT_PURGED, Failure.GONE,
                        Message.of(ModelingMessages.SNAPSHOT_PURGED, versionNo)));
    }

    private static void requireLength(String value, int max, String fieldKey) {
        if (value.length() > max) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.TOO_LONG, Message.of(fieldKey), max));
        }
    }

    /** Зафиксированная версия и событие о ней: событие публикуется после записи версии. */
    public record Committed(ModelVersion version, ModelVersionCommittedEvent event) {

        public Committed {
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(event, "event");
        }
    }

    /** Размер содержимого версии, который несёт событие о фиксации. */
    public record ModelSize(int elements, int relationships) {
    }

    /** Модель, которой принадлежит версия. */
    public ModelId modelId() {
        return modelId;
    }

    /** Номер версии: следующий за последним, с единицы. */
    public long versionNo() {
        return versionNo;
    }

    /** Субъект, сохранивший версию. */
    public String author() {
        return author;
    }

    /** Комментарий к версии; пусто — не задан. */
    public Optional<String> comment() {
        return comment;
    }

    /** Метка версии; пусто — не поставлена. */
    public Optional<String> label() {
        return label;
    }

    /** Момент фиксации версии. */
    public Instant createdAt() {
        return createdAt;
    }

    /** SHA-256 несжатого снимка: переживает очистку и отвечает на «есть ли изменения». */
    public String contentHash() {
        return contentHash;
    }

    /** Коммит Git, которым опубликована версия; пусто — ещё не опубликована. */
    public Optional<String> gitSha() {
        return gitSha;
    }
}
