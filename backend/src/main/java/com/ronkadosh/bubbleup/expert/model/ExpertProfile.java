package com.ronkadosh.bubbleup.expert.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "expert_profiles",
        indexes = {
                @Index(name = "idx_expert_profiles_user", columnList = "user_id", unique = true),
                @Index(name = "idx_expert_profiles_status", columnList = "verification_status")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, length = 140)
    @Setter
    private String headline;

    @Column(length = 2000)
    @Setter
    private String bio;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "expert_profile_tags",
            joinColumns = @JoinColumn(name = "expert_profile_id"),
            indexes = @Index(name = "idx_expert_profile_tags_profile", columnList = "expert_profile_id")
    )
    @Column(name = "tag", length = 64, nullable = false)
    @Builder.Default
    private Set<String> expertiseTags = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 16)
    @Setter
    private VerificationStatus verificationStatus;

    @Column(name = "verified_at")
    @Setter
    private Instant verifiedAt;

    @Column(name = "verified_by")
    @Setter
    private UUID verifiedBy;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @PrePersist
    void onCreate() {
        if (appliedAt == null) {
            appliedAt = Instant.now();
        }
    }

    public void replaceTags(Set<String> tags) {
        this.expertiseTags.clear();
        if (tags != null) {
            this.expertiseTags.addAll(tags);
        }
    }
}
