package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Строка рабочего пространства глазами modeling: имя и время. Настройки ИИ, импорта
 * и Git в той же строке принадлежат другим контекстам и здесь не отображаются —
 * при вставке им достаются значения по умолчанию из схемы.
 */
@Entity
@Table(name = "workspace")
public class WorkspaceEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
