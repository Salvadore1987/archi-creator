package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.LabelVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RestoreVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListVersionsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.SaveVersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionLabelCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionListQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionRestoreCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionSaveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.VersionApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Версии модели: делегирует в handler'ы сценариев. */
public final class VersionApplicationServiceImpl implements VersionApplicationService {

    private final VersionSaveCommandHandler save;
    private final VersionListQueryHandler list;
    private final VersionLabelCommandHandler label;
    private final VersionRestoreCommandHandler restore;

    public VersionApplicationServiceImpl(VersionSaveCommandHandler save, VersionListQueryHandler list,
                                         VersionLabelCommandHandler label, VersionRestoreCommandHandler restore) {
        this.save = save;
        this.list = list;
        this.label = label;
        this.restore = restore;
    }

    @Override
    public SaveVersionResponse saveVersion(EditorIdentity actor, SaveVersionCommand command) {
        return save.handle(actor, command);
    }

    @Override
    public List<VersionResponse> listVersions(EditorIdentity actor, ListVersionsQuery query) {
        return list.handle(actor, query);
    }

    @Override
    public VersionResponse labelVersion(EditorIdentity actor, LabelVersionCommand command) {
        return label.handle(actor, command);
    }

    @Override
    public VersionResponse restoreVersion(EditorIdentity actor, RestoreVersionCommand command) {
        return restore.handle(actor, command);
    }
}
