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

    /** role index: 0 LEADER · 1 PLANNER · 2 EXPERT · 3 CREATIVE · 4 COMMUNICATOR · 5 TEAM_PLAYER · 6 CHALLENGER.
     *  Each user-visible field carries an English + Hebrew twin; the seed picks by the
     *  visitor's language (Accept-Language at /demo/start, see {@link #loc}). Structural
     *  fields (slug, role, course code, member lists) are language-independent. */
    private record Persona(String slug, String name, String nameHe, int role) {}
    private record CourseDef(String code, String name, String nameHe, double credits, String dept, String description, String descriptionHe) {}
    /** {@code role} = the bubble's theme role index; its members are seeded toward it so the
     *  group's role vector has a sharp peak (varied match %s). */
    private record BubbleDef(String slug, String name, String nameHe, String desc, String descHe, int max, String course, int role, List<String> members) {}
    private record ExpertDef(String slug, String name, String nameHe, boolean verified, String dept, String headline, String headlineHe, String bio, String bioHe, Set<String> tags) {}
    private record SessionDef(String host, String title, String titleHe, String desc, String descHe, long startMin, long durMin, int capacity, List<String> bubbles) {}

    private static final List<Persona> SCIENCE = List.of(
            new Persona("ava-cohen", "Ava Cohen", "אווה כהן", 1), new Persona("noah-levi", "Noah Levi", "נואה לוי", 2),
            new Persona("mia-katz", "Mia Katz", "מיה כץ", 4), new Persona("liam-shapiro", "Liam Shapiro", "ליאם שפירא", 0),
            new Persona("emma-roth", "Emma Roth", "אמה רוט", 3), new Persona("ethan-mizrahi", "Ethan Mizrahi", "איתן מזרחי", 6),
            new Persona("sara-friedman", "Sara Friedman", "שרה פרידמן", 5), new Persona("daniel-peretz", "Daniel Peretz", "דניאל פרץ", 2),
            new Persona("tamar-ben-david", "Tamar Ben-David", "תמר בן-דוד", 1), new Persona("omar-haddad", "Omar Haddad", "עומר חדאד", 4),
            new Persona("lily-nguyen", "Lily Nguyen", "לילי נגוין", 3), new Persona("jonah-stern", "Jonah Stern", "יונה שטרן", 0),
            new Persona("maya-azoulay", "Maya Azoulay", "מאיה אזולאי", 5), new Persona("adam-klein", "Adam Klein", "אדם קליין", 6),
            new Persona("nicole-bar", "Nicole Bar", "ניקול בר", 1), new Persona("ryan-oliveira", "Ryan Oliveira", "ראיין אוליביירה", 2),
            new Persona("hana-suzuki", "Hana Suzuki", "האנה סוזוקי", 4), new Persona("leo-martin", "Leo Martin", "ליאו מרטין", 3),
            new Persona("priya-sharma", "Priya Sharma", "פריה שארמה", 0), new Persona("yossi-gabay", "Yossi Gabay", "יוסי גבאי", 5),
            new Persona("zoe-kim", "Zoe Kim", "זואי קים", 6), new Persona("marco-rossi", "Marco Rossi", "מרקו רוסי", 2),
            new Persona("dana-vardi", "Dana Vardi", "דנה ורדי", 1), new Persona("felix-braun", "Felix Braun", "פליקס בראון", 4));

    private static final List<Persona> SOCIOLOGY = List.of(
            new Persona("lena-fischer", "Lena Fischer", "לנה פישר", 4), new Persona("amir-cohen", "Amir Cohen", "אמיר כהן", 0),
            new Persona("grace-owens", "Grace Owens", "גרייס אוונס", 5), new Persona("tariq-aziz", "Tariq Aziz", "טארק עזיז", 2),
            new Persona("yael-sade", "Yael Sade", "יעל שדה", 3), new Persona("ben-harel", "Ben Harel", "בן הראל", 1),
            new Persona("sofia-romano", "Sofia Romano", "סופיה רומנו", 4), new Persona("malik-johnson", "Malik Johnson", "מאליק ג'ונסון", 6),
            new Persona("ruth-adler", "Ruth Adler", "רות אדלר", 5), new Persona("ivan-petrov", "Ivan Petrov", "איוואן פטרוב", 2),
            new Persona("nina-lopez", "Nina Lopez", "נינה לופז", 3), new Persona("caleb-wright", "Caleb Wright", "כיילב רייט", 0),
            new Persona("aisha-rahman", "Aisha Rahman", "עאישה רחמן", 1), new Persona("theo-dubois", "Theo Dubois", "תיאו דובואה", 4),
            new Persona("hila-regev", "Hila Regev", "הילה רגב", 5), new Persona("samuel-okafor", "Samuel Okafor", "סמואל אוקפור", 6),
            new Persona("clara-meyer", "Clara Meyer", "קלרה מאייר", 3), new Persona("josh-green", "Josh Green", "ג'וש גרין", 0),
            new Persona("fatima-ali", "Fatima Ali", "פאטמה עלי", 1), new Persona("david-stein", "David Stein", "דיוויד שטיין", 2),
            new Persona("olivia-park", "Olivia Park", "אוליביה פארק", 4), new Persona("rami-khoury", "Rami Khoury", "רמי חורי", 5),
            new Persona("esther-weiss", "Esther Weiss", "אסתר וייס", 6), new Persona("lucas-silva", "Lucas Silva", "לוקאס סילבה", 3));

    private static final List<CourseDef> COURSES = List.of(
            new CourseDef("CS101", "Introduction to Computer Science", "מבוא למדעי המחשב", 4.0, "SCI", "Foundations of programming, algorithms, and computational thinking.", "יסודות התכנות, אלגוריתמים וחשיבה חישובית."),
            new CourseDef("MAT110", "Calculus I", "חשבון אינפיניטסימלי 1", 5.0, "SCI", "Limits, derivatives, integrals, and their applications.", "גבולות, נגזרות, אינטגרלים והשימושים שלהם."),
            new CourseDef("PHY120", "General Physics", "פיזיקה כללית", 4.0, "SCI", "Classical mechanics, energy, and motion.", "מכניקה קלאסית, אנרגיה ותנועה."),
            new CourseDef("CHM130", "Organic Chemistry", "כימיה אורגנית", 4.0, "SCI", "Structure, mechanisms, and reactions of carbon compounds.", "מבנה, מנגנונים ותגובות של תרכובות פחמן."),
            new CourseDef("SOC101", "Introduction to Sociology", "מבוא לסוציולוגיה", 3.0, "SOC", "Society, institutions, and the sociological imagination.", "חברה, מוסדות והדמיון הסוציולוגי."),
            new CourseDef("PSY110", "Social Psychology", "פסיכולוגיה חברתית", 3.0, "SOC", "How people think, feel, and behave in social contexts.", "כיצד אנשים חושבים, מרגישים ומתנהגים בהקשרים חברתיים."),
            new CourseDef("RES120", "Research Methods in Social Science", "שיטות מחקר במדעי החברה", 4.0, "SOC", "Survey design, qualitative methods, and statistics.", "תכנון סקרים, שיטות איכותניות וסטטיסטיקה."),
            new CourseDef("ANT130", "Cultural Anthropology", "אנתרופולוגיה תרבותית", 3.0, "SOC", "Culture, ritual, kinship, and ethnographic fieldwork.", "תרבות, פולחן, קרבה ועבודת שדה אתנוגרפית."));

    /** Bubbles per Demo-world §6. Member list has the owner first. The {@code role} arg
     *  is the bubble's theme (0 LEADER · 1 PLANNER · 2 EXPERT · 3 CREATIVE · 4 COMMUNICATOR
     *  · 5 TEAM_PLAYER · 6 CHALLENGER); members are seeded toward it, and themes vary within
     *  each course so a guest sees a spread of complementarity match %s. */
    private static final List<BubbleDef> BUBBLES = List.of(
            new BubbleDef("cs101-night-owls", "CS101 Night Owls", "ינשופי הלילה של CS101", "Late-night coding and problem-set grinding.", "קוד עד מאוחר בלילה וטחינת תרגילים.", 8, "CS101", 5, List.of("ava-cohen", "noah-levi", "mia-katz", "liam-shapiro", "emma-roth")),
            new BubbleDef("cs101-recursion-club", "Recursion Club", "מועדון הרקורסיה", "We solve it by solving it again.", "פותרים את זה על ידי פתרון חוזר.", 6, "CS101", 6, List.of("noah-levi", "ethan-mizrahi", "sara-friedman")),
            new BubbleDef("cs101-debuggers", "The Debuggers", "הדבאגרים", "Stuck on a bug? Bring it here.", "תקועים על באג? תביאו אותו לכאן.", 6, "CS101", 2, List.of("mia-katz", "daniel-peretz", "tamar-ben-david", "omar-haddad")),
            new BubbleDef("cs101-hello-world", "Hello, World!", "שלום עולם!", "Absolute beginners welcome.", "מתחילים מוחלטים מוזמנים.", 10, "CS101", 4, List.of("liam-shapiro", "lily-nguyen", "jonah-stern", "maya-azoulay", "adam-klein", "nicole-bar", "ryan-oliveira")),
            new BubbleDef("mat110-limits-crew", "Limits & Beyond", "גבולות ומעבר", "Conquering limits, derivatives, integrals.", "כובשים גבולות, נגזרות ואינטגרלים.", 6, "MAT110", 1, List.of("tamar-ben-david", "ava-cohen")),
            new BubbleDef("mat110-derivative-dojo", "Derivative Dojo", "דוג'ו הנגזרות", "Daily practice — black belt by finals.", "תרגול יומי — חגורה שחורה עד המבחנים.", 8, "MAT110", 6, List.of("emma-roth", "hana-suzuki", "leo-martin", "priya-sharma", "yossi-gabay")),
            new BubbleDef("mat110-integral-squad", "Integral Squad", "חוליית האינטגרלים", "Area under the curve, area under pressure.", "השטח מתחת לעקומה, והלחץ מעל.", 6, "MAT110", 5, List.of("ryan-oliveira", "zoe-kim", "marco-rossi")),
            new BubbleDef("mat110-epsilon-delta", "Epsilon-Delta Gang", "חבורת אפסילון-דלתא", "For the rigor lovers.", "לאוהבי הקפדנות.", 5, "MAT110", 2, List.of("daniel-peretz", "dana-vardi", "felix-braun", "nicole-bar")),
            new BubbleDef("phy120-newtons-crew", "Newton's Crew", "הצוות של ניוטון", "Forces, motion, and free pizza.", "כוחות, תנועה ופיצה חינם.", 7, "PHY120", 0, List.of("jonah-stern", "liam-shapiro", "maya-azoulay", "adam-klein", "ethan-mizrahi", "sara-friedman")),
            new BubbleDef("phy120-momentum", "Momentum", "תנע", "Keep the study streak going.", "שומרים על רצף הלמידה.", 6, "PHY120", 1, List.of("priya-sharma", "leo-martin")),
            new BubbleDef("phy120-quantum-leap", "Quantum Leap", "קפיצה קוונטית", "From classical to spooky.", "מהקלאסי אל המוזר.", 6, "PHY120", 3, List.of("marco-rossi", "omar-haddad", "hana-suzuki", "zoe-kim")),
            new BubbleDef("phy120-lab-partners", "Lab Partners", "שותפים למעבדה", "Lab reports, decoded together.", "דוחות מעבדה, מפענחים יחד.", 8, "PHY120", 5, List.of("yossi-gabay", "ava-cohen", "noah-levi", "mia-katz", "dana-vardi")),
            new BubbleDef("chm130-mole-people", "The Mole People", "אנשי המול", "Stoichiometry support group.", "קבוצת תמיכה בסטויכיומטריה.", 6, "CHM130", 5, List.of("felix-braun", "dana-vardi", "nicole-bar")),
            new BubbleDef("chm130-benzene-buddies", "Benzene Buddies", "חברי הבנזן", "Mechanisms, rings, and reactions.", "מנגנונים, טבעות ותגובות.", 7, "CHM130", 2, List.of("emma-roth", "lily-nguyen", "leo-martin", "priya-sharma", "hana-suzuki", "adam-klein")),
            new BubbleDef("chm130-titration-nation", "Titration Nation", "אומת הטיטרציה", "Drop by drop to an A.", "טיפה אחר טיפה אל הציון המושלם.", 5, "CHM130", 1, List.of("zoe-kim", "marco-rossi", "ethan-mizrahi", "sara-friedman")),
            new BubbleDef("chm130-orgo-survivors", "Orgo Survivors", "ניצולי האורגנית", "We made it past midterm 1. Barely.", "שרדנו את מבחן האמצע הראשון. בקושי.", 6, "CHM130", 6, List.of("tamar-ben-david", "ryan-oliveira")),
            new BubbleDef("soc101-society-now", "Society Now", "החברה עכשיו", "Current events through a sociological lens.", "אקטואליה דרך עדשה סוציולוגית.", 8, "SOC101", 4, List.of("lena-fischer", "amir-cohen", "grace-owens", "tariq-aziz", "yael-sade")),
            new BubbleDef("soc101-norm-breakers", "Norm Breakers", "שוברי הנורמות", "Studying social norms by questioning them.", "לומדים נורמות חברתיות על ידי הטלת ספק בהן.", 6, "SOC101", 6, List.of("amir-cohen", "ben-harel", "sofia-romano")),
            new BubbleDef("soc101-first-years", "First-Year Sociologists", "סוציולוגים בשנה א'", "New to the major, learning together.", "חדשים בחוג, לומדים יחד.", 10, "SOC101", 5, List.of("grace-owens", "malik-johnson", "ruth-adler", "ivan-petrov", "nina-lopez", "caleb-wright", "aisha-rahman")),
            new BubbleDef("soc101-coffee-and-theory", "Coffee & Theory", "קפה ותיאוריה", "Espresso-fueled discussions of the classics.", "דיונים בקלאסיקות על בסיס אספרסו.", 6, "SOC101", 3, List.of("yael-sade", "theo-dubois")),
            new BubbleDef("psy110-mind-the-group", "Mind the Group", "שימו לב לקבוצה", "Group dynamics — studied in a group.", "דינמיקה קבוצתית — נלמדת בקבוצה.", 7, "PSY110", 0, List.of("sofia-romano", "hila-regev", "samuel-okafor", "clara-meyer", "josh-green", "fatima-ali")),
            new BubbleDef("psy110-cognitive-crew", "Cognitive Crew", "צוות הקוגניציה", "Biases, heuristics, and us.", "הטיות, היוריסטיקות ואנחנו.", 6, "PSY110", 2, List.of("caleb-wright", "david-stein", "olivia-park", "rami-khoury")),
            new BubbleDef("psy110-experiment-club", "The Experiment Club", "מועדון הניסויים", "Designing and dissecting classic studies.", "מתכננים ומנתחים מחקרים קלאסיים.", 5, "PSY110", 1, List.of("aisha-rahman", "esther-weiss", "lucas-silva")),
            new BubbleDef("psy110-attachment-theory", "Securely Attached", "מחוברים בביטחון", "Attachment theory study circle.", "מעגל לימוד בתיאוריית ההיקשרות.", 6, "PSY110", 4, List.of("nina-lopez", "lena-fischer")),
            new BubbleDef("res120-data-diggers", "Data Diggers", "חופרי הנתונים", "Surveys, stats, and SPSS tears.", "סקרים, סטטיסטיקה ודמעות SPSS.", 7, "RES120", 2, List.of("ben-harel", "ruth-adler", "ivan-petrov", "malik-johnson", "grace-owens")),
            new BubbleDef("res120-qualitative-circle", "The Qualitative Circle", "המעגל האיכותני", "Interviews, coding, grounded theory.", "ראיונות, קידוד ותיאוריה מעוגנת בשדה.", 6, "RES120", 3, List.of("clara-meyer", "theo-dubois", "hila-regev")),
            new BubbleDef("res120-p-value-pals", "p-value Pals", "חברי ה-p-value", "Making peace with statistics.", "עושים שלום עם הסטטיסטיקה.", 8, "RES120", 5, List.of("david-stein", "olivia-park", "rami-khoury", "esther-weiss", "lucas-silva", "samuel-okafor")),
            new BubbleDef("res120-methodology-mavens", "Methodology Mavens", "מומחי המתודולוגיה", "Designing bulletproof studies.", "מתכננים מחקרים חסיני אש.", 5, "RES120", 1, List.of("fatima-ali", "josh-green")),
            new BubbleDef("ant130-fieldwork-friends", "Fieldwork Friends", "חברים לעבודת שדה", "Ethnography, notes, and stories.", "אתנוגרפיה, רשימות וסיפורים.", 7, "ANT130", 4, List.of("tariq-aziz", "yael-sade", "amir-cohen", "sofia-romano")),
            new BubbleDef("ant130-ritual-roundtable", "Ritual Roundtable", "שולחן עגול לפולחן", "Rites, myths, and meaning.", "טקסים, מיתוסים ומשמעות.", 6, "ANT130", 3, List.of("ruth-adler", "nina-lopez", "caleb-wright", "aisha-rahman", "ivan-petrov")),
            new BubbleDef("ant130-culture-club", "Culture Club", "מועדון התרבות", "Cross-cultural comparison, weekly.", "השוואה בין-תרבותית, שבועית.", 6, "ANT130", 1, List.of("samuel-okafor", "clara-meyer", "olivia-park")),
            new BubbleDef("ant130-kinship-crew", "Kinship Crew", "צוות הקרבה", "Mapping families and societies.", "ממפים משפחות וחברות.", 5, "ANT130", 0, List.of("esther-weiss", "rami-khoury")));

    private static final List<ExpertDef> EXPERTS = List.of(
            new ExpertDef("prof-hannah-gold", "Prof. Hannah Gold", "פרופ' חנה גולד", true, "SCI", "Algorithms & data structures, ex-FAANG", "אלגוריתמים ומבני נתונים, לשעבר FAANG", "Twelve years teaching CS fundamentals. I make Big-O click.", "שתים-עשרה שנים מלמדת יסודות מדעי המחשב. אני גורמת ל-Big-O להתחבר.", Set.of("algorithms", "data-structures", "computer-science")),
            new ExpertDef("dr-omar-said", "Dr. Omar Said", "ד\"ר עומר סעיד", true, "SCI", "Physicist — classical mechanics", "פיזיקאי — מכניקה קלאסית", "From free-body diagrams to orbital motion, one clear step at a time.", "מדיאגרמות גוף חופשי ועד תנועה מסלולית, צעד ברור אחד בכל פעם.", Set.of("physics", "mechanics", "calculus")),
            new ExpertDef("dr-rachel-stone", "Dr. Rachel Stone", "ד\"ר רייצ'ל סטון", true, "SCI", "Organic chemistry tutor", "מורה לכימיה אורגנית", "Reaction mechanisms without the memorization panic.", "מנגנוני תגובה בלי הפאניקה של השינון.", Set.of("chemistry", "organic-chemistry", "lab-safety")),
            new ExpertDef("prof-daniel-roth", "Prof. Daniel Roth", "פרופ' דניאל רוט", true, "SOC", "Sociologist — social theory", "סוציולוג — תיאוריה חברתית", "Durkheim to Bourdieu, and why it matters today.", "מדורקהיים ועד בורדייה, ולמה זה רלוונטי היום.", Set.of("sociology", "social-theory", "research")),
            new ExpertDef("dr-lily-chen", "Dr. Lily Chen", "ד\"ר לילי צ'ן", false, "SOC", "Quantitative research methods", "שיטות מחקר כמותיות", "Survey design and statistics for social scientists.", "תכנון סקרים וסטטיסטיקה למדעני החברה.", Set.of("statistics", "research-methods", "data-analysis")));

    private static final List<SessionDef> SESSIONS = List.of(
            // The starter bubble's session is timed so its room opens ~2 min after seeding and
            // stays open for 120 min — the guest can walk straight in when they finish the tour.
            // Note the tight coupling: group enrollment CLOSES 5 min before start and the room
            // OPENS 5 min before start (same instant). So startMin must be >5 for the seed's
            // enrollGroup to succeed (it runs at ~t0), yet small so the room opens soon after.
            // startMin=7 → enroll at ~t0 (closes at t0+2m, safe), room opens at t0+2m.
            new SessionDef("prof-hannah-gold", "CS Algorithms Exam Crunch", "מרתון למבחן באלגוריתמים", "Past-paper walkthrough + your hardest questions before the CS final.", "מעבר על מבחנים קודמים + השאלות הקשות שלכם לפני המבחן.", 7, 120, 4, List.of("cs101-night-owls", "cs101-debuggers")),
            new SessionDef("dr-omar-said", "Mechanics Problem-Solving Clinic", "קליניקת פתרון בעיות במכניקה", "We work through the trickiest force and energy problems together.", "פותרים יחד את בעיות הכוח והאנרגיה המאתגרות ביותר.", Duration.ofDays(2).toMinutes(), 90, 6, List.of("phy120-newtons-crew", "phy120-lab-partners")),
            new SessionDef("prof-daniel-roth", "Sociological Theory: Office Hours", "תיאוריה סוציולוגית: שעות קבלה", "Bring a theorist you're stuck on; we'll untangle it live.", "הביאו הוגה שאתם תקועים עליו; נפענח אותו יחד בשידור חי.", Duration.ofDays(7).toMinutes(), 60, 8, List.of("soc101-society-now", "ant130-fieldwork-friends")));

    /** Generic chat snippets (Demo-world §9); {course} is interpolated. EN + HE arrays are
     *  index-aligned; the seed picks one set by language. */
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

    private static final String[] CHAT_SNIPPETS_HE = {
            "היי לכולם 👋 שמח שהקבוצה הזו יצאה לדרך!",
            "מתי נפגשים השבוע?",
            "יום חמישי בערב מתאים לי 🙌",
            "העליתי את הסיכומים של {course} לקבצים 📄",
            "מישהו פתר את שאלה 3 בתרגיל? 😅",
            "גם אני לגמרי תקוע עליה — בואו נעבור על זה יחד",
            "הוספתי מפגש לימוד ליומן, תראו 📅",
            "תודה, זה ממש עזר 🙏",
            "אני אביא חטיפים 🍪",
            "בהצלחה במבחן האמצע של {course} לכולם! 🍀"};

    private static final List<String> CS101_CONVO = List.of(
            "Hey Night Owls 🦉 welcome to the Bubble!",
            "Finally a place to grind CS101 together 😄",
            "I uploaded the Week 1 + Week 2 notes to Files 📄",
            "Legend 🙏 anyone else lost on the recursion exercise?",
            "Let's cover it at the study session — added it to the calendar 📅",
            "I'll be there. Bringing snacks 🍪");

    private static final List<String> CS101_CONVO_HE = List.of(
            "היי ינשופי לילה 🦉 ברוכים הבאים לקבוצה!",
            "סוף סוף מקום לטחון את CS101 יחד 😄",
            "העליתי את הסיכומים של שבוע 1 + שבוע 2 לקבצים 📄",
            "אגדה 🙏 עוד מישהו אבוד בתרגיל הרקורסיה?",
            "בואו נעבור על זה במפגש הלימוד — הוספתי ליומן 📅",
            "אני אהיה שם. מביא חטיפים 🍪");

    // ── Seed entry point ───────────────────────────────────────────────────────

    @Transactional
    public DemoWorldHandle seed(String token, String lang) {
        boolean he = "he".equals(lang);   // any other value → English
        Instant t0 = timeProvider.now();
        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);

        // 1. University + term + departments + courses + offerings.
        UUID universityId = universityRepository.save(University.builder()
                .shortCode("BUU" + token).name(loc(he, "Bubble.up University", "אוניברסיטת Bubble.up")).country("US").build()).getId();

        LocalDate today = LocalDate.ofInstant(t0, ZoneOffset.UTC);
        termRepository.save(Term.builder()
                .universityId(universityId).code("DEMO").name(loc(he, "Current Semester", "סמסטר נוכחי")).kind(TermKind.SPRING)
                .academicYear(today.getYear()).startsOn(today.minusDays(45)).endsOn(today.plusDays(75)).build());
        Term term = termRepository.findByUniversityIdAndCode(universityId, "DEMO").orElseThrow();

        Map<String, UUID> deptIds = new HashMap<>();
        deptIds.put("SCI", departmentRepository.save(Department.builder()
                .universityId(universityId).shortCode("SCI").name(loc(he, "Science Department", "המחלקה למדעים")).build()).getId());
        deptIds.put("SOC", departmentRepository.save(Department.builder()
                .universityId(universityId).shortCode("SOC").name(loc(he, "Sociology Department", "המחלקה לסוציולוגיה")).build()).getId());

        Map<String, UUID> courseIds = new HashMap<>();
        for (CourseDef c : COURSES) {
            UUID courseId = courseRepository.save(Course.builder()
                    .universityId(universityId).code(c.code()).name(loc(he, c.name(), c.nameHe()))
                    .creditPoints(BigDecimal.valueOf(c.credits())).description(loc(he, c.description(), c.descriptionHe())).build()).getId();
            courseIds.put(c.code(), courseId);
            courseDepartmentRepository.save(CourseDepartment.builder()
                    .courseId(courseId).departmentId(deptIds.get(c.dept())).primary(true).build());
            courseOfferingRepository.save(CourseOffering.builder()
                    .courseId(courseId).termId(term.getId()).build());
        }

        // 2. Persona + expert + admin users.
        Map<String, UUID> userIds = new HashMap<>();
        for (Persona p : SCIENCE) userIds.put(p.slug(), createUser(p.slug(), loc(he, p.name(), p.nameHe()), token, passwordHash, UserRole.STUDENT, universityId, deptIds.get("SCI"), "people"));
        for (Persona p : SOCIOLOGY) userIds.put(p.slug(), createUser(p.slug(), loc(he, p.name(), p.nameHe()), token, passwordHash, UserRole.STUDENT, universityId, deptIds.get("SOC"), "people"));
        UUID adminId = createUser("admin", loc(he, "Demo Admin", "מנהל הדגמה"), token, passwordHash, UserRole.ADMIN, universityId, deptIds.get("SCI"), null);

        // 3. Experts: user + profile, then force VERIFIED / PENDING (config-independent).
        for (ExpertDef e : EXPERTS) {
            UUID uid = createUser(e.slug(), loc(he, e.name(), e.nameHe()), token, passwordHash, UserRole.STUDENT, universityId, deptIds.get(e.dept()), "experts");
            userIds.put(e.slug(), uid);
            expertProfileService.applyAsExpert(uid, new ApplyAsExpertRequest(loc(he, e.headline(), e.headlineHe()), loc(he, e.bio(), e.bioHe()), e.tags()));
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
                    loc(he, b.name(), b.nameHe()), loc(he, b.desc(), b.descHe()), GroupVisibility.PUBLIC, b.max(), null, courseIds.get(b.course())), ownerId).id();
            groupIds.put(b.slug(), groupId);
            for (int i = 1; i < b.members().size(); i++) {
                groupCommandService.joinGroup(groupId, userIds.get(b.members().get(i)));
            }
        }

        // 6. Character profiles → gives each bubble a distinct matching vector. Each persona
        //    leans toward the THEME role of the bubble they anchor (the one they own, else
        //    their first membership) — so a bubble's members share a role and its group
        //    vector gets a sharp peak, which is what makes the guest's complementarity
        //    match %s actually spread out instead of clustering.
        Map<String, Integer> personaRole = new HashMap<>();
        for (BubbleDef b : BUBBLES) personaRole.putIfAbsent(b.members().get(0), b.role()); // owners win their owned bubble
        for (BubbleDef b : BUBBLES) for (String slug : b.members()) personaRole.putIfAbsent(slug, b.role());
        for (Persona p : SCIENCE) matchingCommandService.seedCharacterAnswers(userIds.get(p.slug()), personaRole.getOrDefault(p.slug(), p.role()), CHARACTER_ANSWER_COUNT);
        for (Persona p : SOCIOLOGY) matchingCommandService.seedCharacterAnswers(userIds.get(p.slug()), personaRole.getOrDefault(p.slug(), p.role()), CHARACTER_ANSWER_COUNT);

        // 7. Calendar, files, chat history per bubble.
        int idx = 0;
        for (BubbleDef b : BUBBLES) {
            UUID groupId = groupIds.get(b.slug());
            UUID ownerId = userIds.get(b.members().get(0));
            String courseName = courseName(b.course(), he);
            seedCalendar(groupId, ownerId, courseName, idx, t0, he);
            seedFiles(groupId, ownerId, b.members().size(), t0, he);
            seedChat(groupId, b, userIds, courseName, idx, t0, he);
            idx++;
        }

        // 8. Expert sessions + group enrollments.
        for (SessionDef s : SESSIONS) {
            UUID hostId = userIds.get(s.host());
            Instant startsAt = t0.plus(Duration.ofMinutes(s.startMin()));
            UUID sessionId = expertSessionCommandService.createSession(hostId,
                    new CreateExpertSessionRequest(loc(he, s.title(), s.titleHe()), loc(he, s.desc(), s.descHe()), startsAt, startsAt.plus(Duration.ofMinutes(s.durMin())), s.capacity())).id();
            for (String bubbleSlug : s.bubbles()) {
                UUID groupId = groupIds.get(bubbleSlug);
                UUID groupOwnerId = userIds.get(ownerSlugOf(bubbleSlug));
                expertSessionCommandService.enrollGroup(sessionId, groupId, groupOwnerId);
            }
        }

        // 9. The visitor "You": no matching profile (Explore stays Trending-only until the tour quiz).
        UUID guestId = createUser("you", loc(he, "You", "אני"), token, passwordHash, UserRole.STUDENT, universityId, deptIds.get("SCI"), null);
        enrollOnce(new HashSet<>(), guestId, courseIds.get("CS101"));
        enrollmentCommandService.enroll(guestId, courseIds.get("SOC101"));
        UUID starterGroupId = groupIds.get("cs101-night-owls");
        groupCommandService.joinGroup(starterGroupId, guestId);
        onboardingStateRepository.save(OnboardingState.builder()
                .userId(guestId).wizardLevel(6).collapsed(false).updatedAt(t0).build());

        // 10. Warm the matching engine synchronously. The demo runs with
        //     app.matching.async-recompute=false, so the MatchingEventListener is disabled
        //     and the seed events above fire into the void — this is where matching state
        //     is actually built. Persona quiz profiles first (their QuizResponse rows were
        //     written inertly by seedCharacterAnswers), then each group profile (a group
        //     vector is the confidence-weighted average of its members'). The guest is left
        //     profile-less on purpose — Explore stays Trending-only until the tour quiz.
        //     One deduped pass ≈ a couple of seconds, vs the async herd's minutes.
        for (Persona p : SCIENCE) matchingCommandService.recomputeUserQuizProfile(userIds.get(p.slug()));
        for (Persona p : SOCIOLOGY) matchingCommandService.recomputeUserQuizProfile(userIds.get(p.slug()));
        for (UUID groupId : groupIds.values()) matchingCommandService.recomputeGroupProfile(groupId);

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

    private void seedCalendar(UUID groupId, UUID ownerId, String courseName, int idx, Instant t0, boolean he) {
        // MEETING (not STUDY_SESSION) so no live video room is auto-created at seed time.
        createEvent(groupId, ownerId, CalendarEventType.MEETING, loc(he, "Weekly study session", "מפגש לימוד שבועי"),
                t0.plus(Duration.ofDays(2)).plus(Duration.ofHours(idx % 4)), 90);
        createEvent(groupId, ownerId, CalendarEventType.DEADLINE,
                loc(he, courseName + " problem set due", "הגשת תרגיל ב" + courseName),
                t0.plus(Duration.ofDays(5)), 30);
        if (idx % 2 == 0) {
            createEvent(groupId, ownerId, CalendarEventType.EXAM,
                    loc(he, courseName + " midterm", "מבחן אמצע ב" + courseName),
                    t0.plus(Duration.ofDays(21)), 120);
        }
    }

    private void createEvent(UUID groupId, UUID ownerId, CalendarEventType type, String title, Instant startsAt, int durMin) {
        calendarCommandService.create(new CreateCalendarEventRequest(
                CalendarOwnerType.GROUP, groupId, type, title, startsAt, startsAt.plus(Duration.ofMinutes(durMin))), ownerId);
    }

    private void seedFiles(UUID groupId, UUID ownerId, int memberCount, Instant t0, boolean he) {
        // Display names + folder names are localized; the underlying asset bytes are shared
        // (PDFs keep Latin-only inner text — see DemoAssetRegistry — but welcome.txt has a
        // Hebrew variant selected by the asset key).
        addFile(groupId, ownerId, null, loc(he, "Syllabus.pdf", "סילבוס.pdf"), "syllabus.pdf", t0);
        addFile(groupId, ownerId, null, loc(he, "Welcome.txt", "ברוכים הבאים.txt"), he ? "welcome.he.txt" : "welcome.txt", t0);
        UUID lectureNotes = addFolder(groupId, ownerId, loc(he, "Lecture Notes", "סיכומי הרצאות"), t0);
        addFile(groupId, ownerId, lectureNotes, loc(he, "Week-1-Notes.pdf", "סיכום-שבוע-1.pdf"), "lecture-notes-1.pdf", t0);
        addFile(groupId, ownerId, lectureNotes, loc(he, "Week-2-Notes.pdf", "סיכום-שבוע-2.pdf"), "lecture-notes-2.pdf", t0);
        UUID pastExams = addFolder(groupId, ownerId, loc(he, "Past Exams", "מבחנים קודמים"), t0);
        addFile(groupId, ownerId, pastExams, loc(he, "2025-Midterm.pdf", "מבחן-אמצע-2025.pdf"), "past-exam-2025.pdf", t0);
        if (memberCount >= 4) {
            UUID resources = addFolder(groupId, ownerId, loc(he, "Resources", "משאבים"), t0);
            addFile(groupId, ownerId, resources, loc(he, "Cheat-Sheet.pdf", "דף-נוסחאות.pdf"), "cheat-sheet.pdf", t0);
            addFile(groupId, ownerId, resources, loc(he, "Diagram.png", "תרשים.png"), "diagram.png", t0);
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

    private void seedChat(UUID groupId, BubbleDef b, Map<String, UUID> userIds, String courseName, int idx, Instant t0, boolean he) {
        List<ChatRoomSummary> rooms = chatInternalService.getRoomsForGroup(groupId);
        if (rooms.isEmpty()) return;
        UUID roomId = rooms.get(0).id();

        // Push the auto-posted "X joined" system messages back before the chat history,
        // so the room reads: people joined ~8 days ago, then started chatting. (The
        // guest's own join happens later, at T0, so "You joined" stays newest.)
        chatMessageRepository.backdateRoomMessages(roomId, t0.minus(Duration.ofDays(8)));

        String[] snippets = he ? CHAT_SNIPPETS_HE : CHAT_SNIPPETS;
        List<String> lines = new ArrayList<>();
        if (b.slug().equals("cs101-night-owls")) {
            lines.addAll(he ? CS101_CONVO_HE : CS101_CONVO);
        } else {
            int count = 4 + (idx % 3); // 4–6
            for (int i = 0; i < count; i++) {
                lines.add(snippets[(idx + i) % snippets.length].replace("{course}", courseName));
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

    private static String courseName(String code, boolean he) {
        return COURSES.stream().filter(c -> c.code().equals(code)).findFirst()
                .map(c -> he ? c.nameHe() : c.name()).orElse(code);
    }

    /** Pick the English or Hebrew variant of a seeded string by the world's language. */
    private static String loc(boolean he, String en, String heText) {
        return he ? heText : en;
    }

    private static String ownerSlugOf(String bubbleSlug) {
        return BUBBLES.stream().filter(b -> b.slug().equals(bubbleSlug)).findFirst()
                .map(b -> b.members().get(0)).orElseThrow();
    }
}
