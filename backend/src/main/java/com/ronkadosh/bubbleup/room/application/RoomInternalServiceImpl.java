package com.ronkadosh.bubbleup.room.application;

import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.internal.dto.ChatRoomSummary;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.expert.application.WhiteboardWriterRegistry;
import com.ronkadosh.bubbleup.room.internal.RoomInternalService;
import com.ronkadosh.bubbleup.room.internal.dto.LiveRoomSummary;
import com.ronkadosh.bubbleup.room.internal.dto.RoomSummary;
import com.ronkadosh.bubbleup.room.model.Room;
import com.ronkadosh.bubbleup.room.model.RoomScope;
import com.ronkadosh.bubbleup.room.persistence.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomInternalServiceImpl implements RoomInternalService {

    private final RoomRepository roomRepository;
    private final ChatInternalService chatInternalService;
    private final CalendarInternalService calendarInternalService;
    private final WhiteboardRelay whiteboardRelay;
    private final WhiteboardWriterRegistry whiteboardWriterRegistry;
    private final RoomCallPresenceService roomCallPresenceService;

    @Override
    @Transactional
    public UUID createRoomForCalendarEvent(UUID calendarEventId, UUID createdBy) {
        return roomRepository.findByCalendarEventId(calendarEventId)
                .map(Room::getId)
                .orElseGet(() -> createNewRoom(calendarEventId, createdBy));
    }

    private UUID createNewRoom(UUID eventId, UUID createdBy) {
        CalendarEventSummary event = calendarInternalService.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
        if (event.eventType() != CalendarEventType.STUDY_SESSION) {
            throw new AppException(ErrorCode.ROOM_REQUIRES_STUDY_SESSION_EVENT);
        }
        if (event.ownerType() != CalendarOwnerType.GROUP) {
            throw new AppException(ErrorCode.ROOM_REQUIRES_STUDY_SESSION_EVENT);
        }
        UUID groupId = event.ownerId();
        UUID chatRoomId = chatInternalService.getRoomsForGroup(groupId).stream()
                .findFirst()
                .map(ChatRoomSummary::id)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        String jitsiRoomName = "bubble-" + groupId + "-" + eventId;
        Room saved = roomRepository.save(Room.builder()
                .scope(RoomScope.GROUP)
                .calendarEventId(eventId)
                .groupId(groupId)
                .chatRoomId(chatRoomId)
                .jitsiRoomName(jitsiRoomName)
                .whiteboardEnabled(true)
                .createdBy(createdBy)
                .build());
        return saved.getId();
    }

    @Override
    @Transactional
    public void deleteRoomForCalendarEvent(UUID calendarEventId) {
        roomRepository.findByCalendarEventId(calendarEventId).ifPresent(room -> {
            whiteboardRelay.clear(room.getId());
            if (room.getExpertSessionId() != null) {
                whiteboardWriterRegistry.clear(room.getExpertSessionId());
            }
            roomRepository.delete(room);
        });
    }

    @Override
    @Transactional
    public UUID createRoomForExpertSession(UUID expertSessionId, UUID calendarEventId, UUID createdBy) {
        return roomRepository.findByExpertSessionId(expertSessionId)
                .map(Room::getId)
                .orElseGet(() -> {
                    UUID chatRoomId = chatInternalService.createRoomForExpertSession(
                            expertSessionId, "Expert session");
                    String jitsiRoomName = "expert-" + expertSessionId;
                    Room saved = roomRepository.save(Room.builder()
                            .scope(RoomScope.EXPERT_SESSION)
                            .calendarEventId(calendarEventId)
                            .expertSessionId(expertSessionId)
                            .chatRoomId(chatRoomId)
                            .jitsiRoomName(jitsiRoomName)
                            .whiteboardEnabled(true)
                            .createdBy(createdBy)
                            .build());
                    return saved.getId();
                });
    }

    @Override
    @Transactional
    public void deleteRoomForExpertSession(UUID expertSessionId) {
        roomRepository.findByExpertSessionId(expertSessionId).ifPresent(room -> {
            whiteboardRelay.clear(room.getId());
            whiteboardWriterRegistry.clear(expertSessionId);
            roomRepository.delete(room);
            chatInternalService.deleteRoomForExpertSession(expertSessionId);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoomSummary> findRoomForExpertSession(UUID expertSessionId) {
        return roomRepository.findByExpertSessionId(expertSessionId).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoomSummary> findRoomForCalendarEvent(UUID calendarEventId) {
        return roomRepository.findByCalendarEventId(calendarEventId).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoomSummary> findById(UUID roomId) {
        return roomRepository.findById(roomId).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean roomExists(UUID roomId) {
        return roomRepository.existsById(roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiveRoomSummary> findLiveGroupRoomsForGroups(Set<UUID> groupIds, Instant now) {
        if (groupIds == null || groupIds.isEmpty()) return List.of();
        List<LiveRoomSummary> out = new ArrayList<>();
        for (Room room : roomRepository.findAllByGroupIdInAndEndedAtIsNull(groupIds)) {
            if (room.getScope() != RoomScope.GROUP || room.getCalendarEventId() == null) continue;
            CalendarEventSummary event =
                    calendarInternalService.findById(room.getCalendarEventId()).orElse(null);
            if (event == null) continue;
            // Joinable window: from startsAt - GROUP_OPEN_BEFORE onwards. There's no hard
            // upper bound at endsAt — a room stays live past its scheduled end while the
            // call is occupied (mirrors RoomQueryService.requireOpen + the lifecycle
            // scheduler). So past endsAt the room is only "live" if someone is still in it.
            Instant opensAt = event.startsAt().minus(RoomQueryService.GROUP_OPEN_BEFORE);
            if (now.isBefore(opensAt)) continue;
            if (now.isAfter(event.endsAt()) && roomCallPresenceService.count(room.getId()) == 0) continue;
            out.add(new LiveRoomSummary(
                    room.getId(),
                    room.getGroupId(),
                    room.getCalendarEventId(),
                    event.startsAt(),
                    event.endsAt(),
                    roomCallPresenceService.count(room.getId())));
        }
        return out;
    }

    @Override
    public int callParticipantCount(UUID roomId) {
        return roomCallPresenceService.count(roomId);
    }

    private RoomSummary toSummary(Room room) {
        return new RoomSummary(
                room.getId(),
                room.getScope(),
                room.getGroupId(),
                room.getExpertSessionId(),
                room.getCalendarEventId(),
                room.getChatRoomId(),
                room.getJitsiRoomName(),
                room.isWhiteboardEnabled()
        );
    }
}
