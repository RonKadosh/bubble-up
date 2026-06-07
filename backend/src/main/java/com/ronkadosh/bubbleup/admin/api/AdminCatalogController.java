package com.ronkadosh.bubbleup.admin.api;

import com.ronkadosh.bubbleup.admin.api.dto.AdminCatalogDtos;
import com.ronkadosh.bubbleup.admin.application.AdminCatalogService;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.pagination.PageResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ADMIN_BASE + "/catalog")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final AdminCatalogService service;

    // ─── Universities ─────────────────────────────────────────────────────────

    @GetMapping("/universities")
    public ApiResponse<List<AdminCatalogDtos.University>> listUniversities() {
        return ApiResponse.success(service.listUniversities());
    }

    @PostMapping("/universities")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCatalogDtos.University> createUniversity(
            @Valid @RequestBody AdminCatalogDtos.CreateUniversityRequest req
    ) {
        return ApiResponse.success(service.createUniversity(req));
    }

    @GetMapping("/universities/{id}")
    public ApiResponse<AdminCatalogDtos.University> getUniversity(@PathVariable UUID id) {
        return ApiResponse.success(service.getUniversity(id));
    }

    @PatchMapping("/universities/{id}")
    public ApiResponse<AdminCatalogDtos.University> updateUniversity(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCatalogDtos.UpdateUniversityRequest req
    ) {
        return ApiResponse.success(service.updateUniversity(id, req));
    }

    @DeleteMapping("/universities/{id}")
    public ApiResponse<Void> deleteUniversity(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCatalogDtos.DeleteReasonRequest req
    ) {
        service.deleteUniversity(id, req);
        return ApiResponse.success(null);
    }

    // ─── Departments ──────────────────────────────────────────────────────────

    @GetMapping("/universities/{universityId}/departments")
    public ApiResponse<List<AdminCatalogDtos.Department>> listDepartments(@PathVariable UUID universityId) {
        return ApiResponse.success(service.listDepartments(universityId));
    }

    @PostMapping("/universities/{universityId}/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCatalogDtos.Department> createDepartment(
            @PathVariable UUID universityId,
            @Valid @RequestBody AdminCatalogDtos.CreateDepartmentRequest req
    ) {
        return ApiResponse.success(service.createDepartment(universityId, req));
    }

    @PatchMapping("/departments/{id}")
    public ApiResponse<AdminCatalogDtos.Department> updateDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCatalogDtos.UpdateDepartmentRequest req
    ) {
        return ApiResponse.success(service.updateDepartment(id, req));
    }

    @DeleteMapping("/departments/{id}")
    public ApiResponse<Void> deleteDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCatalogDtos.DeleteReasonRequest req
    ) {
        service.deleteDepartment(id, req);
        return ApiResponse.success(null);
    }

    // ─── Terms ────────────────────────────────────────────────────────────────

    @GetMapping("/universities/{universityId}/terms")
    public ApiResponse<List<AdminCatalogDtos.Term>> listTerms(@PathVariable UUID universityId) {
        return ApiResponse.success(service.listTerms(universityId));
    }

    @PostMapping("/universities/{universityId}/terms")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCatalogDtos.Term> createTerm(
            @PathVariable UUID universityId,
            @Valid @RequestBody AdminCatalogDtos.CreateTermRequest req
    ) {
        return ApiResponse.success(service.createTerm(universityId, req));
    }

    @PatchMapping("/terms/{id}")
    public ApiResponse<AdminCatalogDtos.Term> updateTerm(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCatalogDtos.UpdateTermRequest req
    ) {
        return ApiResponse.success(service.updateTerm(id, req));
    }

    @DeleteMapping("/terms/{id}")
    public ApiResponse<Void> deleteTerm(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCatalogDtos.DeleteReasonRequest req
    ) {
        service.deleteTerm(id, req);
        return ApiResponse.success(null);
    }

    @PostMapping("/terms/{sourceTermId}/rollover")
    public ApiResponse<AdminCatalogDtos.RolloverTermResponse> rolloverTerm(
            @PathVariable UUID sourceTermId,
            @Valid @RequestBody AdminCatalogDtos.RolloverTermRequest req
    ) {
        return ApiResponse.success(service.rolloverTerm(sourceTermId, req));
    }

    // ─── Courses ──────────────────────────────────────────────────────────────

    @GetMapping("/courses")
    public ApiResponse<PageResponseDto<AdminCatalogDtos.Course>> searchCourses(
            @RequestParam(required = false) UUID universityId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        return ApiResponse.success(service.searchCourses(universityId, q, page, size, sortBy, sortDirection));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<AdminCatalogDtos.CourseDetail> getCourseDetail(@PathVariable UUID id) {
        return ApiResponse.success(service.getCourseDetail(id));
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCatalogDtos.Course> createCourse(
            @Valid @RequestBody AdminCatalogDtos.CreateCourseRequest req
    ) {
        return ApiResponse.success(service.createCourse(req));
    }

    @PatchMapping("/courses/{id}")
    public ApiResponse<AdminCatalogDtos.Course> updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCatalogDtos.UpdateCourseRequest req
    ) {
        return ApiResponse.success(service.updateCourse(id, req));
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<Void> deleteCourse(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCatalogDtos.DeleteReasonRequest req
    ) {
        service.deleteCourse(id, req);
        return ApiResponse.success(null);
    }

    // ─── Course ↔ Department links ────────────────────────────────────────────

    @PostMapping("/courses/{courseId}/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCatalogDtos.CourseDepartmentLink> linkCourseDepartment(
            @PathVariable UUID courseId,
            @Valid @RequestBody AdminCatalogDtos.LinkCourseDepartmentRequest req
    ) {
        return ApiResponse.success(service.linkCourseDepartment(courseId, req));
    }

    @PatchMapping("/courses/{courseId}/departments/{deptId}")
    public ApiResponse<AdminCatalogDtos.CourseDepartmentLink> setLinkPrimary(
            @PathVariable UUID courseId,
            @PathVariable UUID deptId,
            @Valid @RequestBody AdminCatalogDtos.SetLinkPrimaryRequest req
    ) {
        return ApiResponse.success(service.setLinkPrimary(courseId, deptId, req));
    }

    @DeleteMapping("/courses/{courseId}/departments/{deptId}")
    public ApiResponse<Void> unlinkCourseDepartment(
            @PathVariable UUID courseId,
            @PathVariable UUID deptId
    ) {
        service.unlinkCourseDepartment(courseId, deptId);
        return ApiResponse.success(null);
    }

    // ─── Offerings ────────────────────────────────────────────────────────────

    @GetMapping("/courses/{courseId}/offerings")
    public ApiResponse<List<AdminCatalogDtos.Offering>> listOfferingsForCourse(@PathVariable UUID courseId) {
        return ApiResponse.success(service.listOfferingsForCourse(courseId));
    }

    @PostMapping("/courses/{courseId}/offerings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCatalogDtos.Offering> createOffering(
            @PathVariable UUID courseId,
            @Valid @RequestBody AdminCatalogDtos.CreateOfferingRequest req
    ) {
        return ApiResponse.success(service.createOffering(courseId, req));
    }

    @DeleteMapping("/offerings/{id}")
    public ApiResponse<Void> deleteOffering(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCatalogDtos.DeleteReasonRequest req
    ) {
        service.deleteOffering(id, req);
        return ApiResponse.success(null);
    }
}
