package com.ronkadosh.bubbleup.auth.persistence;

import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.model.UserStatus;
import com.ronkadosh.bubbleup.common.context.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Primary lookup for OAuth sign-ins. Google's {@code sub} claim is stable
     * per Google account regardless of email changes, so we always prefer it
     * over email when resolving an existing user.
     */
    Optional<User> findByGoogleSub(String googleSub);

    long countByRole(UserRole role);
    long countByCreatedAtAfter(Instant since);
    List<User> findTop50ByOrderByCreatedAtDesc();

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:q = '' or lower(u.email) like concat('%', :q, '%')
                          or lower(u.displayName) like concat('%', :q, '%'))
              and u.createdAt > :createdAfter
            """)
    Page<User> searchForAdmin(
            @Param("role") UserRole role,
            @Param("status") UserStatus status,
            @Param("q") String q,
            @Param("createdAfter") Instant createdAfter,
            Pageable pageable
    );
}
