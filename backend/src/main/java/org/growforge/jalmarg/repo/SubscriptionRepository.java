package org.growforge.jalmarg.repo;

import org.growforge.jalmarg.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByEmailIgnoreCase(String email);
}
