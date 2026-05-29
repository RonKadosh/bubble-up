package com.ronkadosh.bubbleup.enrollment.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.enrollment.api.dto.EnrollRequest;
import com.ronkadosh.bubbleup.enrollment.api.dto.EnrollmentResponse;
import com.ronkadosh.bubbleup.enrollment.application.EnrollmentCommandService;
import com.ronkadosh.bubbleup.enrollment.application.EnrollmentQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ENROLLMENTS_BASE)
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentCommandService commands;
    private final EnrollmentQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me")
    public ApiResponse<List<EnrollmentResponse>> listMine() {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.listMine(me.id()));
    }

    @GetMapping("/me/current")
    public ApiResponse<List<EnrollmentResponse>> listMineForCurrentTerm() {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.listMineForCurrentTerm(me.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EnrollmentResponse> enroll(@Valid @RequestBody EnrollRequest req) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.enroll(me.id(), req.courseId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> unenroll(@PathVariable UUID id) {
        CurrentUser me = currentUserProvider.get();
        commands.unenroll(me.id(), id);
        return ApiResponse.success(null);
    }
}
