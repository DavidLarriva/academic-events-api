package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.entities;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.entities.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Tabla categories (V1__initial_schema_and_data.sql): eliminación lógica vía
 * "active" (no "deleted"). El nombre único case-insensitive es un índice
 * funcional (uq_categories_name_lower sobre LOWER(name)), no una UNIQUE
 * constraint plana sobre la columna, así que no se mapea con unique=true aquí;
 * se respeta desde el servicio del módulo categories más adelante.
 */
@Entity
@Table(name = "categories")
public class CategoryEntity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
