package com.guftagu.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guftagu.identity.domain.AccountStatus;
import com.guftagu.identity.domain.ClientType;
import com.guftagu.identity.domain.NormalizedPhoneNumber;
import com.guftagu.identity.domain.OtpChallengeStatus;
import com.guftagu.identity.domain.OtpPurpose;
import com.guftagu.identity.domain.PhoneVerificationStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class IdentityPersistenceIT {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private IdentityUserRepository userRepository;
    @Autowired
    private IdentityPhoneNumberRepository phoneRepository;
    @Autowired
    private IdentitySessionRepository sessionRepository;
    @Autowired
    private IdentityRefreshTokenFamilyRepository familyRepository;
    @Autowired
    private IdentityRefreshTokenRepository tokenRepository;
    @Autowired
    private IdentityOtpChallengeRepository otpChallengeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    @Transactional
    void phoneLookupReturnsOnlyVerifiedPrimary() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        userRepository.save(user);

        IdentityPhoneNumberEntity primaryVerified = new IdentityPhoneNumberEntity(
                user, new NormalizedPhoneNumber("+14155552671"), PhoneVerificationStatus.VERIFIED, true);
        phoneRepository.save(primaryVerified);

        IdentityPhoneNumberEntity nonPrimaryVerified = new IdentityPhoneNumberEntity(
                user, new NormalizedPhoneNumber("+14155552672"), PhoneVerificationStatus.VERIFIED, false);
        phoneRepository.save(nonPrimaryVerified);

        IdentityUserEntity user2 = new IdentityUserEntity(AccountStatus.ACTIVE);
        userRepository.save(user2);

        IdentityPhoneNumberEntity primaryUnverified = new IdentityPhoneNumberEntity(
                user2, new NormalizedPhoneNumber("+14155552673"), PhoneVerificationStatus.PENDING, true);
        phoneRepository.save(primaryUnverified);

        Optional<IdentityPhoneNumberEntity> found1 = phoneRepository
                .findByNormalizedE164AndVerificationStatusAndPrimaryTrue("+14155552671", PhoneVerificationStatus.VERIFIED);
        assertThat(found1).isPresent();

        Optional<IdentityPhoneNumberEntity> found2 = phoneRepository
                .findByNormalizedE164AndVerificationStatusAndPrimaryTrue("+14155552672", PhoneVerificationStatus.VERIFIED);
        assertThat(found2).isEmpty();

        Optional<IdentityPhoneNumberEntity> found3 = phoneRepository
                .findByNormalizedE164AndVerificationStatusAndPrimaryTrue("+14155552673", PhoneVerificationStatus.VERIFIED);
        assertThat(found3).isEmpty();

        Optional<IdentityPhoneNumberEntity> foundUnrelated = phoneRepository
                .findByNormalizedE164AndVerificationStatusAndPrimaryTrue("+14155559999", PhoneVerificationStatus.VERIFIED);
        assertThat(foundUnrelated).isEmpty();
    }

    @Test
    @Transactional
    void sessionStateMutatorsWorkProperly() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        userRepository.save(user);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        IdentitySessionEntity session = new IdentitySessionEntity(
                user, ClientType.WEB, null, null, null, null, now.plusDays(1));
        sessionRepository.save(session);

        session.touch(now.plusMinutes(5));

        session.revoke("Test revocation");
        assertThatThrownBy(() -> session.revoke("Again"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    void refreshTokenStateMutatorsWorkProperly() {
        IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
        userRepository.save(user);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        IdentitySessionEntity session = new IdentitySessionEntity(
                user, ClientType.WEB, null, null, null, null, expiresAt);
        sessionRepository.save(session);
        IdentityRefreshTokenFamilyEntity family = new IdentityRefreshTokenFamilyEntity(session, expiresAt);
        familyRepository.save(family);

        IdentityRefreshTokenEntity token = new IdentityRefreshTokenEntity(family, null, new byte[]{1}, expiresAt);
        tokenRepository.save(token);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        token.consume(now);

        assertThatThrownBy(() -> token.consume(now)).isInstanceOf(IllegalStateException.class);

        IdentityRefreshTokenEntity token2 = new IdentityRefreshTokenEntity(family, null, new byte[]{2}, expiresAt);
        tokenRepository.save(token2);
        token2.revoke("Test revoke");
        assertThatThrownBy(() -> token2.consume(now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> token2.revoke("Again")).isInstanceOf(IllegalStateException.class);

        IdentityRefreshTokenEntity token3 = new IdentityRefreshTokenEntity(family, null, new byte[]{3}, expiresAt);
        tokenRepository.save(token3);
        assertThatThrownBy(() -> token3.consume(expiresAt)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> token3.consume(expiresAt.plusSeconds(1))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pessimisticLockingBlocksConcurrentTransactions() throws InterruptedException {
        // Prepare data in a separate transaction
        UUID tokenId = transactionTemplate.execute(status -> {
            IdentityUserEntity user = new IdentityUserEntity(AccountStatus.ACTIVE);
            userRepository.save(user);
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
            IdentitySessionEntity session = new IdentitySessionEntity(
                    user, ClientType.WEB, null, null, null, null, expiresAt);
            sessionRepository.save(session);
            IdentityRefreshTokenFamilyEntity family = new IdentityRefreshTokenFamilyEntity(session, expiresAt);
            familyRepository.save(family);
            IdentityRefreshTokenEntity token = new IdentityRefreshTokenEntity(family, null, new byte[]{1}, expiresAt);
            tokenRepository.save(token);
            return (UUID) ReflectionTestUtils.getField(token, "id");
        });

        CountDownLatch thread1Locked = new CountDownLatch(1);
        CountDownLatch thread1Release = new CountDownLatch(1);
        CountDownLatch thread2Started = new CountDownLatch(1);
        CountDownLatch thread2Finished = new CountDownLatch(1);
        AtomicReference<Exception> thread1Exception = new AtomicReference<>();
        AtomicReference<Exception> thread2Exception = new AtomicReference<>();

        Thread thread1 = new Thread(() -> {
            try {
                transactionTemplate.execute(status -> {
                    tokenRepository.findByIdForUpdate(tokenId);
                    thread1Locked.countDown();
                    try {
                        if (!thread1Release.await(10, TimeUnit.SECONDS)) {
                            throw new RuntimeException("Thread 1 timed out waiting for release latch");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (Exception e) {
                thread1Exception.set(e);
                thread1Locked.countDown(); // unblock main thread if we fail before acquiring
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                if (!thread1Locked.await(10, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Thread 2 timed out waiting for Thread 1 to lock");
                }
                thread2Started.countDown();
                transactionTemplate.execute(status -> {
                    tokenRepository.findByIdForUpdate(tokenId);
                    return null;
                });
            } catch (Exception e) {
                thread2Exception.set(e);
                thread2Started.countDown(); // unblock main thread if we fail early
            } finally {
                thread2Finished.countDown();
            }
        });

        thread1.start();
        assertThat(thread1Locked.await(10, TimeUnit.SECONDS))
                .withFailMessage("Thread 1 did not acquire lock in time")
                .isTrue();
        assertThat(thread1Exception.get()).isNull();

        thread2.start();
        assertThat(thread2Started.await(10, TimeUnit.SECONDS))
                .withFailMessage("Thread 2 did not start lock attempt in time")
                .isTrue();
        assertThat(thread2Exception.get()).isNull();

        boolean thread2CompletedEarly = thread2Finished.await(500, TimeUnit.MILLISECONDS);
        assertThat(thread2CompletedEarly).isFalse();
        assertThat(thread2Exception.get()).isNull();

        thread1Release.countDown();

        thread1.join(5000);
        assertThat(thread1.isAlive()).isFalse();
        assertThat(thread1Exception.get()).isNull();

        assertThat(thread2Finished.await(5, TimeUnit.SECONDS))
                .withFailMessage("Thread 2 did not finish after Thread 1 released lock")
                .isTrue();
        thread2.join(5000);
        assertThat(thread2.isAlive()).isFalse();

        assertThat(thread2Exception.get()).isNull();
    }

    // -------------------------------------------------------------------------
    // Phase 3.6.2D — IdentityOtpChallengeRepository
    // -------------------------------------------------------------------------

    @Test
    @Transactional
    void findByIdForUpdateReturnsExistingChallenge() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                new NormalizedPhoneNumber("+14155550001"),
                OtpPurpose.REGISTRATION,
                "provider-ref-exists",
                expiresAt);
        otpChallengeRepository.save(challenge);

        Optional<IdentityOtpChallengeEntity> found =
                otpChallengeRepository.findByIdForUpdate(challenge.id());
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(challenge.id());
        assertThat(found.get().normalizedE164()).isEqualTo("+14155550001");
        assertThat(found.get().purpose()).isEqualTo(OtpPurpose.REGISTRATION);
        assertThat(found.get().status()).isEqualTo(OtpChallengeStatus.PENDING);
    }

    @Test
    @Transactional
    void findByIdForUpdateReturnsEmptyForUnknownId() {
        Optional<IdentityOtpChallengeEntity> found =
                otpChallengeRepository.findByIdForUpdate(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    void otpChallengePessimisticLockBlocksConcurrentTransactions() throws InterruptedException {
        UUID challengeId = transactionTemplate.execute(status -> {
            IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                    new NormalizedPhoneNumber("+14155550002"),
                    OtpPurpose.REGISTRATION,
                    "provider-ref-lock-test",
                    OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
            otpChallengeRepository.save(challenge);
            return challenge.id();
        });

        CountDownLatch thread1Locked = new CountDownLatch(1);
        CountDownLatch thread1Release = new CountDownLatch(1);
        CountDownLatch thread2Started = new CountDownLatch(1);
        CountDownLatch thread2Finished = new CountDownLatch(1);
        AtomicReference<Exception> thread1Exception = new AtomicReference<>();
        AtomicReference<Exception> thread2Exception = new AtomicReference<>();

        Thread thread1 = new Thread(() -> {
            try {
                transactionTemplate.execute(status -> {
                    otpChallengeRepository.findByIdForUpdate(challengeId);
                    thread1Locked.countDown();
                    try {
                        if (!thread1Release.await(10, TimeUnit.SECONDS)) {
                            throw new RuntimeException("Thread 1 timed out waiting for release latch");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (Exception e) {
                thread1Exception.set(e);
                thread1Locked.countDown();
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                if (!thread1Locked.await(10, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Thread 2 timed out waiting for Thread 1 to lock");
                }
                thread2Started.countDown();
                transactionTemplate.execute(status -> {
                    otpChallengeRepository.findByIdForUpdate(challengeId);
                    return null;
                });
            } catch (Exception e) {
                thread2Exception.set(e);
                thread2Started.countDown();
            } finally {
                thread2Finished.countDown();
            }
        });

        thread1.start();
        assertThat(thread1Locked.await(10, TimeUnit.SECONDS))
                .withFailMessage("Thread 1 did not acquire lock in time")
                .isTrue();
        assertThat(thread1Exception.get()).isNull();

        thread2.start();
        assertThat(thread2Started.await(10, TimeUnit.SECONDS))
                .withFailMessage("Thread 2 did not start lock attempt in time")
                .isTrue();
        assertThat(thread2Exception.get()).isNull();

        boolean thread2CompletedEarly = thread2Finished.await(500, TimeUnit.MILLISECONDS);
        assertThat(thread2CompletedEarly).isFalse();
        assertThat(thread2Exception.get()).isNull();

        thread1Release.countDown();

        thread1.join(5000);
        assertThat(thread1.isAlive()).isFalse();
        assertThat(thread1Exception.get()).isNull();

        assertThat(thread2Finished.await(5, TimeUnit.SECONDS))
                .withFailMessage("Thread 2 did not finish after Thread 1 released lock")
                .isTrue();
        thread2.join(5000);
        assertThat(thread2.isAlive()).isFalse();
        assertThat(thread2Exception.get()).isNull();
    }

    @Test
    @Transactional
    void lookupReturnsLatestPendingChallengeForPhoneAndPurpose() {
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC);
        NormalizedPhoneNumber phone = new NormalizedPhoneNumber("+14155550003");

        IdentityOtpChallengeEntity older = new IdentityOtpChallengeEntity(
                UUID.randomUUID(),
                phone,
                OtpPurpose.REGISTRATION,
                "provider-ref-older",
                base.minusMinutes(2),
                base.plusMinutes(8));
        IdentityOtpChallengeEntity newer = new IdentityOtpChallengeEntity(
                UUID.randomUUID(),
                phone,
                OtpPurpose.REGISTRATION,
                "provider-ref-newer",
                base.minusMinutes(1),
                base.plusMinutes(9));

        otpChallengeRepository.save(older);
        otpChallengeRepository.save(newer);

        Optional<IdentityOtpChallengeEntity> found =
                otpChallengeRepository
                        .findTopByNormalizedE164AndPurposeAndStatusOrderByCreatedAtDesc(
                                "+14155550003", OtpPurpose.REGISTRATION, OtpChallengeStatus.PENDING);

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(newer.id());
    }

    @Test
    @Transactional
    void lookupReturnsEmptyForDifferentPhoneOrPurpose() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        IdentityOtpChallengeEntity challenge = new IdentityOtpChallengeEntity(
                new NormalizedPhoneNumber("+14155550004"),
                OtpPurpose.REGISTRATION,
                "provider-ref-no-match",
                expiresAt);
        otpChallengeRepository.save(challenge);

        // different phone, same purpose
        Optional<IdentityOtpChallengeEntity> differentPhone =
                otpChallengeRepository
                        .findTopByNormalizedE164AndPurposeAndStatusOrderByCreatedAtDesc(
                                "+14155559999", OtpPurpose.REGISTRATION, OtpChallengeStatus.PENDING);
        assertThat(differentPhone).isEmpty();

        // same phone, different purpose
        Optional<IdentityOtpChallengeEntity> differentPurpose =
                otpChallengeRepository
                        .findTopByNormalizedE164AndPurposeAndStatusOrderByCreatedAtDesc(
                                "+14155550004", OtpPurpose.PASSWORD_RESET, OtpChallengeStatus.PENDING);
        assertThat(differentPurpose).isEmpty();
    }

    @Test
    @Transactional
    void lookupExcludesVerifiedAndConsumedWhenQueryingPending() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        NormalizedPhoneNumber phone = new NormalizedPhoneNumber("+14155550005");

        IdentityOtpChallengeEntity verifiedChallenge = new IdentityOtpChallengeEntity(
                phone, OtpPurpose.REGISTRATION, "provider-ref-verified",
                now.plusMinutes(10));
        verifiedChallenge.markVerified(now.plusMinutes(1));
        otpChallengeRepository.save(verifiedChallenge);

        IdentityOtpChallengeEntity consumedChallenge = new IdentityOtpChallengeEntity(
                phone, OtpPurpose.REGISTRATION, "provider-ref-consumed",
                now.plusMinutes(10));
        consumedChallenge.markVerified(now.plusMinutes(1));
        consumedChallenge.markConsumed(now.plusMinutes(2));
        otpChallengeRepository.save(consumedChallenge);

        Optional<IdentityOtpChallengeEntity> found =
                otpChallengeRepository
                        .findTopByNormalizedE164AndPurposeAndStatusOrderByCreatedAtDesc(
                                "+14155550005", OtpPurpose.REGISTRATION, OtpChallengeStatus.PENDING);
        assertThat(found).isEmpty();
    }
}
