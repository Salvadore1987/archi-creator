package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import java.util.Optional;
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
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Вход для других контекстов: выгрузка снимков, публикация в Git и замена содержимого извне.
 * Роль проверяет вызывающий — у его операции своя строка в таблице доступа; список доступа,
 * состояние модели и блокировка проверяются здесь. Операции без актора — системные: их зовёт
 * фоновый обработчик.
 */
public interface ModelExchangeApplicationService {

    /** Снимок версии для выгрузки: указанной или последней. */
    VersionSnapshotResponse getVersionSnapshot(EditorIdentity actor, GetVersionSnapshotQuery query);

    /** Заголовок модели, видимой на чтение. */
    ModelSummaryResponse getModelSummary(EditorIdentity actor, GetModelSummaryQuery query);

    /** Текущее содержимое модели и последняя версия; модель видна на запись. */
    ModelContentResponse getModelContent(EditorIdentity actor, GetModelContentQuery query);

    /** Содержимое модели заменяется снимком и фиксируется версией. */
    ReplaceModelContentResponse replaceModelContent(EditorIdentity actor, ReplaceModelContentCommand command);

    /** Зафиксированная версия со снимком; системное чтение. */
    Optional<CommittedVersionResponse> getCommittedVersion(GetCommittedVersionQuery query);

    /** Последняя опубликованная в Git версия; системное чтение. */
    Optional<VersionResponse> getLastPublishedVersion(GetLastPublishedVersionQuery query);

    /** Коммит публикации версии; системная запись. */
    void recordGitSha(RecordGitShaCommand command);
}
