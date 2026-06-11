package com.ronkadosh.bubbleup.catalog.api;

import com.ronkadosh.bubbleup.catalog.api.dto.CourseSummary;
import com.ronkadosh.bubbleup.catalog.api.dto.DepartmentSummary;
import com.ronkadosh.bubbleup.catalog.api.dto.OfferingSummary;
import com.ronkadosh.bubbleup.catalog.api.dto.TermSummary;
import com.ronkadosh.bubbleup.catalog.api.dto.UniversitySummary;
import com.ronkadosh.bubbleup.catalog.application.CatalogQueryService;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.CATALOG_BASE)
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogQueryService queries;

    @GetMapping("/universities")
    public ApiResponse<List<UniversitySummary>> listUniversities() {
        return ApiResponse.success(queries.listUniversities());
    }

    @GetMapping("/universities/{id}/departments")
    public ApiResponse<List<DepartmentSummary>> listDepartments(@PathVariable UUID id) {
        return ApiResponse.success(queries.listDepartments(id));
    }

    @GetMapping("/universities/{id}/terms")
    public ApiResponse<List<TermSummary>> listTerms(@PathVariable UUID id) {
        return ApiResponse.success(queries.listTerms(id));
    }

    @GetMapping("/universities/{id}/current-term")
    public ApiResponse<TermSummary> getCurrentTerm(@PathVariable UUID id) {
        return ApiResponse.success(queries.getCurrentTerm(id));
    }

    @GetMapping("/departments/{id}/courses")
    public ApiResponse<List<CourseSummary>> listCoursesByDepartment(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID termId
    ) {
        return ApiResponse.success(queries.listCoursesByDepartment(id, termId));
    }

    @GetMapping("/courses/search")
    public ApiResponse<List<CourseSummary>> searchCourses(
            @RequestParam UUID universityId,
            @RequestParam UUID termId,
            @RequestParam String q
    ) {
        return ApiResponse.success(queries.searchCourses(universityId, termId, q));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<CourseSummary> getCourse(@PathVariable UUID id) {
        return ApiResponse.success(queries.getCourse(id));
    }

    @GetMapping("/courses/{id}/offerings")
    public ApiResponse<List<OfferingSummary>> listOfferingsForCourse(@PathVariable UUID id) {
        return ApiResponse.success(queries.listOfferingsForCourse(id));
    }

    @GetMapping("/courses/{id}/offerings/current")
    public ApiResponse<OfferingSummary> getCurrentOfferingForCourse(@PathVariable UUID id) {
        return ApiResponse.success(queries.getCurrentOfferingForCourse(id));
    }

    @GetMapping("/offerings/{id}")
    public ApiResponse<OfferingSummary> getOffering(@PathVariable UUID id) {
        return ApiResponse.success(queries.getOffering(id));
    }
}
