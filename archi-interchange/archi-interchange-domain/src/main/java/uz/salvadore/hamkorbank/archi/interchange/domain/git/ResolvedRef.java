package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.util.Objects;

/** Ссылка, разрешённая в коммит: что просили, чем это оказалось и какой коммит за ней. */
public record ResolvedRef(GitRef ref, RefKind kind, GitSha sha) {

    public ResolvedRef {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sha, "sha");
    }
}
