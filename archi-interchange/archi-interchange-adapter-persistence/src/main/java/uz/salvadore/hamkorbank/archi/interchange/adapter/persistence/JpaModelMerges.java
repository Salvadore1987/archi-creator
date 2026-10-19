package uz.salvadore.hamkorbank.archi.interchange.adapter.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelMerges;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;

/** Слитый коммит модели в {@code git_model_merge}: строка на модель, новое слияние её заменяет. */
@Repository
public class JpaModelMerges implements ModelMerges {

    private final EntityManager em;

    public JpaModelMerges(EntityManager em) {
        this.em = em;
    }

    @Override
    public void record(ModelId model, GitSha sha, Instant at) {
        em.createNativeQuery("insert into git_model_merge (model_id, merged_sha, merged_at) values (:model, :sha, :at) "
                        + "on conflict (model_id) do update set merged_sha = excluded.merged_sha, "
                        + "merged_at = excluded.merged_at")
                .setParameter("model", model.value()).setParameter("sha", sha.value()).setParameter("at", at)
                .executeUpdate();
    }

    @Override
    public Optional<GitSha> last(ModelId model) {
        List<?> rows = em.createNativeQuery("select merged_sha from git_model_merge where model_id = :model")
                .setParameter("model", model.value()).getResultList();
        return rows.stream().findFirst().map(sha -> GitSha.of((String) sha));
    }
}
