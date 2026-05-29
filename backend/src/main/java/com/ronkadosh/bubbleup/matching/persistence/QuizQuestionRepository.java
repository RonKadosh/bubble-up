package com.ronkadosh.bubbleup.matching.persistence;

import com.ronkadosh.bubbleup.matching.model.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {
    List<QuizQuestion> findByActiveOrderByOrderIndexAsc(boolean active);
    long countByActive(boolean active);

    @Query("select q from QuizQuestion q where q.active = true and q.id not in " +
           "(select r.questionId from QuizResponse r where r.userId = :userId)")
    List<QuizQuestion> findUnansweredActiveByUserId(@Param("userId") UUID userId);
}
