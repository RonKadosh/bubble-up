package com.ronkadosh.bubbleup.admin.api;

import com.ronkadosh.bubbleup.admin.api.dto.AdminQuizDtos;
import com.ronkadosh.bubbleup.admin.application.AdminQuizService;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ADMIN_BASE + "/quiz")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminQuizController {

    private final AdminQuizService service;

    @GetMapping("/questions")
    public ApiResponse<List<AdminQuizDtos.QuestionDetail>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminQuizDtos.Question> createQuestion(
            @Valid @RequestBody AdminQuizDtos.CreateQuestionRequest req
    ) {
        return ApiResponse.success(service.createQuestion(req));
    }

    @PatchMapping("/questions/{id}")
    public ApiResponse<AdminQuizDtos.Question> updateQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody AdminQuizDtos.UpdateQuestionRequest req
    ) {
        return ApiResponse.success(service.updateQuestion(id, req));
    }

    @PatchMapping("/questions/{id}/active")
    public ApiResponse<AdminQuizDtos.Question> setActive(
            @PathVariable UUID id,
            @Valid @RequestBody AdminQuizDtos.SetActiveRequest req
    ) {
        return ApiResponse.success(service.setActive(id, req));
    }

    @DeleteMapping("/questions/{id}")
    public ApiResponse<Void> deleteQuestion(@PathVariable UUID id) {
        service.deleteQuestion(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/questions/{questionId}/options")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminQuizDtos.Option> createOption(
            @PathVariable UUID questionId,
            @Valid @RequestBody AdminQuizDtos.CreateOptionRequest req
    ) {
        return ApiResponse.success(service.createOption(questionId, req));
    }

    @PatchMapping("/options/{id}")
    public ApiResponse<AdminQuizDtos.Option> updateOption(
            @PathVariable UUID id,
            @Valid @RequestBody AdminQuizDtos.UpdateOptionRequest req
    ) {
        return ApiResponse.success(service.updateOption(id, req));
    }

    @DeleteMapping("/options/{id}")
    public ApiResponse<Void> deleteOption(@PathVariable UUID id) {
        service.deleteOption(id);
        return ApiResponse.success(null);
    }
}
