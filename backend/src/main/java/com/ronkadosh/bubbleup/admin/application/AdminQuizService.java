package com.ronkadosh.bubbleup.admin.application;

import com.ronkadosh.bubbleup.admin.api.dto.AdminQuizDtos;
import com.ronkadosh.bubbleup.matching.internal.MatchingAdminInternalService;
import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizCommands;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminQuizService {

    private final MatchingAdminInternalService matchingAdmin;

    public List<AdminQuizDtos.QuestionDetail> list() {
        return matchingAdmin.listAllWithOptions().stream()
                .map(AdminQuizDtos.QuestionDetail::from)
                .toList();
    }

    public AdminQuizDtos.Question createQuestion(AdminQuizDtos.CreateQuestionRequest req) {
        return AdminQuizDtos.Question.from(
                matchingAdmin.createQuestion(new QuizCommands.CreateQuestion(
                        req.textEn(), req.textHe(), req.orderIndex(), req.active()))
        );
    }

    public AdminQuizDtos.Question updateQuestion(UUID id, AdminQuizDtos.UpdateQuestionRequest req) {
        return AdminQuizDtos.Question.from(
                matchingAdmin.updateQuestion(id, new QuizCommands.UpdateQuestion(
                        req.textEn(), req.textHe(), req.orderIndex(), req.active()))
        );
    }

    public AdminQuizDtos.Question setActive(UUID id, AdminQuizDtos.SetActiveRequest req) {
        return AdminQuizDtos.Question.from(matchingAdmin.setActive(id, req.active()));
    }

    public void deleteQuestion(UUID id) {
        matchingAdmin.deleteQuestion(id);
    }

    public AdminQuizDtos.Option createOption(UUID questionId, AdminQuizDtos.CreateOptionRequest req) {
        return AdminQuizDtos.Option.from(
                matchingAdmin.createOption(questionId, new QuizCommands.CreateOption(
                        req.textEn(), req.textHe(),
                        req.weightLeader(), req.weightPlanner(), req.weightExpert(),
                        req.weightCreative(), req.weightCommunicator(),
                        req.weightTeamPlayer(), req.weightChallenger()))
        );
    }

    public AdminQuizDtos.Option updateOption(UUID id, AdminQuizDtos.UpdateOptionRequest req) {
        return AdminQuizDtos.Option.from(
                matchingAdmin.updateOption(id, new QuizCommands.UpdateOption(
                        req.textEn(), req.textHe(),
                        req.weightLeader(), req.weightPlanner(), req.weightExpert(),
                        req.weightCreative(), req.weightCommunicator(),
                        req.weightTeamPlayer(), req.weightChallenger()))
        );
    }

    public void deleteOption(UUID id) {
        matchingAdmin.deleteOption(id);
    }
}
