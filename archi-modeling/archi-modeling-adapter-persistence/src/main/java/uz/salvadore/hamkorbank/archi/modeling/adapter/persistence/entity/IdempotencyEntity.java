package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotency_record")
@IdClass(IdempotencyEntity.Key.class)
public class IdempotencyEntity {

    @Id
    public String scope;

    @Id
    public String actor;

    @Id
    @Column(name = "idem_key")
    public String key;

    @Column(nullable = false)
    public String fingerprint;

    @Column(name = "result_ref", nullable = false)
    public String resultRef;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static class Key implements Serializable {
        public String scope;
        public String actor;
        public String key;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(k.scope, scope) && Objects.equals(k.actor, actor)
                    && Objects.equals(k.key, key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scope, actor, key);
        }
    }
}
