package uz.salvadore.hamkorbank.archi.interchange.application.mapper;

import java.util.List;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitAppliedResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitBindingResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitChangeCountResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitChangeResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitMergePreviewResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitPullPreviewResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ModelGitStatusResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitMerge;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublisher;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPull;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.EntityMerge;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;

/** Git: привязка, состояние публикации, отчёты и итоги импорта и слияния — в результаты сценариев. */
public final class GitDataMapper {

    private GitDataMapper() {
    }

    /** Привязка без токена: в ней только ссылка на секрет. */
    public static GitBindingResponse binding(GitBinding binding) {
        return new GitBindingResponse(binding.repoUrl(), binding.branch(), binding.tokenRef().value(),
                binding.status().name(), sha(binding.lastCommitSha()), binding.lastCheckedAt(),
                binding.authors().stream()
                        .map(a -> new GitBindingResponse.Author(a.subject(), a.name(), a.email())).toList());
    }

    /** Состояние модели в Git. */
    public static ModelGitStatusResponse status(GitPublisher.ModelGitStatus status) {
        return new ModelGitStatusResponse(status.model().value(), status.state().name(), status.pending(),
                status.last() == null ? null : status.last().versionNo(),
                status.last() == null ? null : status.last().sha().value(), status.divergedVersionNo());
    }

    /** Отчёт импорта по ссылке; находки — на языке запроса. */
    public static GitPullPreviewResponse pullPreview(GitPull.Preview preview, TextCatalog texts) {
        return new GitPullPreviewResponse(preview.model().value(), preview.ref().ref().value(),
                preview.ref().kind().name(), preview.ref().sha().value(), preview.baseVersionNo(),
                preview.baseHash(), preview.targetHash(), preview.unchanged(), preview.applicable(),
                summary(preview.changes()), changes(preview.changes()),
                ImportDataMapper.findings(preview.findings(), texts));
    }

    /** Итог импорта по ссылке. */
    public static GitAppliedResponse pulled(GitPull.Pulled pulled, TextCatalog texts) {
        return applied(pulled.session(), pulled.model(), pulled.versionNo(), pulled.created(), pulled.commit(),
                pulled.findings(), texts);
    }

    /** Отчёт слияния: конфликты, каскад, изменения и то, что нужно холсту. */
    public static GitMergePreviewResponse mergePreview(GitMerge.Preview preview, TextCatalog texts) {
        return new GitMergePreviewResponse(preview.model().value(), preview.ref().ref().value(),
                preview.ref().kind().name(), preview.ref().sha().value(), preview.mergeBase().value(),
                preview.baseVersionNo(), preview.baseHash(), preview.targetHash(), preview.unchanged(),
                preview.applicable(), preview.conflicts().stream().map(GitDataMapper::conflict).toList(),
                preview.cascade().stream().map(GitDataMapper::cascade).toList(), summary(preview.changes()),
                changes(preview.changes()),
                preview.marks().stream().map(m -> new GitMergePreviewResponse.Mark(m.archiId().value(),
                        m.mark().name())).toList(),
                preview.views().stream().map(GitDataMapper::view).toList(),
                ImportDataMapper.findings(preview.findings(), texts));
    }

    /** Итог слияния. */
    public static GitAppliedResponse merged(GitMerge.Merged merged, TextCatalog texts) {
        return applied(merged.session(), merged.model(), merged.versionNo(), merged.created(), merged.commit(),
                merged.findings(), texts);
    }

    private static GitAppliedResponse applied(ImportSessionId session, ModelId model, long versionNo,
                                              boolean created, GitSha commit, List<ImportFinding> findings,
                                              TextCatalog texts) {
        return new GitAppliedResponse(session.value(), ImportStatus.APPLIED.name(), model.value(), versionNo,
                created, commit.value(), ImportDataMapper.findings(findings, texts));
    }

    private static List<GitChangeCountResponse> summary(ModelChanges changes) {
        return changes.summary().stream().map(c -> new GitChangeCountResponse(c.kind().name(), c.added(),
                c.removed(), c.changed())).toList();
    }

    private static List<GitChangeResponse> changes(ModelChanges changes) {
        return changes.changes().stream().map(c -> new GitChangeResponse(c.kind().name(), c.type().name(),
                c.archiId().value(), c.archiType(), c.name(), c.fields())).toList();
    }

    private static GitMergePreviewResponse.Conflict conflict(EntityMerge.Conflict c) {
        return new GitMergePreviewResponse.Conflict(c.id(), c.kind().name(), c.archiId().value(), c.archiType(),
                c.name(), c.field(), c.base(), c.ours(), c.theirs(),
                c.resolution() == null ? null : c.resolution().name());
    }

    private static GitMergePreviewResponse.Cascade cascade(EntityMerge.Cascade c) {
        return new GitMergePreviewResponse.Cascade(c.kind().name(), c.archiId().value(), c.name(), c.reason().name(),
                id(c.view()));
    }

    private static GitMergePreviewResponse.View view(GitMerge.ViewAdditions view) {
        return new GitMergePreviewResponse.View(view.view().value(), view.nodes().stream()
                .map(n -> new GitMergePreviewResponse.AddedNode(n.archiId().value(), id(n.element()), n.type(),
                        n.name(), n.x(), n.y(), n.width(), n.height())).toList());
    }

    private static String id(ArchiId id) {
        return id == null ? null : id.value();
    }

    private static String sha(GitSha sha) {
        return sha == null ? null : sha.value();
    }
}
