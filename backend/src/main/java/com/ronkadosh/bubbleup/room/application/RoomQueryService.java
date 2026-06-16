package com.ronkadosh.bubbleup.room.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.common.config.JitsiProperties;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.expert.internal.ExpertInternalService;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.groups.internal.dto.GroupSummary;
import com.ronkadosh.bubbleup.room.api.dto.RoomResponse;
import com.ronkadosh.bubbleup.room.internal.RoomInternalService;
import com.ronkadosh.bubbleup.room.internal.dto.LiveRoomSummary;
import com.ronkadosh.bubbleup.room.model.Room;
import com.ronkadosh.bubbleup.room.model.RoomScope;
import com.ronkadosh.bubbleup.room.persistence.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomQueryService {

    /** GROUP rooms: how early before {@code event.startsAt} a member may open the room. */
    public static final Duration GROUP_OPEN_BEFORE = Duration.ofMinutes(15);
    /** EXPERT_SESSION rooms: chat + whiteboard open {@code EXPERT_OPEN_BEFORE} before
     *  {@code event.startsAt}; video opens at {@code startsAt}. */
    public static final Duration EXPERT_OPEN_BEFORE = Duration.ofMinutes(5);
    /** Deprecated alias preserved for backward compatibility — group window. */
    public static final Duration OPEN_BEFORE = GROUP_OPEN_BEFORE;

    private final RoomRepository roomRepository;
    private final RoomInternalService roomInternalService;
    private final RoomCallPresenceService roomCallPresenceService;
    private final GroupInternalService groupInternalService;
    private final ExpertInternalService expertInternalService;
    private final CalendarInternalService calendarInternalService;
    private final AuthInternalService authInternalService;
    private final JitsiTokenService jitsiTokenService;
    private final JitsiProperties jitsiProperties;
    private final WhiteboardRelay whiteboardRelay;
    private final TimeProvider timeProvider;

    /**
     * Group IDs (among the caller's groups) that are "live" right now — i.e. have
     * something joinable in their window. Two contributors, mirroring the dashboard
     * feed's LIVE section:
     *   - a GROUP study room in its join window (Bubble Room live), and
     *   - an enrolled expert session starting within the next 15 min or running.
     * Drives the red live marker on the My Bubbles sidebar.
     */
    @Transactional(readOnly = true)
    public List<UUID> findLiveGroupIds(UUID userId) {
        Set<UUID> groupIds = groupInternalService.getGroupsForUser(userId).stream()
                .map(GroupSummary::id)
                .collect(Collectors.toSet());
        if (groupIds.isEmpty()) return List.of();

        Instant now = timeProvider.now();
        Set<UUID> live = new HashSet<>();
        // Bubble Rooms (GROUP study sessions) live now.
        for (LiveRoomSummary r : roomInternalService.findLiveGroupRoomsForGroups(groupIds, now)) {
            live.add(r.groupId());
        }
        // Expert sessions joinable now (mirrors LiveExpertSessionSource's 15-min window).
        Instant until = now.plus(GROUP_OPEN_BEFORE);
        for (UUID groupId : groupIds) {
            if (!expertInternalService.findActiveSessionsOverlappingForGroup(groupId, now, until).isEmpty()) {
                live.add(groupId);
            }
        }
        return List.copyOf(live);
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoom(UUID roomId, CurrentUser requester) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        return buildResponse(room, requester);
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoomForCalendarEvent(UUID eventId, CurrentUser requester) {
        Room room = roomRepository.findByCalendarEventId(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        return buildResponse(room, requester);
    }

    @Transactional(readOnly = true)
    public ExcalidrawSnapshot getCurrentWhiteboard(UUID roomId, UUID requesterId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        requireMember(room, requesterId);
        return whiteboardRelay.get(roomId);
    }

    private RoomResponse buildResponse(Room room, CurrentUser requester) {
        requireMember(room, requester.id());
        CalendarEventSummary event = room.getCalendarEventId() != null
                ? calendarInternalService.findById(room.getCalendarEventId()).orElse(null)
                : null;
        // EXPERT_SESSION host owns the room and can arrive early to prep.
        // Both flags are reused below — host implies moderator on Jitsi.
        boolean isHost = room.getScope() == RoomScope.EXPERT_SESSION
                && room.getExpertSessionId() != null
                && expertInternalService.isSessionHost(room.getExpertSessionId(), requester.id());
        requireOpen(room, event, isHost);
        // EXPERT_SESSION rooms split chat/whiteboard (open at startsAt - 5min) from
        // video (opens at startsAt). Before videoOpensAt we skip Jitsi token issuance
        // entirely — the page renders the chat + whiteboard cells and a countdown
        // placeholder in the Video cell.
        Instant videoOpensAt = computeVideoOpensAt(room, event);
        Instant now = timeProvider.now();
        boolean videoOpen = videoOpensAt != null && !now.isBefore(videoOpensAt);
        String jwt = null;
        if (videoOpen || isHost) {
            // The token is NOT capped at endsAt: a room stays alive past its scheduled
            // end while the call is occupied (see RoomLifecycleScheduler), so the JWT must
            // outlive endsAt or JaaS would kick everyone at the scheduled end. A fresh token
            // is minted on every GET, so overtime sessions keep getting valid tokens; the
            // server-side empty-close (ENDED broadcast) is the real lifecycle bound. The
            // token service still bounds each token by tokenTtl (default 2h).
            Instant hardExpiry = null;
            UserIdentity identity = authInternalService.getIdentity(requester.id()).orElse(null);
            // GROUP rooms: no Jitsi moderator. EXPERT_SESSION rooms: the host gets
            // moderator privileges (mute, kick, share-screen permissions), participants don't.
            boolean moderator = isHost;
            jwt = jitsiTokenService.issue(
                    room,
                    requester.id(),
                    identity != null ? identity.displayName() : null,
                    requester.email(),
                    identity != null ? identity.avatarUrl() : null,
                    moderator,
                    hardExpiry
            );
        }
        return RoomResponse.of(room, event, jitsiProperties, jwt, videoOpensAt,
                roomCallPresenceService.count(room.getId()));
    }

    /**
     * Time-window gate: a member can open the room only from
     * {@code event.startsAt - openBefore} onwards. The {@code openBefore} window
     * depends on scope — 15 min for GROUP rooms, 5 min for EXPERT_SESSION rooms.
     *
     * <p>There is intentionally <b>no upper bound at {@code endsAt}</b>: a room
     * stays alive past its scheduled end while the call is occupied, and a member
     * may join or rejoin during that overtime (rejoining is what keeps it alive).
     * Joining is closed only once the lifecycle scheduler stamps {@code Room.endedAt}
     * (set after the call has been empty past {@code endsAt} for the grace window) —
     * ended is ended for everyone.
     *
     * <p>The EXPERT_SESSION host is exempt from the "not yet open" gate so they
     * can arrive early to set up the room.
     */
    private void requireOpen(Room room, CalendarEventSummary event, boolean isHost) {
        if (event == null) {
            // V1 GROUP scope always has a calendar event; an orphaned room is a bug, not a join path.
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }
        Instant now = timeProvider.now();
        if (!isHost) {
            Duration openBefore = room.getScope() == RoomScope.EXPERT_SESSION
                    ? EXPERT_OPEN_BEFORE
                    : GROUP_OPEN_BEFORE;
            Instant opensAt = event.startsAt().minus(openBefore);
            if (now.isBefore(opensAt)) {
                throw new AppException(room.getScope() == RoomScope.EXPERT_SESSION
                        ? ErrorCode.EXPERT_SESSION_NOT_OPEN_FOR_JOIN_YET
                        : ErrorCode.ROOM_NOT_YET_OPEN);
            }
        }
        if (room.getEndedAt() != null) {
            throw new AppException(ErrorCode.ROOM_ENDED);
        }
    }

    /**
     * When the Video cell becomes joinable. GROUP rooms: same as the join window
     * ({@code startsAt - 15min}). EXPERT_SESSION rooms: {@code startsAt}.
     */
    private static Instant computeVideoOpensAt(Room room, CalendarEventSummary event) {
        if (event == null) return null;
        return room.getScope() == RoomScope.EXPERT_SESSION
                ? event.startsAt()
                : event.startsAt().minus(GROUP_OPEN_BEFORE);
    }

    private void requireMember(Room room, UUID userId) {
        if (room.getScope() == RoomScope.GROUP) {
            UUID groupId = room.getGroupId();
            if (groupId == null || !groupInternalService.isMember(groupId, userId)) {
                throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
            }
            return;
        }
        if (room.getScope() == RoomScope.EXPERT_SESSION) {
            UUID sessionId = room.getExpertSessionId();
            if (sessionId == null || !expertInternalService.isAuthorizedForSession(sessionId, userId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            return;
        }
        throw new AppException(ErrorCode.FORBIDDEN);
    }
}
