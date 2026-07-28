package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ForbiddenException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities.RoleEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RoleName;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnershipValidatorImplTest {

    private final OwnershipValidator validator = new OwnershipValidatorImpl();

    @Test
    void adminBypassesOwnershipCheckEvenForForeignResources() {
        UserDetailsImpl admin = userWith(1L, RoleName.ADMIN);

        assertTrue(validator.isOwner(999L, admin));
        assertDoesNotThrow(() -> validator.checkOwnership(999L, admin));
    }

    @Test
    void resourceOwnerPassesCheck() {
        UserDetailsImpl organizer = userWith(2L, RoleName.ORGANIZER);

        assertTrue(validator.isOwner(2L, organizer));
        assertDoesNotThrow(() -> validator.checkOwnership(2L, organizer));
    }

    @Test
    void nonOwnerIsRejectedWithForbidden() {
        UserDetailsImpl organizer = userWith(2L, RoleName.ORGANIZER);

        assertFalse(validator.isOwner(3L, organizer));
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> validator.checkOwnership(3L, organizer));
        assertEquals("NOT_RESOURCE_OWNER", exception.getCode());
    }

    private UserDetailsImpl userWith(Long id, RoleName roleName) {
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName(roleName);
        role.setDescription(roleName.name());

        UserEntity user = new UserEntity();
        user.setId(id);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail("test@example.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));

        return UserDetailsImpl.build(user);
    }
}
