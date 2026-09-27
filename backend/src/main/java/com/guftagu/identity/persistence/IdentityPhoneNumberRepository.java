package com.guftagu.identity.persistence;

import com.guftagu.identity.domain.PhoneVerificationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityPhoneNumberRepository extends JpaRepository<IdentityPhoneNumberEntity, UUID> {
    Optional<IdentityPhoneNumberEntity> findByNormalizedE164AndVerificationStatusAndPrimaryTrue(
            String normalizedE164, PhoneVerificationStatus verificationStatus);
}
