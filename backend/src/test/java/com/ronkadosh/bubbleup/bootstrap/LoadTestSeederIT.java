package com.ronkadosh.bubbleup.bootstrap;

import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.model.CourseOffering;
import com.ronkadosh.bubbleup.catalog.persistence.CourseOfferingRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.chat.model.ChatMessage;
import com.ronkadosh.bubbleup.chat.model.ChatRoom;
import com.ronkadosh.bubbleup.chat.persistence.ChatMessageRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatRoomRepository;
import com.ronkadosh.bubbleup.enrollment.model.Enrollment;
import com.ronkadosh.bubbleup.enrollment.persistence.EnrollmentRepository;
import com.ronkadosh.bubbleup.groups.model.GroupMember;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.MembershipRole;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;
import com.ronkadosh.bubbleup.groups.persistence.GroupMemberRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupRepository;
import com.ronkadosh.bubbleup.matching.model.UserMatchCache;
import com.ronkadosh.bubbleup.matching.persistence.UserMatchCacheRepository;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the LoadTestSeeder with tiny volumes (the seeder runs on
 * ApplicationReadyEvent during context startup) and asserts the dataset's
 * invariants: counts, term-window timestamps, enrolled-only membership, and a
 * match cache scoped to enrolled current-term PUBLIC non-member groups.
 */
@TestPropertySource(properties = {
        "app.loadtest.enabled=true",
        "app.loadtest.users=24",
        "app.loadtest.courses=6",
        "app.loadtest.enrollments-per-user=2",
        "app.loadtest.groups-per-course=2",
        "app.loadtest.avg-members-per-group=4",
        "app.loadtest.avg-messages-per-group=6",
        "app.loadtest.max-messages-in-one-group=25",
        "app.loadtest.public-fraction=0.75",
        "app.loadtest.poll-fraction=0.5",
        "app.loadtest.pinned-fraction=0.5",
        "app.loadtest.calendar-fraction=0.5",
        "app.loadtest.build-match-cache=true",
        "app.loadtest.random-seed=7",
})
class LoadTestSeederIT extends IntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UniversityRepository universityRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseOfferingRepository courseOfferingRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private UserMatchCacheRepository userMatchCacheRepository;
    @Autowired private CatalogInternalService catalogInternalService;

    private List<User> loadUsers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("loadtest-"))
                .toList();
    }

    private List<UUID> loadOfferingIds() {
        UUID universityId = universityRepository.findByShortCode("BGU").orElseThrow().getId();
        List<UUID> courseIds = courseRepository.findAllByUniversityId(universityId).stream()
                .filter(c -> c.getCode().startsWith("LT-"))
                .map(Course::getId)
                .toList();
        return courseOfferingRepository.findAllByCourseIdIn(courseIds).stream()
                .map(CourseOffering::getId)
                .toList();
    }

    @Test
    void seeds_expected_volumes() {
        List<User> users = loadUsers();
        assertThat(users).hasSize(24);

        List<UUID> offeringIds = loadOfferingIds();
        assertThat(offeringIds).hasSize(6);

        long enrollments = users.stream()
                .mapToLong(u -> enrollmentRepository.findAllByUserId(u.getId()).stream()
                        .filter(e -> offeringIds.contains(e.getOfferingId()))
                        .count())
                .sum();
        assertThat(enrollments).isEqualTo(24L * 2);

        List<StudyGroup> groups = groupRepository.findAllByOfferingIdIn(offeringIds);
        assertThat(groups).hasSize(6 * 2);
    }

    @Test
    void every_group_has_room_owner_and_enrolled_members_only() {
        List<StudyGroup> groups = groupRepository.findAllByOfferingIdIn(loadOfferingIds());
        for (StudyGroup group : groups) {
            assertThat(chatRoomRepository.findAllByGroupId(group.getId()))
                    .as("group %s has a general room", group.getName())
                    .isNotEmpty();

            List<GroupMember> members = groupMemberRepository.findAllByGroupId(group.getId());
            assertThat(members).as("group %s has members", group.getName()).isNotEmpty();
            assertThat(members.stream().filter(m -> m.getRole() == MembershipRole.OWNER))
                    .as("group %s has exactly one OWNER", group.getName())
                    .hasSize(1);
            for (GroupMember member : members) {
                assertThat(enrollmentRepository.findByUserIdAndOfferingId(
                        member.getUserId(), group.getOfferingId()))
                        .as("member %s of group %s is enrolled in its offering",
                                member.getUserId(), group.getName())
                        .isPresent();
            }
        }
    }

    @Test
    void messages_are_within_the_term_window_and_one_group_is_hot() {
        var term = catalogInternalService.currentTermFor(
                universityRepository.findByShortCode("BGU").orElseThrow().getId()).orElseThrow();
        Instant termStart = term.startsOn().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant upperBound = Instant.now().plusSeconds(60);

        List<StudyGroup> groups = groupRepository.findAllByOfferingIdIn(loadOfferingIds());
        List<ChatRoom> rooms = chatRoomRepository.findAllByGroupIdIn(
                groups.stream().map(StudyGroup::getId).toList());

        long total = 0;
        long maxPerRoom = 0;
        for (ChatRoom room : rooms) {
            List<ChatMessage> messages = chatMessageRepository
                    .findAllByRoomIdOrderBySentAtDesc(room.getId(), PageRequest.of(0, 500))
                    .getContent();
            total += messages.size();
            maxPerRoom = Math.max(maxPerRoom, messages.size());
            for (ChatMessage message : messages) {
                assertThat(message.getSentAt())
                        .as("message %s within term window", message.getId())
                        .isAfterOrEqualTo(termStart)
                        .isBeforeOrEqualTo(upperBound);
            }
        }
        assertThat(total).as("chat volume seeded").isGreaterThan(0);
        // The forced hot group: budget 25 (joins + texts), possibly +poll/+calendar links.
        assertThat(maxPerRoom).as("one group runs hot").isGreaterThanOrEqualTo(20);
    }

    @Test
    void match_cache_holds_only_enrolled_current_term_public_non_member_groups() {
        List<UUID> offeringIds = loadOfferingIds();
        boolean anyCacheRows = false;
        for (User user : loadUsers()) {
            Set<UUID> enrolledOfferings = enrollmentRepository.findAllByUserId(user.getId()).stream()
                    .map(Enrollment::getOfferingId)
                    .collect(Collectors.toSet());
            List<UserMatchCache> rows = userMatchCacheRepository
                    .findByUserIdOrderByMatchScoreDesc(user.getId(), PageRequest.of(0, 100));
            for (UserMatchCache row : rows) {
                anyCacheRows = true;
                StudyGroup group = groupRepository.findById(row.getGroupId()).orElseThrow();
                assertThat(group.getVisibility())
                        .as("cached group %s is PUBLIC", group.getId())
                        .isEqualTo(GroupVisibility.PUBLIC);
                assertThat(enrolledOfferings)
                        .as("cached group's offering is one the user is enrolled in")
                        .contains(group.getOfferingId());
                assertThat(offeringIds)
                        .as("cached group belongs to a load-test offering")
                        .contains(group.getOfferingId());
                Set<UUID> memberIds = groupMemberRepository.findAllByGroupId(group.getId()).stream()
                        .map(GroupMember::getUserId)
                        .collect(Collectors.toCollection(HashSet::new));
                assertThat(memberIds)
                        .as("user is not a member of a cached candidate")
                        .doesNotContain(user.getId());
            }
        }
        assertThat(anyCacheRows).as("the real cache builder produced rows").isTrue();
    }
}
