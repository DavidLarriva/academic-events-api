package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.entities.BaseEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Tabla roles (V1__initial_schema_and_data.sql): sin updated_at, created_at
 * lo llena el DEFAULT CURRENT_TIMESTAMP de la columna (no hay trigger).
 */
@Entity
@Table(name = "roles")
public class RoleEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 30)
    private RoleName name;

    @Column(name = "description", nullable = false, length = 150)
    private String description;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public RoleName getName() {
        return name;
    }

    public void setName(RoleName name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
