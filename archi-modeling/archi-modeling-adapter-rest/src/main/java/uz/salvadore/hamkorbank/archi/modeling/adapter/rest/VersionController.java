package uz.salvadore.hamkorbank.archi.modeling.adapter.rest;

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
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.CommentRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.LabelRequest;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.SaveResult;
import uz.salvadore.hamkorbank.archi.modeling.adapter.rest.dto.Dtos.VersionInfo;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** История версий: сохранить, пометить, откатиться. */
@RestController
@RequestMapping("/api/v1/models/{id}/versions")
public class VersionController {

    private final VersionService versions;

    public VersionController(VersionService versions) {
        this.versions = versions;
    }

    /** История версий модели, от новых к старым. */
    @GetMapping
    public List<VersionInfo> list(EditorIdentity actor, @PathVariable UUID id) {
        return versions.list(actor, ModelId.of(id)).stream().map(DtoMapper::version).toList();
    }

    /** Сохранение: новая версия — {@code 201}; изменений нет или повтор ключа — {@code 200}. */
    @PostMapping
    public ResponseEntity<SaveResult> save(EditorIdentity actor, @PathVariable UUID id,
                                           @RequestBody(required = false) CommentRequest request,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        VersionService.SaveResult result = versions.save(actor, ModelId.of(id),
                request == null ? null : request.comment(), key);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new SaveResult(DtoMapper.version(result.version()), result.created()));
    }

    /** Откатывает модель к версии; откат фиксируется новой версией. */
    @PostMapping("/{no}/restore")
    public VersionInfo restore(EditorIdentity actor, @PathVariable UUID id, @PathVariable long no,
                               @RequestBody(required = false) CommentRequest request) {
        return DtoMapper.version(versions.rollback(actor, ModelId.of(id), no,
                request == null ? null : request.comment()));
    }

    /** Ставит или снимает метку версии. */
    @PutMapping("/{no}/label")
    public VersionInfo label(EditorIdentity actor, @PathVariable UUID id, @PathVariable long no,
                             @RequestBody LabelRequest request) {
        return DtoMapper.version(versions.label(actor, ModelId.of(id), no, request.label()));
    }
}
