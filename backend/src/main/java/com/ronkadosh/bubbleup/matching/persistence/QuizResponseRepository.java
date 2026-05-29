package com.ronkadosh.bubbleup.matching.persistence;

import com.ronkadosh.bubbleup.matching.model.QuizResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizResponseRepository extends JpaRepository<QuizResponse, UUID> {
    Optional<QuizResponse> findByUserIdAndQuestionId(UUID userId, UUID questionId);
    List<QuizResponse> findAllByUserId(UUID userId);
    long countByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from QuizResponse r where r.questionId = :questionId")
    int deleteAllByQuestionId(@org.springframework.data.repository.query.Param("questionId") UUID questionId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from QuizResponse r where r.answerId = :answerId")
    int deleteAllByAnswerId(@org.springframework.data.repository.query.Param("answerId") UUID answerId);
}
