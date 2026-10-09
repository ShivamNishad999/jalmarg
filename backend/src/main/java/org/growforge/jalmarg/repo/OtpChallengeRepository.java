package org.growforge.jalmarg.repo;

import org.growforge.jalmarg.model.Enums.OtpPurpose;
import org.growforge.jalmarg.model.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    Optional<OtpChallenge> findFirstByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(Long userId, OtpPurpose purpose);

    Optional<OtpChallenge> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, OtpPurpose purpose);

    List<OtpChallenge> findByUserIdAndPurposeAndConsumedFalse(Long userId, OtpPurpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, OtpPurpose purpose, Instant after);
}
