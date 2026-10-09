package org.growforge.jalmarg.repo;

import org.growforge.jalmarg.model.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from AuthSession s where s.user.id = :userId")
    int deleteAllForUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
