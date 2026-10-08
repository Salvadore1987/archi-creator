package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "model_access_entry")
@IdClass(AccessEntryEntity.Key.class)
public class AccessEntryEntity {

    @Id
    @Column(name = "model_id")
    public UUID modelId;

    @Id
    @Column(name = "principal_type")
    public String principalType;

    @Id
    public String principal;

    @Column(nullable = false)
    public String access;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;

    public static class Key implements Serializable {
        public UUID modelId;
        public String principalType;
        public String principal;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(k.modelId, modelId)
                    && Objects.equals(k.principalType, principalType) && Objects.equals(k.principal, principal);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modelId, principalType, principal);
        }
    }
}
