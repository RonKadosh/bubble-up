package com.ronkadosh.studybuddy.calendar.persistence;

import com.ronkadosh.studybuddy.calendar.model.CalendarEvent;
import com.ronkadosh.studybuddy.calendar.model.CalendarOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    @Query("""
            select e from CalendarEvent e
            where e.ownerType = :type and e.ownerId = :ownerId
              and e.endsAt >= :from and e.startsAt <= :to
            order by e.startsAt asc
            """)
    List<CalendarEvent> findInRange(
            @Param("type") CalendarOwnerType type,
            @Param("ownerId") UUID ownerId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Modifying
    @Query("delete from CalendarEvent e where e.ownerType = :type and e.ownerId = :ownerId")
    int deleteAllByOwner(@Param("type") CalendarOwnerType type, @Param("ownerId") UUID ownerId);
}
