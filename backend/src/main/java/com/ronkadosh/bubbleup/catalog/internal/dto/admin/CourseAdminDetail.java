package com.ronkadosh.bubbleup.catalog.internal.dto.admin;

import java.util.List;

public record CourseAdminDetail(
        CourseAdminDto course,
        List<OfferingAdminDto> offerings,
        List<CourseDepartmentLinkDto> departmentLinks
) {}
