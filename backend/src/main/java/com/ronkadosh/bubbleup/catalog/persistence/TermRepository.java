package com.ronkadosh.bubbleup.catalog.persistence;

import com.ronkadosh.bubbleup.catalog.model.Term;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TermRepository extends JpaRepository<Term, UUID> {

    List<Term> findAllByUniversityIdOrderByStartsOnAsc(UUID universityId);

    Optional<Term> findByUniversityIdAndCode(UUID universityId, String code);

    long countByUniversityId(UUID universityId);

    /**
     * Terms whose date window contains {@code today}. Usually 0 or 1 row, but
     * universities with overlapping summer/fall sessions can return more — the
     * caller (see {@code CatalogInternalServiceImpl.currentTermFor}) applies the
     * tiebreak rule.
     */
    List<Term> findAllByUniversityIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(
            UUID universityId, LocalDate todayStart, LocalDate todayEnd);

    Optional<Term> findFirstByUniversityIdAndStartsOnAfterOrderByStartsOnAsc(
            UUID universityId, LocalDate today);
}
