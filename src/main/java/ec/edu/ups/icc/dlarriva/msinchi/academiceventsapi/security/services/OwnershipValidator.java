package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

/**
 * Componente genérico y reutilizable de validación de ownership
 * (contexto-materia.md sección 14, docs/instrucciones.pdf sección 3: "un organizador no
 * podrá modificar eventos pertenecientes a otro organizador").
 * <p>
 * No conoce entidades de negocio: los módulos de eventos/sesiones/
 * inscripciones (que todavía no existen) resuelven ellos mismos el id del
 * dueño real del recurso (event.getOrganizer().getId(),
 * registration.getParticipant().getId(), session.getEvent().getOrganizer()
 * .getId(), etc.) y se lo pasan aquí junto con el usuario autenticado
 * (siempre desde @AuthenticationPrincipal, nunca del body ni de la URL).
 * ADMIN pasa siempre, sin importar el dueño real.
 */
public interface OwnershipValidator {

    /**
     * Lanza ForbiddenException (403) si currentUser no es ADMIN ni el dueño
     * del recurso.
     */
    void checkOwnership(Long resourceOwnerId, UserDetailsImpl currentUser);

    /**
     * Variante que no lanza excepción, para lógica condicional (ej. filtrar
     * un listado a "mis recursos o soy ADMIN").
     */
    boolean isOwner(Long resourceOwnerId, UserDetailsImpl currentUser);
}
