package uz.salvadore.hamkorbank.archi.modeling.domain.version;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
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
    private final @Nullable String comment;
    private final @Nullable String label;
    private final Instant createdAt;
    private final byte @Nullable [] snapshot;
    private final String contentHash;
    private final @Nullable String gitSha;

    private ModelVersion(VersionId id, ModelId modelId, long versionNo, String author, @Nullable String comment,
                         @Nullable String label, Instant createdAt, byte @Nullable [] snapshot, String contentHash,
                         @Nullable String gitSha) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(createdAt, "createdAt");
        if (versionNo <= 0) {
            throw new InvalidValueException(ModelingMessages.VERSION_NUMBER_POSITIVE);
        }
        if (contentHash == null || !SHA256.matcher(contentHash).matches()) {
            throw new InvalidValueException(ModelingMessages.HASH_NOT_SHA256);
        }
        if (comment != null) {
            requireLength(comment, COMMENT_MAX, ModelingMessages.FIELD_COMMENT);
        }
        if (label != null) {
            requireLength(label, LABEL_MAX, ModelingMessages.FIELD_LABEL);
        }
        if (gitSha != null && !GIT_SHA.matcher(gitSha).matches()) {
            throw new InvalidValueException(ModelingMessages.GIT_SHA_INVALID);
        }
        snapshot = copy(snapshot);
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

    /**
     * Сборка из сохранённых значений — для хранилища; значения проверяются, как при создании.
     * {@code null} в комментарии, метке, снимке и коммите — значения нет.
     */
    public static ModelVersion restore(VersionId id, ModelId modelId, long versionNo, String author,
                                       @Nullable String comment, @Nullable String label, Instant createdAt,
                                       byte @Nullable [] snapshot, String contentHash, @Nullable String gitSha) {
        return new ModelVersion(id, modelId, versionNo, author, comment, label, createdAt, snapshot, contentHash,
                                gitSha);
    }

    /**
     * Фиксация следующей версии: номер строго больше последнего и не переиспользуется,
     * событие о фиксации рождается вместе с версией — без него версия не создаётся.
     *
     * @param last          последняя версия модели; {@code null} — версий ещё не было
     * @param comment       комментарий; {@code null} и пустой — без комментария
     * @param modelSize     сколько в снимке элементов и связей — для события
     */
    public static Committed commit(@Nullable ModelVersion last, VersionId id, ModelId modelId,
                                   WorkspaceId workspaceId, String author, @Nullable String comment, Instant now,
                                   byte[] snapshot, String contentHash, ModelSize modelSize) {
        if (last != null && !last.modelId.equals(modelId)) {
            throw new InvalidValueException(ModelingMessages.VERSION_OTHER_MODEL);
        }
        long number = last != null ? last.versionNo + 1 : 1L;
        ModelVersion version = new ModelVersion(id, modelId, number, author,
                comment == null || comment.isBlank() ? null : comment,
                null, now, Objects.requireNonNull(snapshot, "snapshot"), contentHash, null);
        ModelVersionCommittedEvent event = new ModelVersionCommittedEvent(modelId, workspaceId, number, author,
                version.comment, modelSize.elements(), modelSize.relationships(), now);
        return new Committed(version, event);
    }

    /** Метка релиза: снимок помеченной версии не удаляется. {@code null} и пустая метка снимают пометку. */
    public ModelVersion labelled(@Nullable String newLabel) {
        String stripped = newLabel == null ? null : newLabel.strip();
        String normalized = stripped == null || stripped.isEmpty() ? null : stripped;
        if (normalized != null) {
            requireLength(normalized, LABEL_MAX, ModelingMessages.FIELD_LABEL);
        }
        return new ModelVersion(id(), modelId, versionNo, author, comment, normalized, createdAt, snapshot, contentHash,
                gitSha);
    }

    /**
     * Очистка снимка — только при настроенном и доступном Git и только для коммита,
     * из которого его можно восстановить. Без Git снимок — единственная копия.
     */
    public ModelVersion snapshotPurged(boolean gitBound) {
        if (!gitBound || gitSha == null) {
            throw new ModelingDomainException(INVARIANT, Failure.CONFLICT,
                    Message.of(ModelingMessages.SNAPSHOT_ONLY_COPY, versionNo));
        }
        if (label != null) {
            throw new ModelingDomainException(INVARIANT, Failure.CONFLICT,
                    Message.of(ModelingMessages.SNAPSHOT_LABELLED, versionNo));
        }
        return new ModelVersion(id(), modelId, versionNo, author, comment, label, createdAt, null,
                contentHash, gitSha);
    }

    /**
     * Версия опубликована коммитом. Повтор с тем же коммитом ничего не меняет; другой
     * коммит для уже опубликованной версии — нарушение: связь версии с коммитом,
     * по которой восстанавливается снимок, не переписывается.
     */
    public ModelVersion publishedAs(String sha) {
        if (gitSha != null && !gitSha.equals(sha)) {
            throw new ModelingDomainException(INVARIANT, Failure.CONFLICT,
                    Message.of(ModelingMessages.VERSION_ALREADY_PUBLISHED, versionNo, gitSha));
        }
        return new ModelVersion(id(), modelId, versionNo, author, comment, label, createdAt, snapshot, contentHash,
                Objects.requireNonNull(sha, "sha"));
    }

    /** Сжатый снимок — копией: массив версии не меняется снаружи; {@code null} — очищен по ретеншену. */
    public byte @Nullable [] snapshot() {
        return copy(snapshot);
    }

    /** Лежит ли снимок в базе — без копирования массива. */
    public boolean hasSnapshot() {
        return snapshot != null;
    }

    /** Снимок на месте; очищен — восстановить его без Git неоткуда ({@code 410}). */
    public byte[] requireSnapshot() {
        if (snapshot == null) {
            throw new ModelingDomainException(ModelingCodes.SNAPSHOT_PURGED, Failure.GONE,
                    Message.of(ModelingMessages.SNAPSHOT_PURGED, versionNo));
        }
        return Arrays.copyOf(snapshot, snapshot.length);
    }

    private static byte @Nullable [] copy(byte @Nullable [] bytes) {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
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

    /** Комментарий к версии; {@code null} — не задан. */
    public @Nullable String comment() {
        return comment;
    }

    /** Метка версии; {@code null} — не поставлена. */
    public @Nullable String label() {
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

    /** Коммит Git, которым опубликована версия; {@code null} — ещё не опубликована. */
    public @Nullable String gitSha() {
        return gitSha;
    }
}
