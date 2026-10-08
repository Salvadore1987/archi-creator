package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_lock")
public class ModelLockEntity {

    @Id
    @Column(name = "model_id")
    public UUID modelId;

    @Column(nullable = false)
    public String owner;

    @Column(name = "acquired_at", nullable = false)
    public Instant acquiredAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;
}
