package com.ronkadosh.bubbleup.auth.model;

import com.ronkadosh.bubbleup.common.context.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private UserRole role;

    @Column(name = "display_name", nullable = false, length = 100)
    @Setter
    private String displayName;

    @Column(length = 280)
    @Setter
    private String bio;

    @Column(name = "avatar_file_id", length = 64)
    @Setter
    private String avatarFileId;

    @Column(name = "avatar_content_type", length = 64)
    @Setter
    private String avatarContentType;

    @Column(name = "university_id")
    @Setter
    private UUID universityId;

    @Column(name = "department_id")
    @Setter
    private UUID departmentId;

    @Column(name = "enrollment_year")
    @Setter
    private Integer enrollmentYear;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
