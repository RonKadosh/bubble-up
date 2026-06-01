package com.ronkadosh.bubbleup.calendar.persistence;

import com.ronkadosh.bubbleup.calendar.model.CalendarEvent;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
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

    @Query("select e from CalendarEvent e where e.ownerType = :type and e.ownerId = :ownerId")
    List<CalendarEvent> findAllByOwner(@Param("type") CalendarOwnerType type, @Param("ownerId") UUID ownerId);

    /**
     * Upcoming events across many owners (e.g. all of a user's groups), starting within
     * {@code [from, to]}, soonest first. Drives the dashboard feed's "Upcoming" section.
     */
    @Query("""
            select e from CalendarEvent e
            where e.ownerType = :type and e.ownerId in :ownerIds
              and e.startsAt >= :from and e.startsAt <= :to
            order by e.startsAt asc
            """)
    List<CalendarEvent> findUpcomingForOwners(
            @Param("type") CalendarOwnerType type,
            @Param("ownerIds") Collection<UUID> ownerIds,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    /** Count of upcoming events for the given owners within {@code [from, to]} — matching trending signal. */
    @Query("""
            select count(e) from CalendarEvent e
            where e.ownerType = :type and e.ownerId in :ownerIds
              and e.startsAt >= :from and e.startsAt <= :to
            """)
    long countUpcomingForOwners(
            @Param("type") CalendarOwnerType type,
            @Param("ownerIds") Collection<UUID> ownerIds,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * Returns the owner's {@code STUDY_SESSION} events that overlap the given
     * window — i.e. anything that would conflict with a proposed
     * {@code [startsAt, endsAt)} booking. Half-open interval: an event ending
     * exactly at {@code startsAt} does not conflict.
     *
     * <p>Used by the "one active session per group at a time" rule when
     * scheduling/extending a Bubble Room or enrolling in an expert session.
     */
    @Query("""
            select e from CalendarEvent e
            where e.ownerType = :type and e.ownerId = :ownerId
              and e.eventType = com.ronkadosh.bubbleup.calendar.model.CalendarEventType.STUDY_SESSION
              and e.endsAt > :startsAt and e.startsAt < :endsAt
            """)
    List<CalendarEvent> findGroupStudySessionsOverlapping(
            @Param("type") CalendarOwnerType type,
            @Param("ownerId") UUID ownerId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    @Modifying
    @Query("delete from CalendarEvent e where e.ownerType = :type and e.ownerId = :ownerId")
    int deleteAllByOwner(@Param("type") CalendarOwnerType type, @Param("ownerId") UUID ownerId);
}
