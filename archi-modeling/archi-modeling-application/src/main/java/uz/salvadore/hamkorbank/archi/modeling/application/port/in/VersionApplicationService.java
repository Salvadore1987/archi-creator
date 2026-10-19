package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.LabelVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RestoreVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListVersionsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.SaveVersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Версии модели: сохранить, перечислить, пометить, откатиться. */
public interface VersionApplicationService {

    /** Новая версия; изменений нет или повтор ключа — последняя. */
    SaveVersionResponse saveVersion(EditorIdentity actor, SaveVersionCommand command);

    /** История версий, от новых к старым. */
    List<VersionResponse> listVersions(EditorIdentity actor, ListVersionsQuery query);

    /** Метка релиза. */
    VersionResponse labelVersion(EditorIdentity actor, LabelVersionCommand command);

    /** Откат к версии новой версией. */
    VersionResponse restoreVersion(EditorIdentity actor, RestoreVersionCommand command);
}
