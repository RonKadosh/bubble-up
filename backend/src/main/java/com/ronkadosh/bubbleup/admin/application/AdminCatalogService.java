package com.ronkadosh.bubbleup.admin.application;

import com.ronkadosh.bubbleup.admin.api.dto.AdminCatalogDtos;
import com.ronkadosh.bubbleup.admin.audit.AdminAuditService;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.CatalogAdminInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CatalogCommands;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.OfferingAdminDto;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.pagination.PageMapper;
import com.ronkadosh.bubbleup.common.pagination.PageResponseDto;
import com.ronkadosh.bubbleup.groups.internal.GroupAdminInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCatalogService {

    private final CatalogAdminInternalService catalogAdmin;
    private final CatalogInternalService catalogInternal;
    private final GroupAdminInternalService groupAdmin;
    private final AdminAuditService audit;
    private final AdminGuards guards;
    private final PageMapper pageMapper;

    // ─── Universities ─────────────────────────────────────────────────────────

    public List<AdminCatalogDtos.University> listUniversities() {
        return catalogAdmin.listUniversities().stream().map(AdminCatalogDtos.University::from).toList();
    }

    public AdminCatalogDtos.University getUniversity(UUID id) {
        return AdminCatalogDtos.University.from(catalogAdmin.getUniversity(id));
    }

    public AdminCatalogDtos.University createUniversity(AdminCatalogDtos.CreateUniversityRequest req) {
        return AdminCatalogDtos.University.from(
                catalogAdmin.createUniversity(new CatalogCommands.CreateUniversity(
                        req.name(), req.shortCode(), req.country()))
        );
    }

    public AdminCatalogDtos.University updateUniversity(UUID id, AdminCatalogDtos.UpdateUniversityRequest req) {
        return AdminCatalogDtos.University.from(
                catalogAdmin.updateUniversity(id, new CatalogCommands.UpdateUniversity(
                        req.name(), req.shortCode(), req.country()))
        );
    }

    public void deleteUniversity(UUID id, AdminCatalogDtos.DeleteReasonRequest req) {
        guards.requireReason(req.reason());
        catalogAdmin.deleteUniversity(id);
    }

    // ─── Departments ──────────────────────────────────────────────────────────

    public List<AdminCatalogDtos.Department> listDepartments(UUID universityId) {
        return catalogAdmin.listDepartments(universityId).stream()
                .map(AdminCatalogDtos.Department::from).toList();
    }

    public AdminCatalogDtos.Department createDepartment(UUID universityId, AdminCatalogDtos.CreateDepartmentRequest req) {
        return AdminCatalogDtos.Department.from(
                catalogAdmin.createDepartment(new CatalogCommands.CreateDepartment(
                        universityId, req.name(), req.shortCode()))
        );
    }

    public AdminCatalogDtos.Department updateDepartment(UUID id, AdminCatalogDtos.UpdateDepartmentRequest req) {
        return AdminCatalogDtos.Department.from(
                catalogAdmin.updateDepartment(id, new CatalogCommands.UpdateDepartment(req.name(), req.shortCode()))
        );
    }

    public void deleteDepartment(UUID id, AdminCatalogDtos.DeleteReasonRequest req) {
        guards.requireReason(req.reason());
        catalogAdmin.deleteDepartment(id);
    }

    // ─── Terms ────────────────────────────────────────────────────────────────

    public List<AdminCatalogDtos.Term> listTerms(UUID universityId) {
        return catalogAdmin.listTerms(universityId).stream().map(AdminCatalogDtos.Term::from).toList();
    }

    public AdminCatalogDtos.Term createTerm(UUID universityId, AdminCatalogDtos.CreateTermRequest req) {
        var term = AdminCatalogDtos.Term.from(
                catalogAdmin.createTerm(new CatalogCommands.CreateTerm(
                        universityId, req.code(), req.name(), req.kind(),
                        req.academicYear(), req.startsOn(), req.endsOn()))
        );
        audit.record("TERM_CREATED", "TERM", term.id(), null,
                "{\"code\":\"" + term.code() + "\"}");
        return term;
    }

    public AdminCatalogDtos.Term updateTerm(UUID id, AdminCatalogDtos.UpdateTermRequest req) {
        return AdminCatalogDtos.Term.from(
                catalogAdmin.updateTerm(id, new CatalogCommands.UpdateTerm(
                        req.code(), req.name(), req.kind(),
                        req.academicYear(), req.startsOn(), req.endsOn()))
        );
    }

    public void deleteTerm(UUID id, AdminCatalogDtos.DeleteReasonRequest req) {
        guards.requireReason(req.reason());
        catalogAdmin.deleteTerm(id);
        audit.record("TERM_DELETED", "TERM", id, req.reason(), null);
    }

    @Transactional
    public AdminCatalogDtos.RolloverTermResponse rolloverTerm(UUID sourceTermId, AdminCatalogDtos.RolloverTermRequest req) {
        guards.requireReason(req.reason());
        var source = catalogAdmin.getTerm(sourceTermId);
        var next = AdminCatalogDtos.Term.from(catalogAdmin.createTerm(new CatalogCommands.CreateTerm(
                source.universityId(), req.code(), req.name(), req.kind(),
                req.academicYear(), req.startsOn(), req.endsOn()
        )));

        List<OfferingAdminDto> sourceOfferings = catalogInternal.offeringIdsForTerm(sourceTermId).stream()
                .map(catalogAdmin::getOffering)
                .toList();
        int copied = 0;
        for (OfferingAdminDto offering : sourceOfferings) {
            catalogAdmin.createOffering(offering.courseId(), next.id());
            copied++;
        }
        int archivedGroups = groupAdmin.setGroupStatusForOfferings(
                sourceOfferings.stream().map(OfferingAdminDto::id).toList(),
                com.ronkadosh.bubbleup.groups.model.GroupStatus.ARCHIVED
        );
        audit.record("TERM_ROLLED_OVER", "TERM", next.id(), req.reason(),
                "{\"sourceTermId\":\"" + sourceTermId + "\",\"copiedOfferings\":" + copied +
                        ",\"archivedGroups\":" + archivedGroups + "}");
        return new AdminCatalogDtos.RolloverTermResponse(next, copied, archivedGroups);
    }

    // ─── Courses ──────────────────────────────────────────────────────────────

    public PageResponseDto<AdminCatalogDtos.Course> searchCourses(
            UUID universityId, String q, int page, int size, String sortBy, String sortDirection
    ) {
        Sort.Direction dir = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortBy));
        var resultPage = catalogAdmin.searchCourses(universityId, q, pageable)
                .map(AdminCatalogDtos.Course::from);
        return pageMapper.toDto(resultPage);
    }

    public AdminCatalogDtos.CourseDetail getCourseDetail(UUID id) {
        return AdminCatalogDtos.CourseDetail.from(catalogAdmin.getCourseDetail(id));
    }

    public AdminCatalogDtos.Course createCourse(AdminCatalogDtos.CreateCourseRequest req) {
        return AdminCatalogDtos.Course.from(
                catalogAdmin.createCourse(new CatalogCommands.CreateCourse(
                        req.universityId(), req.code(), req.name(),
                        req.creditPoints(), req.description()))
        );
    }

    public AdminCatalogDtos.Course updateCourse(UUID id, AdminCatalogDtos.UpdateCourseRequest req) {
        return AdminCatalogDtos.Course.from(
                catalogAdmin.updateCourse(id, new CatalogCommands.UpdateCourse(
                        req.code(), req.name(), req.creditPoints(), req.description()))
        );
    }

    public void deleteCourse(UUID id, AdminCatalogDtos.DeleteReasonRequest req) {
        guards.requireReason(req.reason());
        // Cross-module check: would deleting this course remove any offering still used by groups?
        List<UUID> offeringIds = catalogAdmin.getCourseDetail(id).offerings().stream()
                .map(o -> o.id()).toList();
        long groups = groupAdmin.countGroupsForOfferings(offeringIds);
        if (groups > 0) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_COURSE_HAS_ACTIVE_GROUPS);
        }
        catalogAdmin.deleteCourse(id);
    }

    // ─── Course ↔ Department links ────────────────────────────────────────────

    public AdminCatalogDtos.CourseDepartmentLink linkCourseDepartment(
            UUID courseId, AdminCatalogDtos.LinkCourseDepartmentRequest req
    ) {
        return AdminCatalogDtos.CourseDepartmentLink.from(
                catalogAdmin.linkCourseDepartment(courseId, req.departmentId(), req.primary())
        );
    }

    public AdminCatalogDtos.CourseDepartmentLink setLinkPrimary(
            UUID courseId, UUID departmentId, AdminCatalogDtos.SetLinkPrimaryRequest req
    ) {
        return AdminCatalogDtos.CourseDepartmentLink.from(
                catalogAdmin.setLinkPrimary(courseId, departmentId, req.primary())
        );
    }

    public void unlinkCourseDepartment(UUID courseId, UUID departmentId) {
        catalogAdmin.unlinkCourseDepartment(courseId, departmentId);
    }

    // ─── Offerings ────────────────────────────────────────────────────────────

    public List<AdminCatalogDtos.Offering> listOfferingsForCourse(UUID courseId) {
        return catalogAdmin.listOfferingsForCourse(courseId).stream()
                .map(AdminCatalogDtos.Offering::from).toList();
    }

    public AdminCatalogDtos.Offering createOffering(UUID courseId, AdminCatalogDtos.CreateOfferingRequest req) {
        return AdminCatalogDtos.Offering.from(catalogAdmin.createOffering(courseId, req.termId()));
    }

    public void deleteOffering(UUID id, AdminCatalogDtos.DeleteReasonRequest req) {
        guards.requireReason(req.reason());
        if (groupAdmin.countGroupsForOffering(id) > 0) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_OFFERING_HAS_GROUPS);
        }
        catalogAdmin.deleteOffering(id);
    }
}
