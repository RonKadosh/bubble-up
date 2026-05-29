package com.ronkadosh.bubbleup.admin.api.dto;

import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizOptionAdminDto;
import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizQuestionAdminDto;
import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizQuestionDetailDto;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public final class AdminQuizDtos {
    private AdminQuizDtos() {}

    public record Question(UUID id, String textEn, String textHe, int orderIndex, boolean active) {
        public static Question from(QuizQuestionAdminDto d) {
            return new Question(d.id(), d.textEn(), d.textHe(), d.orderIndex(), d.active());
        }
    }

    public record Option(
            UUID id, UUID questionId, String textEn, String textHe,
            double weightLeader, double weightPlanner, double weightExpert,
            double weightCreative, double weightCommunicator,
            double weightTeamPlayer, double weightChallenger
    ) {
        public static Option from(QuizOptionAdminDto d) {
            return new Option(
                    d.id(), d.questionId(), d.textEn(), d.textHe(),
                    d.weightLeader(), d.weightPlanner(), d.weightExpert(),
                    d.weightCreative(), d.weightCommunicator(),
                    d.weightTeamPlayer(), d.weightChallenger()
            );
        }
    }

    public record QuestionDetail(Question question, List<Option> options) {
        public static QuestionDetail from(QuizQuestionDetailDto d) {
            return new QuestionDetail(
                    Question.from(d.question()),
                    d.options().stream().map(Option::from).toList()
            );
        }
    }

    public record CreateQuestionRequest(@NotBlank String textEn, String textHe, Integer orderIndex, Boolean active) {}
    public record UpdateQuestionRequest(String textEn, String textHe, Integer orderIndex, Boolean active) {}
    public record SetActiveRequest(boolean active) {}

    public record CreateOptionRequest(
            @NotBlank String textEn,
            String textHe,
            Double weightLeader,
            Double weightPlanner,
            Double weightExpert,
            Double weightCreative,
            Double weightCommunicator,
            Double weightTeamPlayer,
            Double weightChallenger
    ) {}

    public record UpdateOptionRequest(
            String textEn,
            String textHe,
            Double weightLeader,
            Double weightPlanner,
            Double weightExpert,
            Double weightCreative,
            Double weightCommunicator,
            Double weightTeamPlayer,
            Double weightChallenger
    ) {}
}
