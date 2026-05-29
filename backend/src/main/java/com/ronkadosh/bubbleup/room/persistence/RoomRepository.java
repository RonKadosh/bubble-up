package com.ronkadosh.bubbleup.room.persistence;

import com.ronkadosh.bubbleup.room.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByCalendarEventId(UUID calendarEventId);

    Optional<Room> findByExpertSessionId(UUID expertSessionId);

    Optional<Room> findByJitsiRoomName(String jitsiRoomName);

    void deleteByCalendarEventId(UUID calendarEventId);

    boolean existsByCalendarEventId(UUID calendarEventId);

    /** All rooms still considered "live" (lifecycle scheduler hasn't closed them yet). */
    List<Room> findAllByEndedAtIsNull();
}
