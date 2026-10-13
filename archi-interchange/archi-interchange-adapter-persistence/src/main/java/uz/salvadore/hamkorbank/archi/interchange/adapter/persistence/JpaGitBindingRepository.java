package uz.salvadore.hamkorbank.archi.interchange.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitAuthor;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBindingStatus;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.SecretRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/** Привязка — строка {@code git_binding}, авторы — {@code git_author} по порядку. */
@Repository
public class JpaGitBindingRepository implements GitBindingRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<GitBinding> find(WorkspaceId workspaceId) {
        return Optional.ofNullable(em.find(GitBindingEntity.class, workspaceId.value())).map(e -> new GitBinding(
                new WorkspaceId(e.workspaceId), e.repoUrl, e.branch, SecretRef.of(e.tokenRef),
                GitBindingStatus.valueOf(e.status), Optional.ofNullable(e.lastCommitSha).map(GitSha::of),
                Optional.ofNullable(e.lastCheckedAt),
                e.authors.stream().map(a -> new GitAuthor(a.subject, a.name, a.email)).toList()));
    }

    @Override
    public void save(GitBinding binding) {
        GitBindingEntity entity = em.find(GitBindingEntity.class, binding.workspaceId().value());
        boolean fresh = entity == null;
        if (fresh) {
            entity = new GitBindingEntity();
            entity.workspaceId = binding.workspaceId().value();
        }
        entity.repoUrl = binding.repoUrl();
        entity.branch = binding.branch();
        entity.tokenRef = binding.tokenRef().value();
        entity.status = binding.status().name();
        entity.lastCommitSha = binding.lastCommitSha().map(GitSha::value).orElse(null);
        entity.lastCheckedAt = binding.lastCheckedAt().orElse(null);
        entity.authors.clear();
        for (GitAuthor author : binding.authors()) {
            GitBindingEntity.Author row = new GitBindingEntity.Author();
            row.subject = author.subject();
            row.name = author.name();
            row.email = author.email();
            entity.authors.add(row);
        }
        if (fresh) {
            em.persist(entity);
        }
        em.flush();
    }

    @Override
    public boolean workspaceExists(WorkspaceId workspaceId) {
        return !em.createNativeQuery("select 1 from workspace where id = :w")
                .setParameter("w", workspaceId.value()).getResultList().isEmpty();
    }
}
