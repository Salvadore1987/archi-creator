package uz.salvadore.hamkorbank.archi.interchange.adapter.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "git_binding")
public class GitBindingEntity {

    @Id
    @Column(name = "workspace_id")
    public UUID workspaceId;

    @Column(name = "repo_url", nullable = false)
    public String repoUrl;

    @Column(nullable = false)
    public String branch;

    @Column(name = "token_ref", nullable = false)
    public String tokenRef;

    @Column(nullable = false)
    public String status;

    @Column(name = "last_commit_sha")
    public String lastCommitSha;

    @Column(name = "last_checked_at")
    public Instant lastCheckedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "git_author", joinColumns = @JoinColumn(name = "workspace_id"))
    @OrderColumn(name = "sort_order")
    public List<Author> authors = new ArrayList<>();

    @Embeddable
    public static class Author {

        @Column(nullable = false)
        public String subject;

        @Column(nullable = false)
        public String name;

        @Column(nullable = false)
        public String email;
    }
}
