package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.CommentRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.LabelRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.SaveResult;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.VersionInfo;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.LabelVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RestoreVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListVersionsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.SaveVersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.VersionApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** История версий: сохранить, пометить, откатиться. */
@RestController
@RequestMapping("/api/v1/models/{id}/versions")
public class VersionController {

    private final VersionApplicationService versions;

    public VersionController(VersionApplicationService versions) {
        this.versions = versions;
    }

    /** Страница истории версий модели, от новых к старым; {@code before} — с какой версии продолжить. */
    @GetMapping
    public List<VersionInfo> list(EditorIdentity actor, @PathVariable UUID id,
                                  @RequestParam(required = false) Integer size,
                                  @RequestParam(required = false) Long before) {
        return versions.listVersions(actor, new ListVersionsQuery(id, size, before)).stream()
                .map(DtoMapper::version).toList();
    }

    /** Сохранение: новая версия — {@code 201}; изменений нет или повтор ключа — {@code 200}. */
    @PostMapping
    public ResponseEntity<SaveResult> save(EditorIdentity actor, @PathVariable UUID id,
                                           @Valid @RequestBody(required = false) CommentRequest request,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        SaveVersionResponse result = versions.saveVersion(actor, new SaveVersionCommand(id,
                request == null ? null : request.comment(), key));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(DtoMapper.saveResult(result));
    }

    /** Откатывает модель к версии; откат фиксируется новой версией. */
    @PostMapping("/{no}/restore")
    public VersionInfo restore(EditorIdentity actor, @PathVariable UUID id, @PathVariable long no,
                               @Valid @RequestBody(required = false) CommentRequest request) {
        return DtoMapper.version(versions.restoreVersion(actor, new RestoreVersionCommand(id, no,
                request == null ? null : request.comment())));
    }

    /** Ставит или снимает метку версии. */
    @PutMapping("/{no}/label")
    public VersionInfo label(EditorIdentity actor, @PathVariable UUID id, @PathVariable long no,
                             @Valid @RequestBody LabelRequest request) {
        return DtoMapper.version(versions.labelVersion(actor, new LabelVersionCommand(id, no, request.label())));
    }
}
