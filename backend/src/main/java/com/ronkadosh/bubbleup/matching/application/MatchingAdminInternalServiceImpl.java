package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.matching.internal.MatchingAdminInternalService;
import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizCommands;
import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizOptionAdminDto;
import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizQuestionAdminDto;
import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizQuestionDetailDto;
import com.ronkadosh.bubbleup.matching.model.QuizAnswerOption;
import com.ronkadosh.bubbleup.matching.model.QuizQuestion;
import com.ronkadosh.bubbleup.matching.persistence.QuizAnswerOptionRepository;
import com.ronkadosh.bubbleup.matching.persistence.QuizQuestionRepository;
import com.ronkadosh.bubbleup.matching.persistence.QuizResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchingAdminInternalServiceImpl implements MatchingAdminInternalService {

    private final QuizQuestionRepository questionRepo;
    private final QuizAnswerOptionRepository optionRepo;
    private final QuizResponseRepository responseRepo;

    @Override
    @Transactional(readOnly = true)
    public List<QuizQuestionDetailDto> listAllWithOptions() {
        List<QuizQuestion> questions = questionRepo.findAll().stream()
                .sorted(Comparator.comparingInt(QuizQuestion::getOrderIndex))
                .toList();
        return questions.stream().map(q -> {
            List<QuizOptionAdminDto> opts = optionRepo.findAllByQuestionId(q.getId()).stream()
                    .map(MatchingAdminInternalServiceImpl::toOptionDto)
                    .toList();
            return new QuizQuestionDetailDto(toQuestionDto(q), opts);
        }).toList();
    }

    @Override
    @Transactional
    public QuizQuestionAdminDto createQuestion(QuizCommands.CreateQuestion cmd) {
        int order = cmd.orderIndex() != null
                ? cmd.orderIndex()
                : (int) questionRepo.count();
        QuizQuestion q = QuizQuestion.builder()
                .textEn(cmd.textEn())
                .textHe(cmd.textHe())
                .orderIndex(order)
                .active(cmd.active() == null ? true : cmd.active())
                .build();
        return toQuestionDto(questionRepo.save(q));
    }

    @Override
    @Transactional
    public QuizQuestionAdminDto updateQuestion(UUID questionId, QuizCommands.UpdateQuestion cmd) {
        QuizQuestion q = questionRepo.findById(questionId)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND));
        if (cmd.textEn() != null) q.setTextEn(cmd.textEn());
        if (cmd.textHe() != null) q.setTextHe(cmd.textHe());
        if (cmd.orderIndex() != null) q.setOrderIndex(cmd.orderIndex());
        if (cmd.active() != null) q.setActive(cmd.active());
        return toQuestionDto(questionRepo.save(q));
    }

    @Override
    @Transactional
    public QuizQuestionAdminDto setActive(UUID questionId, boolean active) {
        QuizQuestion q = questionRepo.findById(questionId)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND));
        q.setActive(active);
        return toQuestionDto(questionRepo.save(q));
    }

    @Override
    @Transactional
    public void deleteQuestion(UUID questionId) {
        if (!questionRepo.existsById(questionId)) {
            throw new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND);
        }
        responseRepo.deleteAllByQuestionId(questionId);
        optionRepo.deleteAllByQuestionId(questionId);
        questionRepo.deleteById(questionId);
    }

    @Override
    @Transactional
    public QuizOptionAdminDto createOption(UUID questionId, QuizCommands.CreateOption cmd) {
        if (!questionRepo.existsById(questionId)) {
            throw new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND);
        }
        QuizAnswerOption o = QuizAnswerOption.builder()
                .questionId(questionId)
                .textEn(cmd.textEn())
                .textHe(cmd.textHe())
                .weightLeader(orZero(cmd.weightLeader()))
                .weightPlanner(orZero(cmd.weightPlanner()))
                .weightExpert(orZero(cmd.weightExpert()))
                .weightCreative(orZero(cmd.weightCreative()))
                .weightCommunicator(orZero(cmd.weightCommunicator()))
                .weightTeamPlayer(orZero(cmd.weightTeamPlayer()))
                .weightChallenger(orZero(cmd.weightChallenger()))
                .build();
        return toOptionDto(optionRepo.save(o));
    }

    @Override
    @Transactional
    public QuizOptionAdminDto updateOption(UUID optionId, QuizCommands.UpdateOption cmd) {
        QuizAnswerOption o = optionRepo.findById(optionId)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND));
        if (cmd.textEn() != null) o.setTextEn(cmd.textEn());
        if (cmd.textHe() != null) o.setTextHe(cmd.textHe());
        if (cmd.weightLeader() != null) o.setWeightLeader(cmd.weightLeader());
        if (cmd.weightPlanner() != null) o.setWeightPlanner(cmd.weightPlanner());
        if (cmd.weightExpert() != null) o.setWeightExpert(cmd.weightExpert());
        if (cmd.weightCreative() != null) o.setWeightCreative(cmd.weightCreative());
        if (cmd.weightCommunicator() != null) o.setWeightCommunicator(cmd.weightCommunicator());
        if (cmd.weightTeamPlayer() != null) o.setWeightTeamPlayer(cmd.weightTeamPlayer());
        if (cmd.weightChallenger() != null) o.setWeightChallenger(cmd.weightChallenger());
        return toOptionDto(optionRepo.save(o));
    }

    @Override
    @Transactional
    public void deleteOption(UUID optionId) {
        if (!optionRepo.existsById(optionId)) {
            throw new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND);
        }
        responseRepo.deleteAllByAnswerId(optionId);
        optionRepo.deleteById(optionId);
    }

    private static double orZero(Double v) {
        return v == null ? 0.0 : v;
    }

    private static QuizQuestionAdminDto toQuestionDto(QuizQuestion q) {
        return new QuizQuestionAdminDto(q.getId(), q.getTextEn(), q.getTextHe(), q.getOrderIndex(), q.isActive());
    }

    private static QuizOptionAdminDto toOptionDto(QuizAnswerOption o) {
        return new QuizOptionAdminDto(
                o.getId(), o.getQuestionId(), o.getTextEn(), o.getTextHe(),
                o.getWeightLeader(), o.getWeightPlanner(), o.getWeightExpert(),
                o.getWeightCreative(), o.getWeightCommunicator(),
                o.getWeightTeamPlayer(), o.getWeightChallenger()
        );
    }
}
