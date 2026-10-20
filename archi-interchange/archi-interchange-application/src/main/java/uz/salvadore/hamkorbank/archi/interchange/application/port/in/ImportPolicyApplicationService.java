package uz.salvadore.hamkorbank.archi.interchange.application.port.in;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ConfigureImportPolicyCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.GetImportPolicyQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportPolicyResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Настройка импорта рабочего пространства: строгий режим. */
public interface ImportPolicyApplicationService {

    /** Текущая настройка; читают те, кто импортирует. */
    ImportPolicyResponse getImportPolicy(EditorIdentity actor, GetImportPolicyQuery query);

    /** Меняет настройку и отдаёт итоговую; менять может только {@code ADMIN}. */
    ImportPolicyResponse configureImportPolicy(EditorIdentity actor, ConfigureImportPolicyCommand command);
}
