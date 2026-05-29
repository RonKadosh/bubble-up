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
        List<CatalogSeedData.CourseSeed> courses = buildIseCourses();
        List<CatalogSeedData.OfferingSeed> offerings = new ArrayList<>(courses.size());
        for (CatalogSeedData.CourseSeed c : courses) {
            offerings.add(new CatalogSeedData.OfferingSeed(c.code(), "2025-B"));
        }

        CatalogSeedData.UniversitySeed bgu = new CatalogSeedData.UniversitySeed(
                "BGU",
                "Ben-Gurion University of the Negev",
                "IL",
                List.of(
                        new CatalogSeedData.DepartmentSeed("SE", "Software Engineering"),
                        new CatalogSeedData.DepartmentSeed("ISE", "Information Systems Engineering")
                ),
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
}
