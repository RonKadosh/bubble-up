package com.ronkadosh.bubbleup.bootstrap.seed;

import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.calendar.api.dto.CreateCalendarEventRequest;
import com.ronkadosh.bubbleup.calendar.application.CalendarCommandService;
import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.model.CourseDepartment;
import com.ronkadosh.bubbleup.catalog.model.CourseOffering;
import com.ronkadosh.bubbleup.catalog.model.Department;
import com.ronkadosh.bubbleup.catalog.model.Term;
import com.ronkadosh.bubbleup.catalog.model.TermKind;
import com.ronkadosh.bubbleup.catalog.model.University;
import com.ronkadosh.bubbleup.catalog.persistence.CourseDepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseOfferingRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.catalog.persistence.DepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.TermRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.internal.dto.ChatRoomSummary;
import com.ronkadosh.bubbleup.chat.model.ChatMessage;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.chat.persistence.ChatMessageRepository;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.file.StoredFile;
import com.ronkadosh.bubbleup.enrollment.application.EnrollmentCommandService;
import com.ronkadosh.bubbleup.expert.api.dto.ApplyAsExpertRequest;
import com.ronkadosh.bubbleup.expert.api.dto.CreateExpertSessionRequest;
import com.ronkadosh.bubbleup.expert.application.ExpertProfileService;
import com.ronkadosh.bubbleup.expert.application.ExpertSessionCommandService;
import com.ronkadosh.bubbleup.expert.internal.ExpertAdminInternalService;
import com.ronkadosh.bubbleup.groups.api.dto.CreateGroupRequest;
import com.ronkadosh.bubbleup.groups.application.GroupCommandService;
import com.ronkadosh.bubbleup.groups.model.GroupFile;
import com.ronkadosh.bubbleup.groups.model.GroupFolder;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.persistence.GroupFileRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupFolderRepository;
import com.ronkadosh.bubbleup.matching.application.MatchingCommandService;
import com.ronkadosh.bubbleup.onboarding.model.OnboardingState;
import com.ronkadosh.bubbleup.onboarding.persistence.OnboardingStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds one complete, isolated demo world per "Start demo" click (see
 * Demo-world.md). Lives in {@code bootstrap/seed} — the sanctioned
 * module-boundary-exception zone (same as {@code DemoSeeder}/{@code LoadTestSeeder})
 * — so it may inject repositories + command services directly and write backdated
 * rows the public APIs can't.
 *
 * <p>Each world gets its own {@link University} ({@code shortCode = "BUU"+token});
 * every other globally-unique field (user emails) carries the same token. All the
 * world's data therefore hangs off that one university, which is what
 * {@code DemoCleanupService} scopes its purge by.
 *
 * <p>Only registers when {@code app.demo.enabled}.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoWorldSeeder {

    private static final String DEMO_PASSWORD = "Demo!Pass1";
    private static final int CHARACTER_ANSWER_COUNT = 7;

    private final TimeProvider timeProvider;
    private final UniversityRepository universityRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final CourseDepartmentRepository courseDepartmentRepository;
    private final TermRepository termRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EnrollmentCommandService enrollmentCommandService;
    private final GroupCommandService groupCommandService;
    private final ChatInternalService chatInternalService;
    private final ChatMessageRepository chatMessageRepository;
    private final GroupFolderRepository groupFolderRepository;
    private final GroupFileRepository groupFileRepository;
    private final CalendarCommandService calendarCommandService;
    private final MatchingCommandService matchingCommandService;
    private final ExpertProfileService expertProfileService;
    private final ExpertAdminInternalService expertAdminInternalService;
    private final ExpertSessionCommandService expertSessionCommandService;
    private final OnboardingStateRepository onboardingStateRepository;
    private final DemoAssetRegistry assets;

    // ── Static world definition (Demo-world.md §4–§9) ──────────────────────────

    /** role index: 0 LEADER · 1 PLANNER · 2 EXPERT · 3 CREATIVE · 4 COMMUNICATOR · 5 TEAM_PLAYER · 6 CHALLENGER */
    private record Persona(String slug, String name, int role) {}
    private record CourseDef(String code, String name, double credits, String dept, String description) {}
    private record BubbleDef(String slug, String name, String desc, int max, String course, List<String> members) {}
    private record ExpertDef(String slug, String name, boolean verified, String dept, String headline, String bio, Set<String> tags) {}
    private record SessionDef(String host, String title, String desc, long startMin, long durMin, int capacity, List<String> bubbles) {}

    private static final List<Persona> SCIENCE = List.of(
            new Persona("ava-cohen", "Ava Cohen", 1), new Persona("noah-levi", "Noah Levi", 2),
            new Persona("mia-katz", "Mia Katz", 4), new Persona("liam-shapiro", "Liam Shapiro", 0),
            new Persona("emma-roth", "Emma Roth", 3), new Persona("ethan-mizrahi", "Ethan Mizrahi", 6),
            new Persona("sara-friedman", "Sara Friedman", 5), new Persona("daniel-peretz", "Daniel Peretz", 2),
            new Persona("tamar-ben-david", "Tamar Ben-David", 1), new Persona("omar-haddad", "Omar Haddad", 4),
            new Persona("lily-nguyen", "Lily Nguyen", 3), new Persona("jonah-stern", "Jonah Stern", 0),
            new Persona("maya-azoulay", "Maya Azoulay", 5), new Persona("adam-klein", "Adam Klein", 6),
            new Persona("nicole-bar", "Nicole Bar", 1), new Persona("ryan-oliveira", "Ryan Oliveira", 2),
            new Persona("hana-suzuki", "Hana Suzuki", 4), new Persona("leo-martin", "Leo Martin", 3),
            new Persona("priya-sharma", "Priya Sharma", 0), new Persona("yossi-gabay", "Yossi Gabay", 5),
            new Persona("zoe-kim", "Zoe Kim", 6), new Persona("marco-rossi", "Marco Rossi", 2),
            new Persona("dana-vardi", "Dana Vardi", 1), new Persona("felix-braun", "Felix Braun", 4));

    private static final List<Persona> SOCIOLOGY = List.of(
            new Persona("lena-fischer", "Lena Fischer", 4), new Persona("amir-cohen", "Amir Cohen", 0),
            new Persona("grace-owens", "Grace Owens", 5), new Persona("tariq-aziz", "Tariq Aziz", 2),
            new Persona("yael-sade", "Yael Sade", 3), new Persona("ben-harel", "Ben Harel", 1),
            new Persona("sofia-romano", "Sofia Romano", 4), new Persona("malik-johnson", "Malik Johnson", 6),
            new Persona("ruth-adler", "Ruth Adler", 5), new Persona("ivan-petrov", "Ivan Petrov", 2),
            new Persona("nina-lopez", "Nina Lopez", 3), new Persona("caleb-wright", "Caleb Wright", 0),
            new Persona("aisha-rahman", "Aisha Rahman", 1), new Persona("theo-dubois", "Theo Dubois", 4),
            new Persona("hila-regev", "Hila Regev", 5), new Persona("samuel-okafor", "Samuel Okafor", 6),
            new Persona("clara-meyer", "Clara Meyer", 3), new Persona("josh-green", "Josh Green", 0),
            new Persona("fatima-ali", "Fatima Ali", 1), new Persona("david-stein", "David Stein", 2),
            new Persona("olivia-park", "Olivia Park", 4), new Persona("rami-khoury", "Rami Khoury", 5),
            new Persona("esther-weiss", "Esther Weiss", 6), new Persona("lucas-silva", "Lucas Silva", 3));

    private static final List<CourseDef> COURSES = List.of(
            new CourseDef("CS101", "Introduction to Computer Science", 4.0, "SCI", "Foundations of programming, algorithms, and computational thinking."),
            new CourseDef("MAT110", "Calculus I", 5.0, "SCI", "Limits, derivatives, integrals, and their applications."),
            new CourseDef("PHY120", "General Physics", 4.0, "SCI", "Classical mechanics, energy, and motion."),
            new CourseDef("CHM130", "Organic Chemistry", 4.0, "SCI", "Structure, mechanisms, and reactions of carbon compounds."),
            new CourseDef("SOC101", "Introduction to Sociology", 3.0, "SOC", "Society, institutions, and the sociological imagination."),
            new CourseDef("PSY110", "Social Psychology", 3.0, "SOC", "How people think, feel, and behave in social contexts."),
            new CourseDef("RES120", "Research Methods in Social Science", 4.0, "SOC", "Survey design, qualitative methods, and statistics."),
            new CourseDef("ANT130", "Cultural Anthropology", 3.0, "SOC", "Culture, ritual, kinship, and ethnographic fieldwork."));

    /** Bubbles per Demo-world §6. Member list has the owner first. */
    private static final List<BubbleDef> BUBBLES = List.of(
            new BubbleDef("cs101-night-owls", "CS101 Night Owls", "Late-night coding and problem-set grinding.", 8, "CS101", List.of("ava-cohen", "noah-levi", "mia-katz", "liam-shapiro", "emma-roth")),
            new BubbleDef("cs101-recursion-club", "Recursion Club", "We solve it by solving it again.", 6, "CS101", List.of("noah-levi", "ethan-mizrahi", "sara-friedman")),
            new BubbleDef("cs101-debuggers", "The Debuggers", "Stuck on a bug? Bring it here.", 6, "CS101", List.of("mia-katz", "daniel-peretz", "tamar-ben-david", "omar-haddad")),
            new BubbleDef("cs101-hello-world", "Hello, World!", "Absolute beginners welcome.", 10, "CS101", List.of("liam-shapiro", "lily-nguyen", "jonah-stern", "maya-azoulay", "adam-klein", "nicole-bar", "ryan-oliveira")),
            new BubbleDef("mat110-limits-crew", "Limits & Beyond", "Conquering limits, derivatives, integrals.", 6, "MAT110", List.of("tamar-ben-david", "ava-cohen")),
            new BubbleDef("mat110-derivative-dojo", "Derivative Dojo", "Daily practice — black belt by finals.", 8, "MAT110", List.of("emma-roth", "hana-suzuki", "leo-martin", "priya-sharma", "yossi-gabay")),
            new BubbleDef("mat110-integral-squad", "Integral Squad", "Area under the curve, area under pressure.", 6, "MAT110", List.of("ryan-oliveira", "zoe-kim", "marco-rossi")),
            new BubbleDef("mat110-epsilon-delta", "Epsilon-Delta Gang", "For the rigor lovers.", 5, "MAT110", List.of("daniel-peretz", "dana-vardi", "felix-braun", "nicole-bar")),
            new BubbleDef("phy120-newtons-crew", "Newton's Crew", "Forces, motion, and free pizza.", 7, "PHY120", List.of("jonah-stern", "liam-shapiro", "maya-azoulay", "adam-klein", "ethan-mizrahi", "sara-friedman")),
            new BubbleDef("phy120-momentum", "Momentum", "Keep the study streak going.", 6, "PHY120", List.of("priya-sharma", "leo-martin")),
            new BubbleDef("phy120-quantum-leap", "Quantum Leap", "From classical to spooky.", 6, "PHY120", List.of("marco-rossi", "omar-haddad", "hana-suzuki", "zoe-kim")),
            new BubbleDef("phy120-lab-partners", "Lab Partners", "Lab reports, decoded together.", 8, "PHY120", List.of("yossi-gabay", "ava-cohen", "noah-levi", "mia-katz", "dana-vardi")),
            new BubbleDef("chm130-mole-people", "The Mole People", "Stoichiometry support group.", 6, "CHM130", List.of("felix-braun", "dana-vardi", "nicole-bar")),
            new BubbleDef("chm130-benzene-buddies", "Benzene Buddies", "Mechanisms, rings, and reactions.", 7, "CHM130", List.of("emma-roth", "lily-nguyen", "leo-martin", "priya-sharma", "hana-suzuki", "adam-klein")),
            new BubbleDef("chm130-titration-nation", "Titration Nation", "Drop by drop to an A.", 5, "CHM130", List.of("zoe-kim", "marco-rossi", "ethan-mizrahi", "sara-friedman")),
            new BubbleDef("chm130-orgo-survivors", "Orgo Survivors", "We made it past midterm 1. Barely.", 6, "CHM130", List.of("tamar-ben-david", "ryan-oliveira")),
            new BubbleDef("soc101-society-now", "Society Now", "Current events through a sociological lens.", 8, "SOC101", List.of("lena-fischer", "amir-cohen", "grace-owens", "tariq-aziz", "yael-sade")),
            new BubbleDef("soc101-norm-breakers", "Norm Breakers", "Studying social norms by questioning them.", 6, "SOC101", List.of("amir-cohen", "ben-harel", "sofia-romano")),
            new BubbleDef("soc101-first-years", "First-Year Sociologists", "New to the major, learning together.", 10, "SOC101", List.of("grace-owens", "malik-johnson", "ruth-adler", "ivan-petrov", "nina-lopez", "caleb-wright", "aisha-rahman")),
            new BubbleDef("soc101-coffee-and-theory", "Coffee & Theory", "Espresso-fueled discussions of the classics.", 6, "SOC101", List.of("yael-sade", "theo-dubois")),
            new BubbleDef("psy110-mind-the-group", "Mind the Group", "Group dynamics — studied in a group.", 7, "PSY110", List.of("sofia-romano", "hila-regev", "samuel-okafor", "clara-meyer", "josh-green", "fatima-ali")),
            new BubbleDef("psy110-cognitive-crew", "Cognitive Crew", "Biases, heuristics, and us.", 6, "PSY110", List.of("caleb-wright", "david-stein", "olivia-park", "rami-khoury")),
            new BubbleDef("psy110-experiment-club", "The Experiment Club", "Designing and dissecting classic studies.", 5, "PSY110", List.of("aisha-rahman", "esther-weiss", "lucas-silva")),
            new BubbleDef("psy110-attachment-theory", "Securely Attached", "Attachment theory study circle.", 6, "PSY110", List.of("nina-lopez", "lena-fischer")),
            new BubbleDef("res120-data-diggers", "Data Diggers", "Surveys, stats, and SPSS tears.", 7, "RES120", List.of("ben-harel", "ruth-adler", "ivan-petrov", "malik-johnson", "grace-owens")),
            new BubbleDef("res120-qualitative-circle", "The Qualitative Circle", "Interviews, coding, grounded theory.", 6, "RES120", List.of("clara-meyer", "theo-dubois", "hila-regev")),
            new BubbleDef("res120-p-value-pals", "p-value Pals", "Making peace with statistics.", 8, "RES120", List.of("david-stein", "olivia-park", "rami-khoury", "esther-weiss", "lucas-silva", "samuel-okafor")),
            new BubbleDef("res120-methodology-mavens", "Methodology Mavens", "Designing bulletproof studies.", 5, "RES120", List.of("fatima-ali", "josh-green")),
            new BubbleDef("ant130-fieldwork-friends", "Fieldwork Friends", "Ethnography, notes, and stories.", 7, "ANT130", List.of("tariq-aziz", "yael-sade", "amir-cohen", "sofia-romano")),
            new BubbleDef("ant130-ritual-roundtable", "Ritual Roundtable", "Rites, myths, and meaning.", 6, "ANT130", List.of("ruth-adler", "nina-lopez", "caleb-wright", "aisha-rahman", "ivan-petrov")),
            new BubbleDef("ant130-culture-club", "Culture Club", "Cross-cultural comparison, weekly.", 6, "ANT130", List.of("samuel-okafor", "clara-meyer", "olivia-park")),
            new BubbleDef("ant130-kinship-crew", "Kinship Crew", "Mapping families and societies.", 5, "ANT130", List.of("esther-weiss", "rami-khoury")));

    private static final List<ExpertDef> EXPERTS = List.of(
            new ExpertDef("prof-hannah-gold", "Prof. Hannah Gold", true, "SCI", "Algorithms & data structures, ex-FAANG", "Twelve years teaching CS fundamentals. I make Big-O click.", Set.of("algorithms", "data-structures", "computer-science")),
            new ExpertDef("dr-omar-said", "Dr. Omar Said", true, "SCI", "Physicist — classical mechanics", "From free-body diagrams to orbital motion, one clear step at a time.", Set.of("physics", "mechanics", "calculus")),
            new ExpertDef("dr-rachel-stone", "Dr. Rachel Stone", true, "SCI", "Organic chemistry tutor", "Reaction mechanisms without the memorization panic.", Set.of("chemistry", "organic-chemistry", "lab-safety")),
            new ExpertDef("prof-daniel-roth", "Prof. Daniel Roth", true, "SOC", "Sociologist — social theory", "Durkheim to Bourdieu, and why it matters today.", Set.of("sociology", "social-theory", "research")),
            new ExpertDef("dr-lily-chen", "Dr. Lily Chen", false, "SOC", "Quantitative research methods", "Survey design and statistics for social scientists.", Set.of("statistics", "research-methods", "data-analysis")));

    private static final List<SessionDef> SESSIONS = List.of(
            new SessionDef("prof-hannah-gold", "CS Algorithms Exam Crunch", "Past-paper walkthrough + your hardest questions before the CS final.", 15, 120, 4, List.of("cs101-night-owls", "cs101-debuggers")),
            new SessionDef("dr-omar-said", "Mechanics Problem-Solving Clinic", "We work through the trickiest force and energy problems together.", Duration.ofDays(2).toMinutes(), 90, 6, List.of("phy120-newtons-crew", "phy120-lab-partners")),
            new SessionDef("prof-daniel-roth", "Sociological Theory: Office Hours", "Bring a theorist you're stuck on; we'll untangle it live.", Duration.ofDays(7).toMinutes(), 60, 8, List.of("soc101-society-now", "ant130-fieldwork-friends")));

    /** Generic chat snippets (Demo-world §9); {course} is interpolated. */
    private static final String[] CHAT_SNIPPETS = {
            "Hey everyone 👋 glad we got this Bubble going!",
            "When are we meeting this week?",
            "Thursday evening works for me 🙌",
            "I dropped my {course} notes in Files 📄",
            "Anyone get Q3 on the problem set? 😅",
            "Same, totally stuck on that one — let's go over it together",
            "I added a study session to the calendar, check it out 📅",
            "Thanks, that really helped 🙏",
            "I'll bring snacks 🍪",
            "Good luck on the {course} midterm everyone! 🍀"};

    private static final List<String> CS101_CONVO = List.of(
            "Hey Night Owls 🦉 welcome to the Bubble!",
            "Finally a place to grind CS101 together 😄",
            "I uploaded the Week 1 + Week 2 notes to Files 📄",
            "Legend 🙏 anyone else lost on the recursion exercise?",
            "Let's cover it at the study session — added it to the calendar 📅",
            "I'll be there. Bringing snacks 🍪");

    // ── Seed entry point ───────────────────────────────────────────────────────

    @Transactional
    public DemoWorldHandle seed(String token) {
        Instant t0 = timeProvider.now();
        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);

        // 1. University + term + departments + courses + offerings.
        UUID universityId = universityRepository.save(University.builder()
                .shortCode("BUU" + token).name("Bubble.up University").country("US").build()).getId();

        LocalDate today = LocalDate.ofInstant(t0, ZoneOffset.UTC);
        termRepository.save(Term.builder()
                .universityId(universityId).code("DEMO").name("Current Semester").kind(TermKind.SPRING)
                .academicYear(today.getYear()).startsOn(today.minusDays(45)).endsOn(today.plusDays(75)).build());
        Term term = termRepository.findByUniversityIdAndCode(universityId, "DEMO").orElseThrow();

        Map<String, UUID> deptIds = new HashMap<>();
        deptIds.put("SCI", departmentRepository.save(Department.builder()
                .universityId(universityId).shortCode("SCI").name("Science Department").build()).getId());
        deptIds.put("SOC", departmentRepository.save(Department.builder()
                .universityId(universityId).shortCode("SOC").name("Sociology Department").build()).getId());

        Map<String, UUID> courseIds = new HashMap<>();
        for (CourseDef c : COURSES) {
            UUID courseId = courseRepository.save(Course.builder()
                    .universityId(universityId).code(c.code()).name(c.name())
                    .creditPoints(BigDecimal.valueOf(c.credits())).description(c.description()).build()).getId();
            courseIds.put(c.code(), courseId);
            courseDepartmentRepository.save(CourseDepartment.builder()
                    .courseId(courseId).departmentId(deptIds.get(c.dept())).primary(true).build());
            courseOfferingRepository.save(CourseOffering.builder()
                    .courseId(courseId).termId(term.getId()).build());
        }

        // 2. Persona + expert + admin users.
        Map<String, UUID> userIds = new HashMap<>();
        for (Persona p : SCIENCE) userIds.put(p.slug(), createUser(p.slug(), p.name(), token, passwordHash, UserRole.STUDENT, universityId, deptIds.get("SCI"), "people"));
        for (Persona p : SOCIOLOGY) userIds.put(p.slug(), createUser(p.slug(), p.name(), token, passwordHash, UserRole.STUDENT, universityId, deptIds.get("SOC"), "people"));
        UUID adminId = createUser("admin", "Demo Admin", token, passwordHash, UserRole.ADMIN, universityId, deptIds.get("SCI"), null);

        // 3. Experts: user + profile, then force VERIFIED / PENDING (config-independent).
        for (ExpertDef e : EXPERTS) {
            UUID uid = createUser(e.slug(), e.name(), token, passwordHash, UserRole.STUDENT, universityId, deptIds.get(e.dept()), "experts");
            userIds.put(e.slug(), uid);
            expertProfileService.applyAsExpert(uid, new ApplyAsExpertRequest(e.headline(), e.bio(), e.tags()));
            if (e.verified()) expertAdminInternalService.verify(uid, adminId);
            else expertAdminInternalService.revoke(uid);
        }

        // 4. Enroll every owner+member in their bubble's course (membership is enrollment-gated).
        Set<String> enrolled = new HashSet<>();
        for (BubbleDef b : BUBBLES) {
            for (String slug : b.members()) enrollOnce(enrolled, userIds.get(slug), courseIds.get(b.course()));
        }

        // 5. Bubbles: owner creates, the rest join. (General chat room auto-created.)
        Map<String, UUID> groupIds = new HashMap<>();
        for (BubbleDef b : BUBBLES) {
            UUID ownerId = userIds.get(b.members().get(0));
            UUID groupId = groupCommandService.createGroup(new CreateGroupRequest(
                    b.name(), b.desc(), GroupVisibility.PUBLIC, b.max(), null, courseIds.get(b.course())), ownerId).id();
            groupIds.put(b.slug(), groupId);
            for (int i = 1; i < b.members().size(); i++) {
                groupCommandService.joinGroup(groupId, userIds.get(b.members().get(i)));
            }
        }

        // 6. Character profiles → gives each bubble a distinct matching vector.
        for (Persona p : SCIENCE) matchingCommandService.seedCharacterAnswers(userIds.get(p.slug()), p.role(), CHARACTER_ANSWER_COUNT);
        for (Persona p : SOCIOLOGY) matchingCommandService.seedCharacterAnswers(userIds.get(p.slug()), p.role(), CHARACTER_ANSWER_COUNT);

        // 7. Calendar, files, chat history per bubble.
        int idx = 0;
        for (BubbleDef b : BUBBLES) {
            UUID groupId = groupIds.get(b.slug());
            UUID ownerId = userIds.get(b.members().get(0));
            String courseName = courseName(b.course());
            seedCalendar(groupId, ownerId, courseName, idx, t0);
            seedFiles(groupId, ownerId, b.members().size(), t0);
            seedChat(groupId, b, userIds, courseName, idx, t0);
            idx++;
        }

        // 8. Expert sessions + group enrollments.
        for (SessionDef s : SESSIONS) {
            UUID hostId = userIds.get(s.host());
            Instant startsAt = t0.plus(Duration.ofMinutes(s.startMin()));
            UUID sessionId = expertSessionCommandService.createSession(hostId,
                    new CreateExpertSessionRequest(s.title(), s.desc(), startsAt, startsAt.plus(Duration.ofMinutes(s.durMin())), s.capacity())).id();
            for (String bubbleSlug : s.bubbles()) {
                UUID groupId = groupIds.get(bubbleSlug);
                UUID groupOwnerId = userIds.get(ownerSlugOf(bubbleSlug));
                expertSessionCommandService.enrollGroup(sessionId, groupId, groupOwnerId);
            }
        }

        // 9. The visitor "You": no matching profile (Explore stays Trending-only until the tour quiz).
        UUID guestId = createUser("you", "You", token, passwordHash, UserRole.STUDENT, universityId, deptIds.get("SCI"), null);
        enrollOnce(new HashSet<>(), guestId, courseIds.get("CS101"));
        enrollmentCommandService.enroll(guestId, courseIds.get("SOC101"));
        UUID starterGroupId = groupIds.get("cs101-night-owls");
        groupCommandService.joinGroup(starterGroupId, guestId);
        onboardingStateRepository.save(OnboardingState.builder()
                .userId(guestId).wizardLevel(6).collapsed(false).updatedAt(t0).build());

        log.info("DemoWorldSeeder: seeded world {} — university {} (32 bubbles, {} users, guest {})",
                token, universityId, userIds.size() + 1, guestId);
        return new DemoWorldHandle(universityId, guestId, starterGroupId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private UUID createUser(String slug, String name, String token, String passwordHash, UserRole role,
                            UUID universityId, UUID departmentId, String avatarDir) {
        StoredFile avatar = avatarDir == null ? null : assets.avatar(avatarDir, slug);
        User user = userRepository.save(User.builder()
                .email(slug + "@s" + token + ".demo.bubble.up")
                .passwordHash(passwordHash)
                .role(role)
                .displayName(name)
                .universityId(universityId)
                .departmentId(departmentId)
                .enrollmentYear(LocalDate.now().getYear())
                .emailVerified(true)
                .avatarFileId(avatar == null ? null : avatar.fileId())
                .avatarContentType(avatar == null ? null : avatar.contentType())
                .build());
        return user.getId();
    }

    private void enrollOnce(Set<String> done, UUID userId, UUID courseId) {
        if (done.add(userId + "|" + courseId)) {
            enrollmentCommandService.enroll(userId, courseId);
        }
    }

    private void seedCalendar(UUID groupId, UUID ownerId, String courseName, int idx, Instant t0) {
        // MEETING (not STUDY_SESSION) so no live video room is auto-created at seed time.
        createEvent(groupId, ownerId, CalendarEventType.MEETING, "Weekly study session",
                t0.plus(Duration.ofDays(2)).plus(Duration.ofHours(idx % 4)), 90);
        createEvent(groupId, ownerId, CalendarEventType.DEADLINE, courseName + " problem set due",
                t0.plus(Duration.ofDays(5)), 30);
        if (idx % 2 == 0) {
            createEvent(groupId, ownerId, CalendarEventType.EXAM, courseName + " midterm",
                    t0.plus(Duration.ofDays(21)), 120);
        }
    }

    private void createEvent(UUID groupId, UUID ownerId, CalendarEventType type, String title, Instant startsAt, int durMin) {
        calendarCommandService.create(new CreateCalendarEventRequest(
                CalendarOwnerType.GROUP, groupId, type, title, startsAt, startsAt.plus(Duration.ofMinutes(durMin))), ownerId);
    }

    private void seedFiles(UUID groupId, UUID ownerId, int memberCount, Instant t0) {
        addFile(groupId, ownerId, null, "Syllabus.pdf", "syllabus.pdf", t0);
        addFile(groupId, ownerId, null, "Welcome.txt", "welcome.txt", t0);
        UUID lectureNotes = addFolder(groupId, ownerId, "Lecture Notes", t0);
        addFile(groupId, ownerId, lectureNotes, "Week-1-Notes.pdf", "lecture-notes-1.pdf", t0);
        addFile(groupId, ownerId, lectureNotes, "Week-2-Notes.pdf", "lecture-notes-2.pdf", t0);
        UUID pastExams = addFolder(groupId, ownerId, "Past Exams", t0);
        addFile(groupId, ownerId, pastExams, "2025-Midterm.pdf", "past-exam-2025.pdf", t0);
        if (memberCount >= 4) {
            UUID resources = addFolder(groupId, ownerId, "Resources", t0);
            addFile(groupId, ownerId, resources, "Cheat-Sheet.pdf", "cheat-sheet.pdf", t0);
            addFile(groupId, ownerId, resources, "Diagram.png", "diagram.png", t0);
        }
    }

    private UUID addFolder(UUID groupId, UUID ownerId, String name, Instant t0) {
        return groupFolderRepository.save(GroupFolder.builder()
                .groupId(groupId).parentId(null).name(name).createdById(ownerId)
                .createdAt(t0.minus(Duration.ofDays(8))).build()).getId();
    }

    private void addFile(UUID groupId, UUID ownerId, UUID folderId, String displayName, String asset, Instant t0) {
        StoredFile blob = assets.file(asset);
        if (blob == null) return; // asset not bundled — skip gracefully
        groupFileRepository.save(GroupFile.builder()
                .groupId(groupId).uploaderId(ownerId).folderId(folderId)
                .fileId(blob.fileId()).originalName(displayName).contentType(blob.contentType())
                .sizeBytes(blob.sizeBytes()).uploadedAt(t0.minus(Duration.ofDays(3))).build());
    }

    private void seedChat(UUID groupId, BubbleDef b, Map<String, UUID> userIds, String courseName, int idx, Instant t0) {
        List<ChatRoomSummary> rooms = chatInternalService.getRoomsForGroup(groupId);
        if (rooms.isEmpty()) return;
        UUID roomId = rooms.get(0).id();

        // Push the auto-posted "X joined" system messages back before the chat history,
        // so the room reads: people joined ~8 days ago, then started chatting. (The
        // guest's own join happens later, at T0, so "You joined" stays newest.)
        chatMessageRepository.backdateRoomMessages(roomId, t0.minus(Duration.ofDays(8)));

        List<String> lines = new ArrayList<>();
        if (b.slug().equals("cs101-night-owls")) {
            lines.addAll(CS101_CONVO);
        } else {
            int count = 4 + (idx % 3); // 4–6
            for (int i = 0; i < count; i++) {
                lines.add(CHAT_SNIPPETS[(idx + i) % CHAT_SNIPPETS.length].replace("{course}", courseName));
            }
        }

        List<UUID> senders = new ArrayList<>();
        for (String slug : b.members()) senders.add(userIds.get(slug));

        // Stagger sentAt ascending across T0−7d … T0−2h.
        long windowSec = Duration.ofDays(7).minus(Duration.ofHours(2)).getSeconds();
        long step = windowSec / Math.max(1, lines.size());
        Instant base = t0.minus(Duration.ofDays(7));
        for (int i = 0; i < lines.size(); i++) {
            chatMessageRepository.save(ChatMessage.builder()
                    .roomId(roomId)
                    .senderId(senders.get(i % senders.size()))
                    .content(lines.get(i))
                    .messageType(ChatMessageType.TEXT)
                    .sentAt(base.plusSeconds(step * i))
                    .build());
        }
    }

    private static String courseName(String code) {
        return COURSES.stream().filter(c -> c.code().equals(code)).findFirst().map(CourseDef::name).orElse(code);
    }

    private static String ownerSlugOf(String bubbleSlug) {
        return BUBBLES.stream().filter(b -> b.slug().equals(bubbleSlug)).findFirst()
                .map(b -> b.members().get(0)).orElseThrow();
    }
}
