package uz.salvadore.hamkorbank.archi.modeling.domain.version;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Запись истории — агрегат. Неизменяема, кроме метки и очистки снимка.
 *
 * @param snapshot    сжатый {@code .archimate}; пусто — очищен по ретеншену
 * @param contentHash SHA-256 несжатого снимка: переживает очистку и отвечает на «есть ли изменения»
 */
public record ModelVersion(VersionId id, ModelId modelId, long versionNo, String author, Optional<String> comment,
                           Optional<String> label, Instant createdAt, Optional<byte[]> snapshot, String contentHash,
                           Optional<String> gitSha) {

    public static final String INVARIANT = ModelingCodes.VERSION;
    public static final int COMMENT_MAX = 1000;
    public static final int LABEL_MAX = 100;
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern GIT_SHA = Pattern.compile("^[0-9a-f]{40}$");

    public ModelVersion {
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
    }

    /**
     * Следующая версия: номер строго больше последнего и не переиспользуется.
     *
     * @param last последняя версия модели; пусто — версий ещё не было
     */
    public static ModelVersion next(Optional<ModelVersion> last, VersionId id, ModelId modelId, String author,
                                    Optional<String> comment, Instant now, byte[] snapshot, String contentHash) {
        last.ifPresent(previous -> {
            if (!previous.modelId.equals(modelId)) {
                throw new InvalidValueException(ModelingMessages.VERSION_OTHER_MODEL);
            }
        });
        long number = last.map(v -> v.versionNo + 1).orElse(1L);
        return new ModelVersion(id, modelId, number, author, comment.filter(c -> !c.isBlank()), Optional.empty(), now,
                Optional.of(snapshot), contentHash, Optional.empty());
    }

    /** Метка релиза: снимок помеченной версии не удаляется. Пустая метка снимает пометку. */
    public ModelVersion labelled(Optional<String> newLabel) {
        Optional<String> normalized = newLabel.map(String::strip).filter(l -> !l.isEmpty());
        normalized.ifPresent(l -> requireLength(l, LABEL_MAX, ModelingMessages.FIELD_LABEL));
        return new ModelVersion(id, modelId, versionNo, author, comment, normalized, createdAt, snapshot, contentHash,
                gitSha);
    }

    /**
     * Очистка снимка — только при настроенном и доступном Git и только для коммита,
     * из которого его можно восстановить. Без Git снимок — единственная копия.
     */
    public ModelVersion snapshotPurged(boolean gitBound) {
        if (!gitBound || gitSha.isEmpty()) {
            throw new ModelingException(INVARIANT, Failure.CONFLICT,
                    Message.of(ModelingMessages.SNAPSHOT_ONLY_COPY, versionNo));
        }
        if (label.isPresent()) {
            throw new ModelingException(INVARIANT, Failure.CONFLICT,
                    Message.of(ModelingMessages.SNAPSHOT_LABELLED, versionNo));
        }
        return new ModelVersion(id, modelId, versionNo, author, comment, label, createdAt, Optional.empty(),
                contentHash, gitSha);
    }

    /** Снимок на месте; пусто — очищен, и восстановить его без Git неоткуда ({@code 410}). */
    public byte[] requireSnapshot() {
        return snapshot.map(bytes -> Arrays.copyOf(bytes, bytes.length))
                .orElseThrow(() -> new ModelingException(ModelingCodes.SNAPSHOT_PURGED, Failure.GONE,
                        Message.of(ModelingMessages.SNAPSHOT_PURGED, versionNo)));
    }

    private static void requireLength(String value, int max, String fieldKey) {
        if (value.length() > max) {
            throw ModelingException.invalid(Message.of(ModelingMessages.TOO_LONG, Message.of(fieldKey), max));
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModelVersion v && v.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
