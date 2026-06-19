package com.ronkadosh.bubbleup.demo.application;

import com.ronkadosh.bubbleup.demo.model.DemoSession;
import com.ronkadosh.bubbleup.demo.persistence.DemoSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Deletes one demo world. The demo DB is disposable and prod never runs this code,
 * so — following the {@code DemoSeeder}/{@code LoadTestSeeder} boundary-exception
 * precedent — this purges with FK-ordered native SQL scoped by the world's
 * University id (every demo row hangs off it). Lives in its own bean so the
 * {@code @Transactional} proxy is honored whether called from the scheduled sweep
 * or the eager "end demo" path (a same-bean self-call would bypass it).
 *
 * <p>Shared asset blobs ({@code DemoAssetRegistry}) are intentionally NOT deleted —
 * per-session rows only reference them.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoWorldPurger {

    private final DemoSessionRepository sessions;

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void purgeWorld(DemoSession session) {
        purge(session.getUniversityId());
        sessions.delete(session);
    }

    private void purge(UUID universityId) {
        String users = "(SELECT id FROM users WHERE university_id = :uni)";
        String courses = "(SELECT id FROM courses WHERE university_id = :uni)";
        String offerings = "(SELECT id FROM course_offerings WHERE course_id IN " + courses + ")";
        String groups = "(SELECT id FROM study_groups WHERE offering_id IN " + offerings + ")";
        String rooms = "(SELECT id FROM chat_rooms WHERE group_id IN " + groups + ")";
        String expertProfiles = "(SELECT id FROM expert_profiles WHERE user_id IN " + users + ")";
        String userProfiles = "(SELECT id FROM user_profiles WHERE user_id IN " + users + ")";
        String onbStates = "(SELECT id FROM onboarding_state WHERE user_id IN " + users + ")";

        // Children → parents. Each group/room-scoped delete runs BEFORE the parent it
        // resolves through is removed (rooms before chat_rooms; everything group-scoped
        // before study_groups; offerings after study_groups; university last).
        List<String> statements = List.of(
                "DELETE FROM message_read_cursors WHERE room_id IN " + rooms,
                "DELETE FROM chat_messages WHERE room_id IN " + rooms,
                "DELETE FROM chat_rooms WHERE group_id IN " + groups,
                "DELETE FROM group_files WHERE group_id IN " + groups,
                "DELETE FROM group_folders WHERE group_id IN " + groups,
                "DELETE FROM group_members WHERE group_id IN " + groups,
                "DELETE FROM group_profiles WHERE group_id IN " + groups,
                "DELETE FROM expert_session_group_enrollments WHERE group_id IN " + groups,
                "DELETE FROM calendar_events WHERE owner_id IN " + groups + " OR owner_id IN " + users,
                "DELETE FROM study_groups WHERE offering_id IN " + offerings,
                "DELETE FROM expert_sessions WHERE expert_user_id IN " + users,
                "DELETE FROM expert_profile_tags WHERE expert_profile_id IN " + expertProfiles,
                "DELETE FROM expert_profiles WHERE user_id IN " + users,
                "DELETE FROM enrollments WHERE user_id IN " + users,
                "DELETE FROM quiz_responses WHERE user_id IN " + users,
                "DELETE FROM user_match_cache WHERE user_id IN " + users,
                "DELETE FROM match_feedback WHERE user_id IN " + users,
                "DELETE FROM user_profile_behavior_counts WHERE user_profile_id IN " + userProfiles,
                "DELETE FROM user_profiles WHERE user_id IN " + users,
                "DELETE FROM onboarding_acknowledged WHERE onboarding_state_id IN " + onbStates,
                "DELETE FROM onboarding_state WHERE user_id IN " + users,
                "DELETE FROM refresh_tokens WHERE user_id IN " + users,
                "DELETE FROM course_offerings WHERE course_id IN " + courses,
                "DELETE FROM course_departments WHERE course_id IN " + courses,
                "DELETE FROM courses WHERE university_id = :uni",
                "DELETE FROM terms WHERE university_id = :uni",
                "DELETE FROM departments WHERE university_id = :uni",
                "DELETE FROM users WHERE university_id = :uni",
                "DELETE FROM universities WHERE id = :uni");

        for (String sql : statements) {
            em.createNativeQuery(sql).setParameter("uni", universityId).executeUpdate();
        }
    }
}
