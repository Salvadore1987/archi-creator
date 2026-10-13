package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitBindingDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.GitBindingSettingsDto;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.ModelGitStatusDto;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitBindingService;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublishingService;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Привязка рабочего пространства к Git-репозиторию и публикация версий модели. */
@RestController
@RequestMapping("/api/v1")
public class GitController {

    private final GitBindingService bindings;
    private final GitPublishingService publishing;

    public GitController(GitBindingService bindings, GitPublishingService publishing) {
        this.bindings = bindings;
        this.publishing = publishing;
    }

    /** Опубликовать ожидающие версии модели сейчас; ответ — состояние модели в Git. */
    @PostMapping("/models/{id}/git/push")
    public ModelGitStatusDto push(EditorIdentity actor, @PathVariable UUID id) {
        return GitDtoMapper.status(publishing.push(actor, new ModelId(id)));
    }

    @GetMapping("/workspaces/{id}/git-binding")
    public GitBindingDto binding(EditorIdentity actor, @PathVariable UUID id) {
        return GitDtoMapper.binding(bindings.binding(actor, id));
    }

    @PutMapping("/workspaces/{id}/git-binding")
    public GitBindingDto configure(EditorIdentity actor, @PathVariable UUID id,
                                   @RequestBody GitBindingSettingsDto settings) {
        return GitDtoMapper.binding(bindings.configure(actor, id, GitDtoMapper.settings(settings)));
    }

    @DeleteMapping("/workspaces/{id}/git-binding")
    public ResponseEntity<Void> disable(EditorIdentity actor, @PathVariable UUID id) {
        bindings.disable(actor, id);
        return ResponseEntity.noContent().build();
    }
}
