package com.ronkadosh.bubbleup.admin.api.dto;

import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CourseAdminDetail;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CourseAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CourseDepartmentLinkDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.DepartmentAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.OfferingAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.TermAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.UniversityAdminDto;
import com.ronkadosh.bubbleup.catalog.model.TermKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AdminCatalogDtos {
    private AdminCatalogDtos() {}

    // ─── Responses (passthroughs over the internal DTOs) ──────────────────────

    public record University(UUID id, String name, String shortCode, String country, Instant createdAt) {
        public static University from(UniversityAdminDto d) {
            return new University(d.id(), d.name(), d.shortCode(), d.country(), d.createdAt());
        }
    }

    public record Department(UUID id, UUID universityId, String name, String shortCode, Instant createdAt) {
        public static Department from(DepartmentAdminDto d) {
            return new Department(d.id(), d.universityId(), d.name(), d.shortCode(), d.createdAt());
        }
    }

    public record Term(
            UUID id, UUID universityId, String code, String name,
            TermKind kind, int academicYear, LocalDate startsOn, LocalDate endsOn, Instant createdAt
    ) {
        public static Term from(TermAdminDto d) {
            return new Term(d.id(), d.universityId(), d.code(), d.name(),
                    d.kind(), d.academicYear(), d.startsOn(), d.endsOn(), d.createdAt());
        }
    }

    public record Course(
            UUID id, UUID universityId, String code, String name,
            BigDecimal creditPoints, String description, Instant createdAt
    ) {
        public static Course from(CourseAdminDto d) {
            return new Course(d.id(), d.universityId(), d.code(), d.name(),
                    d.creditPoints(), d.description(), d.createdAt());
        }
    }

    public record Offering(UUID id, UUID courseId, UUID termId, Instant createdAt) {
        public static Offering from(OfferingAdminDto d) {
            return new Offering(d.id(), d.courseId(), d.termId(), d.createdAt());
        }
    }

    public record CourseDepartmentLink(UUID courseId, UUID departmentId, boolean primary, Instant createdAt) {
        public static CourseDepartmentLink from(CourseDepartmentLinkDto d) {
            return new CourseDepartmentLink(d.courseId(), d.departmentId(), d.primary(), d.createdAt());
        }
    }

    public record CourseDetail(
            Course course,
            List<Offering> offerings,
            List<CourseDepartmentLink> departmentLinks
    ) {
        public static CourseDetail from(CourseAdminDetail d) {
            return new CourseDetail(
                    Course.from(d.course()),
                    d.offerings().stream().map(Offering::from).toList(),
                    d.departmentLinks().stream().map(CourseDepartmentLink::from).toList()
            );
        }
    }

    // ─── Create / Update request bodies ───────────────────────────────────────

    public record CreateUniversityRequest(
            @NotBlank String name,
            @NotBlank @Size(max = 16) String shortCode,
            @NotBlank @Size(min = 2, max = 2) String country
    ) {}

    public record UpdateUniversityRequest(String name, String shortCode, String country) {}

    public record CreateDepartmentRequest(
            @NotBlank String name,
            @NotBlank @Size(max = 16) String shortCode
    ) {}

    public record UpdateDepartmentRequest(String name, String shortCode) {}

    public record CreateTermRequest(
            @NotBlank @Size(max = 16) String code,
            @NotBlank String name,
            @NotNull TermKind kind,
            int academicYear,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn
    ) {}

    public record UpdateTermRequest(
            String code,
            String name,
            TermKind kind,
            Integer academicYear,
            LocalDate startsOn,
            LocalDate endsOn
    ) {}

    public record CreateCourseRequest(
            @NotNull UUID universityId,
            @NotBlank @Size(max = 32) String code,
            @NotBlank String name,
            BigDecimal creditPoints,
            String description
    ) {}

    public record UpdateCourseRequest(
            String code,
            String name,
            BigDecimal creditPoints,
            String description
    ) {}

    public record LinkCourseDepartmentRequest(@NotNull UUID departmentId, boolean primary) {}

    public record SetLinkPrimaryRequest(boolean primary) {}

    public record CreateOfferingRequest(@NotNull UUID termId) {}

    public record DeleteReasonRequest(@NotBlank String reason) {}
}
