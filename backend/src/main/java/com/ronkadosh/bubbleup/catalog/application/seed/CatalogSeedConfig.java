package com.ronkadosh.bubbleup.catalog.application.seed;

import com.ronkadosh.bubbleup.catalog.model.TermKind;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Default catalog seed payload — BGU Information Systems Engineering (department 372),
 * Semester B תשפ"ו (Spring 2026), sourced from the department's published course list.
 *
 * Courses with prefixes 372.x and 237.x are cross-listed to both ISE (primary)
 * and SE (secondary), since at BGU those code ranges are shared between the
 * Software Engineering and Information Systems Engineering programs. Everything
 * else (general studies, humanities, math/physics service courses, English) is
 * ISE-only.
 *
 * Override the entire payload by exposing your own {@code @Bean CatalogSeedData}
 * elsewhere — the {@code @ConditionalOnMissingBean} here will yield to it.
 */
@Configuration
public class CatalogSeedConfig {

    private static final BigDecimal CP_0_00 = new BigDecimal("0.00");
    private static final BigDecimal CP_1_00 = new BigDecimal("1.00");
    private static final BigDecimal CP_1_50 = new BigDecimal("1.50");
    private static final BigDecimal CP_2_00 = new BigDecimal("2.00");
    private static final BigDecimal CP_3_00 = new BigDecimal("3.00");
    private static final BigDecimal CP_3_50 = new BigDecimal("3.50");
    private static final BigDecimal CP_4_00 = new BigDecimal("4.00");
    private static final BigDecimal CP_5_00 = new BigDecimal("5.00");
    private static final BigDecimal CP_6_00 = new BigDecimal("6.00");

    private static final List<CatalogSeedData.CourseDepartmentLinkSeed> ISE_PRIMARY = List.of(
            new CatalogSeedData.CourseDepartmentLinkSeed("ISE", true)
    );

    private static final List<CatalogSeedData.CourseDepartmentLinkSeed> ISE_AND_SE = List.of(
            new CatalogSeedData.CourseDepartmentLinkSeed("ISE", true),
            new CatalogSeedData.CourseDepartmentLinkSeed("SE", false)
    );

    @Bean
    @ConditionalOnMissingBean(CatalogSeedData.class)
    public CatalogSeedData defaultCatalogSeedData() {
        // Curated ISE/SE subset (Spring 2026 = 2025-B), kept first so the
        // findAll().findFirst() seed helpers in IntegrationTest land on a 372.x
        // ISE+SE course. The scraped BGU 2026C summer catalog (department-numeric
        // departments) is appended after, never before.
        List<CatalogSeedData.CourseSeed> iseCourses = buildIseCourses();
        List<CatalogSeedData.CourseSeed> summerCourses = buildBgu2026CCourses();

        List<CatalogSeedData.CourseSeed> courses = new ArrayList<>(iseCourses.size() + summerCourses.size());
        courses.addAll(iseCourses);
        courses.addAll(summerCourses);

        List<CatalogSeedData.OfferingSeed> offerings = new ArrayList<>(courses.size());
        for (CatalogSeedData.CourseSeed c : iseCourses) {
            offerings.add(new CatalogSeedData.OfferingSeed(c.code(), "2025-B"));
        }
        // The scraped catalog is exactly the courses active in Summer 2026
        // (קיץ תשפ"ו), which the seed already models as term "2025-S".
        for (CatalogSeedData.CourseSeed c : summerCourses) {
            offerings.add(new CatalogSeedData.OfferingSeed(c.code(), "2025-S"));
        }

        List<CatalogSeedData.DepartmentSeed> departments = new ArrayList<>();
        departments.add(new CatalogSeedData.DepartmentSeed("SE", "Software Engineering"));
        departments.add(new CatalogSeedData.DepartmentSeed("ISE", "Information Systems Engineering"));
        departments.addAll(buildBgu2026CDepartments());

        CatalogSeedData.UniversitySeed bgu = new CatalogSeedData.UniversitySeed(
                "BGU",
                "Ben-Gurion University of the Negev",
                "IL",
                departments,
                courses,
                List.of(
                        new CatalogSeedData.TermSeed(
                                "2025-A", "Fall 2025 / סמסטר א' תשפ\"ו",
                                TermKind.FALL, 2025,
                                LocalDate.of(2025, 10, 26), LocalDate.of(2026, 2, 6)
                        ),
                        new CatalogSeedData.TermSeed(
                                "2025-B", "Spring 2026 / סמסטר ב' תשפ\"ו",
                                TermKind.SPRING, 2025,
                                LocalDate.of(2026, 3, 15), LocalDate.of(2026, 7, 3)
                        ),
                        new CatalogSeedData.TermSeed(
                                "2025-S", "Summer 2026 / קיץ תשפ\"ו",
                                TermKind.SUMMER, 2025,
                                LocalDate.of(2026, 7, 5), LocalDate.of(2026, 9, 18)
                        )
                ),
                offerings
        );
        return new CatalogSeedData(List.of(bgu));
    }

    /**
     * Source: BGU department 372 (הנדסת מערכות מידע), Semester B תשפ"ו (2026),
     * generated 2026-05-25. 372.x / 237.x are core ISE+SE; everything else is
     * service / general-studies and stays ISE-only.
     *
     * Ordering note: the 372.x core block is placed first so that
     * {@code IntegrationTest.seedCourseId()} / {@code seedOfferingId()} —
     * which read {@code findAll().findFirst()} — reliably land on a course
     * that is cross-listed to SE, which {@code GroupsRelevantIT} requires.
     */
    private static List<CatalogSeedData.CourseSeed> buildIseCourses() {
        List<CatalogSeedData.CourseSeed> c = new ArrayList<>(70);

        // ─── 372.x — ISE / SE core (cross-listed) ───────────────────────────────
        c.add(new CatalogSeedData.CourseSeed("372.1.1117", "מבוא למערכות הפעלה",                                     CP_3_50, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.2104", "נושאים מתקדמים בתכנות",                                  CP_2_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.2306", "מודלים חישוביים",                                        CP_3_50, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.2402", "סביבות פיתוח באינטרנט",                                  CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.3021", "רגרסיה ותכנון ניסויים למערכות מידע",                     CP_3_50, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.3401", "ניתוח ועיצוב מערכות תוכנה",                              CP_5_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.3601", "סדנת הכנה לפרויקט בהנדסת מערכות מידע",                   CP_2_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.4108", "ניהול פרוייקט תוכנה",                                    CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.4601", "אבטחת מחשבים ורשתות תקשורת",                             CP_3_50, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.4807", "נפלאות המתמטיקה ואלגוריתמים",                            CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.4902", "ניתוח וקבלת החלטות במערכות מידע",                        CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.4950", "מבוא לפילוסופיה של המדע למהנדסים",                       CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.4961", "מכריית נתונים לאלגוטרייד",                               CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.5001", "התמחות בתעשיה בהנדסת תוכנה ונתונים",                     CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.1.5922", "הסקת מסקנות תקפות מניתוח המציאות",                       CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("372.5.2026", "חרבות ברזל - בחירה במחלקה",                              CP_0_00, null, ISE_PRIMARY));

        // ─── 237.x — Software / Data Engineering (ISE + SE) ─────────────────────
        c.add(new CatalogSeedData.CourseSeed("237.1.1021", "מבוא להסתברות להנדסת מערכות מידע והנדסת נתונים",          CP_3_50, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.1.1115", "מבני נתונים להנדסת מערכות מידע והנדסת נתונים",            CP_5_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.1.1131", "מבוא לתכנות מונחה עצמים",                                CP_1_50, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.5203", "שיטות לזיהוי תקיפות",                                    CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.5212", "שיתוף פעולה בבינה מלאכותית",                             CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.5324", "שימושי מדעי הנתונים בגנומיקה ובסרטן: התמקדות באונקולוגיה חישובית", CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.5356", "שיטות חישוביות באינטליגנציה נרטיבית",                    CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.5401", "כריית מידע במאגרי נתונים גדולים",                        CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.5513", "שיטות חיפוש בבינה מלאכותית",                             CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.5616", "מחשוב חסוי",                                             CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.5803", "מודלי חיזוי ברפואה (עם יישום בשפת התכנות R)",             CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.5850", "סמנטיקה חישובית והבנת שפה טבעית",                        CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.5865", "יישומים מתקדמים של מודלי שפה: הטמעה, התאמה, וסוכנים חכמים", CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.6101", "למידה עמוקה",                                            CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.6301", "מבוא למדעי נתונים קליניים",                              CP_3_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("237.2.6802", "בינה מלאכותית מתקדמת לרפואה",                            CP_3_00, null, ISE_AND_SE));

        // ─── 203.x / 214.x / 232.x — math + physics service courses (cross-listed) ─
        c.add(new CatalogSeedData.CourseSeed("203.1.1391", "פיסיקה 1ב",                                              CP_3_50, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("203.1.1651", "פיסיקה מודרנית להנדסת תוכנה",                            CP_3_50, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("214.1.9111", "מתמטיקה דיסקרטית להנדסת נתונים",                          CP_6_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("214.1.9761", "חדו\"א 2 להנדסת מערכות תוכנה ומידע",                      CP_4_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("232.2.4971", "חישוב קוונטי",                                           CP_4_00, null, ISE_AND_SE));

        // ─── Projects, ethics, library ──────────────────────────────────────────
        c.add(new CatalogSeedData.CourseSeed("373.1.5002", "פרויקט - הצעת תזה 2",                                    CP_4_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("382.1.3602", "אתיקה של מדעי הנתונים",                                  CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("382.1.4002", "פרויקט מסכם 2",                                          CP_5_00, null, ISE_AND_SE));
        c.add(new CatalogSeedData.CourseSeed("360.1.0011", "הכרת הספריה",                                            CP_0_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("360.1.2041", "מיומנויות מחקר",                                         CP_3_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("360.1.5001", "לימודים בחו\"ל",                                         CP_3_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("360.5.2026", "חרבות ברזל - קורסים כלליים",                             CP_0_00, null, ISE_PRIMARY));

        // ─── 142.x / 681.x / 687.x — management, marketing, finance, ethics ─────
        c.add(new CatalogSeedData.CourseSeed("142.1.3141", "מבוא לכלכלה לתעשיה וניהול",                              CP_3_50, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("681.1.0042", "מבוא להתנהגות הארגונית מאקרו",                           CP_3_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("681.1.0049", "עקרונות השיווק להנדסה",                                  CP_3_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("681.1.0123", "אתיקה בהנדסה",                                           CP_1_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("681.1.8002", "התוכנית האקדמית להכשרת מנהיגות יזמית - אסטרטגיה עסקית",  CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("681.1.8003", "התוכנית האקדמית להכשרת מנהיגות יזמית - ארגז כלים לבניה וניהול חברה", CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("687.2.0604", "מימון למנהלים",                                          CP_3_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("687.2.0605", "שיווק לתלמידי הנדסה",                                    CP_3_00, null, ISE_PRIMARY));

        // ─── 153.x — English language tracks ────────────────────────────────────
        c.add(new CatalogSeedData.CourseSeed("153.1.5031", "אנגלית בסיסי - טבע והנדסה",                              CP_0_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("153.1.5041", "אנגלית מתקדמים א - טבע והנדסה",                          CP_0_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("153.1.5051", "אנגלית מתקדמים ב - טבע והנדסה",                          CP_2_00, null, ISE_PRIMARY));

        // ─── 102.x / 131.x / 132.x / 134.x / 135.x / 167.x / 192.x — general studies ─
        c.add(new CatalogSeedData.CourseSeed("102.1.0276", "מהשואה לשבעה באוקטובר: זיכרון, עדות ופוליטיקה של הנצחה",   CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("102.1.0502", "שואה שלנו? סוגיות חברתיות בתולדות השואה",                 CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("131.1.0269", "בודהיזם כפילוסופיה",                                     CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("132.1.0304", "שינוי אקלים וספרות האנתרופוקן",                          CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("134.1.0267", "על האיש הפשוט בציור של העת החדשה המודרנית",               CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("134.1.0356", "תפיסת הנוף באמנות המודרנית והעכשווית",                    CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("135.1.0134", "יישומי חישה מרחוק לארכיאולוגיה",                          CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("135.1.1361", "טכנולוגיה וטיפולוגיה של כלי צור",                        CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("135.1.1431", "קרמיקה קדומה: מראשית תקופת הברונזה התיכונה ועד שלהי תקופת הברזל", CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("167.1.0626", "לספר את הסיפור הישראלי: הפקת פודקאסטים",                  CP_2_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("192.1.0436", "אפריקה בתקופה הקולוניאלית - ב",                          CP_2_00, null, ISE_PRIMARY));

        // ─── 700.x — sport electives ────────────────────────────────────────────
        c.add(new CatalogSeedData.CourseSeed("700.1.1103", "נבחרות ספורט - מתקדמים",                                 CP_1_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("700.1.7004", "או-שו (קונג-פו) מסורתי",                                 CP_1_00, null, ISE_PRIMARY));

        // ─── 970.x — TA training workshops ──────────────────────────────────────
        c.add(new CatalogSeedData.CourseSeed("970.5.0002", "סדנת הוראה לסגל זוטר",                                   CP_0_00, null, ISE_PRIMARY));
        c.add(new CatalogSeedData.CourseSeed("970.5.0007", "סדנת הוראה לסגל הזוטר - מדריכי מעבדה",                    CP_0_00, null, ISE_PRIMARY));

        return c;
    }

    /**
     * BGU departments that had courses active in Summer 2026 (2026C), keyed by
     * BGU's numeric department code with the registry's Hebrew name. Distinct from
     * the curated "ISE"/"SE" shortCodes above — these are the authoritative
     * registry departments the scraped courses belong to.
     */
    private static List<CatalogSeedData.DepartmentSeed> buildBgu2026CDepartments() {
        return List.of(
                new CatalogSeedData.DepartmentSeed("1", "במחלקה ללימודי מדבר"),
                new CatalogSeedData.DepartmentSeed("124", "במחלקה ללימודי המזרח התיכון"),
                new CatalogSeedData.DepartmentSeed("135", "במחלקה לארכיאולוגיה"),
                new CatalogSeedData.DepartmentSeed("138", "במחלקה לפוליטיקה וממשל"),
                new CatalogSeedData.DepartmentSeed("142", "במחלקה לכלכלה"),
                new CatalogSeedData.DepartmentSeed("153", "במחלקה לאנגלית כשפה זרה"),
                new CatalogSeedData.DepartmentSeed("185", "בחטיבה למדעים וטכנולוגיה"),
                new CatalogSeedData.DepartmentSeed("202", "במחלקה למדעי המחשב"),
                new CatalogSeedData.DepartmentSeed("205", "במחלקה למדעי החיים"),
                new CatalogSeedData.DepartmentSeed("212", "היחידה למתמטיקה - קורסי שירות"),
                new CatalogSeedData.DepartmentSeed("214", "היחידה להוראת קורסי יסוד"),
                new CatalogSeedData.DepartmentSeed("232", "התכנית למדעי המחשב"),
                new CatalogSeedData.DepartmentSeed("237", "התכנית להנדסת מערכות מידע"),
                new CatalogSeedData.DepartmentSeed("238", "התכנית להנדסת נתונים"),
                new CatalogSeedData.DepartmentSeed("361", "במחלקה להנדסת חשמל ומחשבים"),
                new CatalogSeedData.DepartmentSeed("364", "במחלקה להנדסת תעשיה וניהול"),
                new CatalogSeedData.DepartmentSeed("470", "במחלקה למדעי הרפואה"),
                new CatalogSeedData.DepartmentSeed("480", "במחלקה למדיניות וניהול  מערכות בריאות"),
                new CatalogSeedData.DepartmentSeed("500", "המכינה לקורסי הכנה לאקדמיה"),
                new CatalogSeedData.DepartmentSeed("503", "התכנית לעברית כשפה שנייה"),
                new CatalogSeedData.DepartmentSeed("504", "התכנית לנתיב לאקדמיה"),
                new CatalogSeedData.DepartmentSeed("506", "המכינה ל הכנה למכינה"),
                new CatalogSeedData.DepartmentSeed("525", "המכינה לתכנית אופק לחברה הבדואית"),
                new CatalogSeedData.DepartmentSeed("595", "התכנית לקורס חלופה לציון הכמותי בפסיכומטרי"),
                new CatalogSeedData.DepartmentSeed("681", "במחלקה לניהול"),
                new CatalogSeedData.DepartmentSeed("685", "במחלקה לניהול ומדיניות ציבורית"),
                new CatalogSeedData.DepartmentSeed("687", "במחלקה למנהל עסקים"),
                new CatalogSeedData.DepartmentSeed("900", "בית הספר ללימודי המשך")
        );
    }

    /**
     * BGU courses active in Summer 2026 (2026C / קיץ תשפ"ו), scraped from the
     * public course-file registry one department at a time. Lean fields only —
     * code + Hebrew name; creditPoints/description are unknown from the catalog
     * listing and left null. Each course links primary to its numeric registry
     * department (the code prefix). Offered in term 2025-S.
     */
    private static List<CatalogSeedData.CourseSeed> buildBgu2026CCourses() {
        List<CatalogSeedData.CourseSeed> c = new ArrayList<>(105);
        c.add(new CatalogSeedData.CourseSeed("1.2.2066", "מעבדה במטבולומיקה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("1", true))));
        c.add(new CatalogSeedData.CourseSeed("1.2.2076", "ציטומטריה סביבתית שימושית", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("1", true))));
        c.add(new CatalogSeedData.CourseSeed("1.2.5005", "שיטות מעבדה בלימודי סביבה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("1", true))));
        c.add(new CatalogSeedData.CourseSeed("1.2.5068", "שימוש בתכנה פריקיוסי למודלים בכימיה של מים", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("1", true))));
        c.add(new CatalogSeedData.CourseSeed("1.2.6002", "תאוריה ויישומי מערכות מידע גיאוגרפיות (GIS)", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("1", true))));
        c.add(new CatalogSeedData.CourseSeed("124.2.166", "הנגב: סיור לימודי", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("124", true))));
        c.add(new CatalogSeedData.CourseSeed("135.1.335", "חפירה ארכיאולוגית: שיטות עבודה ותיעוד בשדה - מתחילים 1", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("135", true))));
        c.add(new CatalogSeedData.CourseSeed("135.1.345", "חפירה ארכיאולוגית: שיטות עבודה ותיעוד בשדה - מתחילים 2", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("135", true))));
        c.add(new CatalogSeedData.CourseSeed("135.1.355", "חפירה ארכיאולוגית: שיטות עבודה ותיעוד בשדה - מתקדמים 1", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("135", true))));
        c.add(new CatalogSeedData.CourseSeed("135.1.365", "חפירה ארכיאולוגית: שיטות עבודה ותיעוד בשדה - מתקדמים 2", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("135", true))));
        c.add(new CatalogSeedData.CourseSeed("138.1.261", "מבוא לפוליטיקה וממשל", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("138", true))));
        c.add(new CatalogSeedData.CourseSeed("138.1.389", "יחסים בין לאומיים - מבוא", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("138", true))));
        c.add(new CatalogSeedData.CourseSeed("138.1.399", "פוליטיקה בת זמננו - מבוא היסטורי", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("138", true))));
        c.add(new CatalogSeedData.CourseSeed("138.1.464", "מגמות פנימיות, אזוריות ובינלאומיות בדוקטרינת הביטחון הלאומי של ישראל", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("138", true))));
        c.add(new CatalogSeedData.CourseSeed("142.1.1021", "מבוא לכלכלה 2", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("142", true))));
        c.add(new CatalogSeedData.CourseSeed("142.1.1421", "מבוא לחשבונאות פיננסית 2", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("142", true))));
        c.add(new CatalogSeedData.CourseSeed("153.1.5031", "אנגלית בסיסי -  טבע והנדסה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("153", true))));
        c.add(new CatalogSeedData.CourseSeed("153.1.5051", "אנגלית מתקדמים ב - טבע והנדסה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("153", true))));
        c.add(new CatalogSeedData.CourseSeed("185.1.1", "מבוא לפיזיקה לתעופה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("185", true))));
        c.add(new CatalogSeedData.CourseSeed("185.1.1001", "עקרונות מתמטיים לתעופה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("185", true))));
        c.add(new CatalogSeedData.CourseSeed("185.1.1004", "אוירודינמיקה לתעופה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("185", true))));
        c.add(new CatalogSeedData.CourseSeed("185.1.1005", "מבנה, תיכון ומערכות מטוס", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("185", true))));
        c.add(new CatalogSeedData.CourseSeed("185.1.1018", "מכניקת טיסה - מטוסים", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("185", true))));
        c.add(new CatalogSeedData.CourseSeed("185.1.1022", "פיזיקה מתקדמת לתעופה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("185", true))));
        c.add(new CatalogSeedData.CourseSeed("185.1.2", "פיזיקה 1 לתעופה חלק א", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("185", true))));
        c.add(new CatalogSeedData.CourseSeed("185.1.200", "הדרכה בספריה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("185", true))));
        c.add(new CatalogSeedData.CourseSeed("185.1.3", "פיזיקה 1 לתעופה חלק ב", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("185", true))));
        c.add(new CatalogSeedData.CourseSeed("185.1.6", "מעם למדינה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("185", true))));
        c.add(new CatalogSeedData.CourseSeed("185.1.9", "גאודזיה מיפוי וניווט", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("185", true))));
        c.add(new CatalogSeedData.CourseSeed("202.1.3011", "מבוא לאנליזה נומרית", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("202", true))));
        c.add(new CatalogSeedData.CourseSeed("202.1.4741", "נושאים ביישומים של מדעי המחשב", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("202", true))));
        c.add(new CatalogSeedData.CourseSeed("202.1.4821", "נושאים בגרפים רנדומיים", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("202", true))));
        c.add(new CatalogSeedData.CourseSeed("202.1.5012", "מבוא לתכנות עם מודלי שפה גדולים (LLMs)", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("202", true))));
        c.add(new CatalogSeedData.CourseSeed("202.1.5061", "מערכות בסיס נתונים", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("202", true))));
        c.add(new CatalogSeedData.CourseSeed("202.1.6321", "יצירת מספרים אקראיים ושימושיה למדעי המחשב", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("202", true))));
        c.add(new CatalogSeedData.CourseSeed("205.1.1471", "מחנה אקולוגי א", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("205", true))));
        c.add(new CatalogSeedData.CourseSeed("212.1.201", "מבוא ללוגיקה ולתורת הקבוצות למדעי המחשב והנדסת תכנה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("212", true))));
        c.add(new CatalogSeedData.CourseSeed("212.1.2361", "חדו\"א 1 למדעי המחשב והנדסת תוכנה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("212", true))));
        c.add(new CatalogSeedData.CourseSeed("212.1.3011", "מבוא להסתברות למדעי המחשב", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("212", true))));
        c.add(new CatalogSeedData.CourseSeed("212.1.71", "פונקציות מרוכבות להנדסת חשמל", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("212", true))));
        c.add(new CatalogSeedData.CourseSeed("212.1.9521", "אלגברה ליניארית להנדסת חשמל 2", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("212", true))));
        c.add(new CatalogSeedData.CourseSeed("212.1.9681", "חשבון אינטגרלי ומשוואות דיפרנציאליות רגילות להנדסת חשמל", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("212", true))));
        c.add(new CatalogSeedData.CourseSeed("212.1.9831", "תורת ההסתברות להנדסת חשמל", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("212", true))));
        c.add(new CatalogSeedData.CourseSeed("214.1.9281", "מבוא לאלגברה לינארית ג", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("214", true))));
        c.add(new CatalogSeedData.CourseSeed("214.1.9321", "אלגברה לינארית להנדסה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("214", true))));
        c.add(new CatalogSeedData.CourseSeed("232.1.1011", "מבוא למדעי המחשב", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("232", true))));
        c.add(new CatalogSeedData.CourseSeed("232.1.1031", "מבני נתונים", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("232", true))));
        c.add(new CatalogSeedData.CourseSeed("237.1.1111", "מבוא למדעי המחשב בשפת פייתון", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("237", true))));
        c.add(new CatalogSeedData.CourseSeed("238.1.1111", "מבוא למדעי המחשב בשפת פייתון", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("238", true))));
        c.add(new CatalogSeedData.CourseSeed("361.1.3131", "מערכות ספרתיות", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("361", true))));
        c.add(new CatalogSeedData.CourseSeed("364.1.1111", "מבוא לחקר ביצועים", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("364", true))));
        c.add(new CatalogSeedData.CourseSeed("364.1.1351", "בינה עסקית ומערכות תומכות החלטה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("364", true))));
        c.add(new CatalogSeedData.CourseSeed("364.1.1591", "ניהול פרוייקטים של תוכנה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("364", true))));
        c.add(new CatalogSeedData.CourseSeed("364.1.1911", "אסטרטגיה וניהול של מערכות מידע", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("364", true))));
        c.add(new CatalogSeedData.CourseSeed("364.1.2071", "תשתית טכנולוגיות מידע", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("364", true))));
        c.add(new CatalogSeedData.CourseSeed("364.1.3307", "פרויקט גמר - הקמת אב-טיפוס למערכת מידע", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("364", true))));
        c.add(new CatalogSeedData.CourseSeed("364.1.4041", "יסודות פיתוח תוכנה מונחה עצמים", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("364", true))));
        c.add(new CatalogSeedData.CourseSeed("364.1.4141", "יסודות מערכות מידע", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("364", true))));
        c.add(new CatalogSeedData.CourseSeed("364.1.4381", "יישומים ארגוניים של טכנולוגיות מידע (ERP)", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("364", true))));
        c.add(new CatalogSeedData.CourseSeed("470.1.10", "מעורבות סטודנטים בפעילות חברתית וקהילתית", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("470", true))));
        c.add(new CatalogSeedData.CourseSeed("470.2.100", "שימוש וטיפול בבעלי חיים במחקר", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("470", true))));
        c.add(new CatalogSeedData.CourseSeed("480.2.2004", "סמינריון אינטגרטיבי בניהול מערכות בריאות", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("480", true))));
        c.add(new CatalogSeedData.CourseSeed("500.5.6", "מבוא לפיזיקה - מכניקה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("500", true))));
        c.add(new CatalogSeedData.CourseSeed("503.5.4", "עברית כשפה רמה ד", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("503", true))));
        c.add(new CatalogSeedData.CourseSeed("503.5.6", "עברית כשפה רמה ו", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("503", true))));
        c.add(new CatalogSeedData.CourseSeed("504.5.1", "אוריינות עברית", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("504", true))));
        c.add(new CatalogSeedData.CourseSeed("504.5.2", "חשיבה מתמטית", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("504", true))));
        c.add(new CatalogSeedData.CourseSeed("504.5.5", "אנגלית נתיב לאקדמיה בסיסי", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("504", true))));
        c.add(new CatalogSeedData.CourseSeed("504.5.6", "אנגלית נתיב לאקדמיה מתקדמים א", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("504", true))));
        c.add(new CatalogSeedData.CourseSeed("504.5.7", "אנגלית נתיב לאקדמיה מתקדמים ב", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("504", true))));
        c.add(new CatalogSeedData.CourseSeed("506.5.3", "קורס ריענון  במתמטיקה למועמדים ללימודי המכינה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("506", true))));
        c.add(new CatalogSeedData.CourseSeed("525.5.20", "יסודות בחדוא ואלגברה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("525", true))));
        c.add(new CatalogSeedData.CourseSeed("595.5.1", "קורס חלופה לציון הכמותי בפסיכומטרי", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("595", true))));
        c.add(new CatalogSeedData.CourseSeed("681.1.101", "שיטות כמותיות 1", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("681", true))));
        c.add(new CatalogSeedData.CourseSeed("681.1.1061", "מבוא לחשבונאות", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("681", true))));
        c.add(new CatalogSeedData.CourseSeed("681.1.2041", "מבוא לשיווק", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("681", true))));
        c.add(new CatalogSeedData.CourseSeed("681.1.2061", "מבוא למימון", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("681", true))));
        c.add(new CatalogSeedData.CourseSeed("681.1.252", "יזמות היי-טק", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("681", true))));
        c.add(new CatalogSeedData.CourseSeed("681.1.280", "מבוא לכלכלה לניהול", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("681", true))));
        c.add(new CatalogSeedData.CourseSeed("681.1.3041", "שיפוט וקבלת החלטות", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("681", true))));
        c.add(new CatalogSeedData.CourseSeed("681.1.36", "מבוא לסטטיסטיקה לניהול", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("681", true))));
        c.add(new CatalogSeedData.CourseSeed("681.1.4041", "אסטרטגיה עסקית", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("681", true))));
        c.add(new CatalogSeedData.CourseSeed("681.1.42", "מבוא להתנהגות הארגונית מאקרו", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("681", true))));
        c.add(new CatalogSeedData.CourseSeed("685.2.148", "ניהול עסקי של המערך הפנסיוני בישראל", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("685", true))));
        c.add(new CatalogSeedData.CourseSeed("685.5.2026", "סטודנטים במילואים - חרבות ברזל - קורסי בחירה במחלקה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("685", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.1006", "ניהול פרויקטים למנהלים", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.1402", "בניין כח שיטתי בגישת הנדסת מערכות", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.1403", "ניהול מערכות חדשנות בארגונים", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.1721", "אסטרטגיית שיווק תחרותי", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.1728", "ניתוח אסטרטגי יישומי", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.1734", "חשיבה יצירתית מתודולוגיות בחשיבה יצירתית ויזמות", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.1736", "part A- Entrepreneurship Masterclass - Turning Disruptive Ideas into Impactful Ventures", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.1737", "יתת אמן ביזמות - כל הדרך מרעיונות משבשים למיזמים בעלי השפעה - חלק ב'", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.1742", "ניהול במגוון צורות ארגוניות", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.1743", "יוזמות בינלאומיות", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.2017", "ממשל תאגידי - אחריות הדירקטוריון וההנהלה הבכירה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.305", "אסטרטגיה ומדיניות מנהל עסקים למנהלים", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.4005", "חזון ולמידה ארגונית", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.4006", "ניהול השיווק למנהיגות חברתית", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.4022", "היבטים כלכליים וחשבונאיים בניהול ארגונים ללא כוונת רווח", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.4041", "מנהיגות", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.4046", "עיצוב מדיניות חברתית על בסיס נתונים ושותפויות בין-מגזריות", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.2.46", "חשיבה יצירתית בפיתוח מוצרים ובפרסום", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("687.5.2026", "סטודנטים במילואים - חרבות ברזל - קורסי בחירה במחלקה", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("687", true))));
        c.add(new CatalogSeedData.CourseSeed("900.5.5002", "לומדה להכרת החוק והנהלים למניעת הטרדה מינית", null, null, List.of(new CatalogSeedData.CourseDepartmentLinkSeed("900", true))));
        return c;
    }
}
