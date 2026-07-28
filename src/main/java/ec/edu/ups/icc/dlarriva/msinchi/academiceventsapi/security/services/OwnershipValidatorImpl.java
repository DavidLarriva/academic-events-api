package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ForbiddenException;
import org.springframework.stereotype.Service;

@Service
public class OwnershipValidatorImpl implements OwnershipValidator {

    @Override
    public void checkOwnership(Long resourceOwnerId, UserDetailsImpl currentUser) {
        if (isOwner(resourceOwnerId, currentUser)) {
            return;
        }
        throw new ForbiddenException("NOT_RESOURCE_OWNER", "No tiene permisos sobre este recurso");
    }

    @Override
    public boolean isOwner(Long resourceOwnerId, UserDetailsImpl currentUser) {
        if (currentUser.hasRole("ADMIN")) {
            return true;
        }
        return resourceOwnerId != null && resourceOwnerId.equals(currentUser.getId());
    }
}
