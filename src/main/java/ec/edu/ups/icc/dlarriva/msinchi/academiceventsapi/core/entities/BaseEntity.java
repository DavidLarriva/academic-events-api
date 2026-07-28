package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.entities;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Solo el id es común a todas las tablas de V1__initial_schema_and_data.sql.
 * createdAt/updatedAt/deleted/version varían por tabla (no todas los tienen),
 * así que cada entidad concreta declara exactamente las columnas de auditoría
 * que su tabla realmente tiene.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
