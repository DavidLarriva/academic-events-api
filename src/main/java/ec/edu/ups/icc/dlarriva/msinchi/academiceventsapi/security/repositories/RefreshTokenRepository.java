package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.repositories;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenId(UUID tokenId);

    List<RefreshTokenEntity> findAllByUser_IdAndRevokedAtIsNull(Long userId);
}
