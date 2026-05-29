package com.ronkadosh.bubbleup.matching.persistence;

import com.ronkadosh.bubbleup.matching.model.QuizAnswerOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizAnswerOptionRepository extends JpaRepository<QuizAnswerOption, UUID> {
    List<QuizAnswerOption> findAllByQuestionId(UUID questionId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from QuizAnswerOption o where o.questionId = :questionId")
    int deleteAllByQuestionId(@org.springframework.data.repository.query.Param("questionId") UUID questionId);
}
