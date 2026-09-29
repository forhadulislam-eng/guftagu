package com.guftagu.identity.persistence;

import com.guftagu.identity.domain.OtpChallengeStatus;
import com.guftagu.identity.domain.OtpPurpose;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface IdentityOtpChallengeRepository
        extends JpaRepository<IdentityOtpChallengeEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT c FROM IdentityOtpChallengeEntity c WHERE c.id = :id")
    Optional<IdentityOtpChallengeEntity> findByIdForUpdate(UUID id);

    Optional<IdentityOtpChallengeEntity>
    findTopByNormalizedE164AndPurposeAndStatusOrderByCreatedAtDesc(
            String normalizedE164,
            OtpPurpose purpose,
            OtpChallengeStatus status);
}
