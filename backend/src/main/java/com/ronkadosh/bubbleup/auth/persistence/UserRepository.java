package com.ronkadosh.bubbleup.auth.persistence;

import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.model.UserStatus;
import com.ronkadosh.bubbleup.common.context.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Update a user's email. {@link User#email} is intentionally not
     * exposed via Lombok {@code @Setter} (changing it is a privileged
     * operation that should only happen through a verified-ownership flow);
     * this targeted JPQL update lets the verification service rewrite the
     * column without giving the rest of the code a foot-gun.
     */
    @Modifying
    @Query("update User u set u.email = :email where u.id = :id")
    void updateEmail(@Param("id") UUID id, @Param("email") String email);

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
