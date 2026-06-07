package com.ronkadosh.bubbleup.auth.application;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps a raw email address to an Israeli academic institution, when one matches.
 *
 * <p>All academic institutions in Israel live under the {@code .ac.il} TLD
 * (the "ac" = academic). Each institution publishes one or more sub-domains
 * for students vs. staff (e.g. {@code post.bgu.ac.il} for BGU students,
 * {@code bgu.ac.il} for BGU staff). This registry codifies the set so that:
 *
 * <ul>
 *   <li>The OAuth2 sign-in flow can reject Google logins whose email doesn't
 *       belong to an Israeli academic institution.</li>
 *   <li>We can auto-assign the {@code universityId} field on a new user.</li>
 *   <li>We can hint whether a sign-up looks like a student or a staff member
 *       (from the domain prefix), purely for UX defaults.</li>
 * </ul>
 *
 * <p>Lookup walks the email domain from most-specific to least-specific so
 * that arbitrary department sub-domains (e.g. {@code joe@cs.bgu.ac.il})
 * still resolve to BGU even though {@code cs.bgu.ac.il} isn't listed.
 */
@Component
public class UniversityEmailRegistry {

    public enum MemberKind { STUDENT, STAFF, UNKNOWN }

    /**
     * Public, immutable description of a matched institution.
     *
     * @param key          stable lowercase identifier (used by the catalog seed)
     * @param displayName  human-readable name (English + Hebrew where convenient)
     * @param kind         student / staff inferred from the matched sub-domain
     * @param matchedDomain the longest registry suffix that matched
     */
    public record Match(
            String key,
            String displayName,
            MemberKind kind,
            String matchedDomain
    ) {}

    /** Sub-domain prefixes that consistently indicate a student account. */
    private static final Set<String> STUDENT_PREFIXES = Set.of(
            "post.", "mail.", "live.", "campus.", "oumail.", "s.", "g."
    );

    /**
     * Map of registered domain suffix → institution record. Insertion order is
     * preserved so the longest matches stay near the top, but lookup explicitly
     * sorts by length to be safe against future edits.
     *
     * <p>Sources cross-checked against the institutions' admissions / IT
     * documentation pages (Jan 2026). Add new institutions here as the user
     * base expands — the change is one line and no migration is needed.
     */
    private static final Map<String, Record> REGISTRY = build();

    private record Record(String key, String displayName) {}

    private static Map<String, Record> build() {
        Map<String, Record> r = new LinkedHashMap<>();

        // ─── Research universities ────────────────────────────────────────
        r.put("post.bgu.ac.il",        new Record("bgu",        "Ben-Gurion University of the Negev"));
        r.put("bgu.ac.il",             new Record("bgu",        "Ben-Gurion University of the Negev"));

        r.put("mail.tau.ac.il",        new Record("tau",        "Tel Aviv University"));
        r.put("post.tau.ac.il",        new Record("tau",        "Tel Aviv University"));
        r.put("tauex.tau.ac.il",       new Record("tau",        "Tel Aviv University"));
        r.put("tau.ac.il",             new Record("tau",        "Tel Aviv University"));

        r.put("mail.huji.ac.il",       new Record("huji",       "Hebrew University of Jerusalem"));
        r.put("mscc.huji.ac.il",       new Record("huji",       "Hebrew University of Jerusalem"));
        r.put("huji.ac.il",            new Record("huji",       "Hebrew University of Jerusalem"));

        r.put("campus.technion.ac.il", new Record("technion",   "Technion — Israel Institute of Technology"));
        r.put("technion.ac.il",        new Record("technion",   "Technion — Israel Institute of Technology"));

        r.put("weizmann.ac.il",        new Record("weizmann",   "Weizmann Institute of Science"));

        r.put("live.biu.ac.il",        new Record("biu",        "Bar-Ilan University"));
        r.put("biu.ac.il",             new Record("biu",        "Bar-Ilan University"));

        r.put("campus.haifa.ac.il",    new Record("haifa",      "University of Haifa"));
        r.put("univ.haifa.ac.il",      new Record("haifa",      "University of Haifa"));
        r.put("staff.haifa.ac.il",     new Record("haifa",      "University of Haifa"));
        r.put("haifa.ac.il",           new Record("haifa",      "University of Haifa"));

        r.put("post.runi.ac.il",       new Record("runi",       "Reichman University (Herzliya)"));
        r.put("runi.ac.il",            new Record("runi",       "Reichman University (Herzliya)"));

        r.put("oumail.openu.ac.il",    new Record("openu",      "The Open University of Israel"));
        r.put("openu.ac.il",           new Record("openu",      "The Open University of Israel"));

        r.put("live.ariel.ac.il",      new Record("ariel",      "Ariel University"));
        r.put("ariel.ac.il",           new Record("ariel",      "Ariel University"));

        // ─── Academic colleges (selected; add more as needed) ─────────────
        r.put("s.afeka.ac.il",         new Record("afeka",      "Afeka College of Engineering"));
        r.put("afeka.ac.il",           new Record("afeka",      "Afeka College of Engineering"));

        r.put("post.bezalel.ac.il",    new Record("bezalel",    "Bezalel Academy of Arts and Design"));
        r.put("bezalel.ac.il",         new Record("bezalel",    "Bezalel Academy of Arts and Design"));

        r.put("colman.ac.il",          new Record("colman",     "The College of Management Academic Studies"));

        r.put("hit.ac.il",             new Record("hit",        "Holon Institute of Technology"));

        r.put("hac.ac.il",             new Record("hadassah",   "Hadassah Academic College"));

        r.put("g.jct.ac.il",           new Record("jct",        "Jerusalem College of Technology — Machon Lev"));
        r.put("jct.ac.il",             new Record("jct",        "Jerusalem College of Technology — Machon Lev"));

        r.put("kinneret.ac.il",        new Record("kinneret",   "Kinneret Academic College"));

        r.put("yvc.ac.il",             new Record("yvc",        "Max Stern Yezreel Valley College"));

        r.put("mta.ac.il",             new Record("mta",        "Academic College of Tel Aviv-Yaffo"));

        r.put("netanya.ac.il",         new Record("netanya",    "Netanya Academic College"));

        r.put("ono.ac.il",             new Record("ono",        "Ono Academic College"));

        r.put("pac.ac.il",             new Record("peres",      "Peres Academic Center"));

        r.put("ruppin.ac.il",          new Record("ruppin",     "Ruppin Academic Center"));

        r.put("ac.sce.ac.il",          new Record("sce",        "Sami Shamoon College of Engineering"));
        r.put("sce.ac.il",             new Record("sce",        "Sami Shamoon College of Engineering"));

        r.put("sapir.ac.il",           new Record("sapir",      "Sapir Academic College"));

        r.put("shenkar.ac.il",         new Record("shenkar",    "Shenkar College of Engineering, Design and Art"));

        r.put("telhai.ac.il",          new Record("telhai",     "Tel-Hai Academic College"));

        r.put("wgalil.ac.il",          new Record("wgalil",     "Western Galilee College"));

        r.put("zefat.ac.il",           new Record("zefat",      "Zefat Academic College"));

        return r;
    }

    /**
     * Attempt to match the supplied email to a known Israeli academic
     * institution. Returns empty when:
     * <ul>
     *   <li>the email is malformed (no {@code @});</li>
     *   <li>the domain doesn't end in {@code .ac.il}; or</li>
     *   <li>no registry entry matches even after walking down sub-domains.</li>
     * </ul>
     */
    public Optional<Match> lookup(String email) {
        if (email == null) return Optional.empty();
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) return Optional.empty();

        String domain = email.substring(at + 1).toLowerCase(Locale.ROOT).trim();
        if (domain.isBlank() || !domain.endsWith(".ac.il")) return Optional.empty();

        // Try most-specific suffix first.
        String d = domain;
        while (d.contains(".")) {
            Record hit = REGISTRY.get(d);
            if (hit != null) {
                MemberKind kind = inferKind(domain, d);
                return Optional.of(new Match(hit.key, hit.displayName, kind, d));
            }
            int dot = d.indexOf('.');
            d = d.substring(dot + 1);
        }
        return Optional.empty();
    }

    /**
     * True if the email is a syntactically plausible Israeli academic email
     * and resolves to a known institution. The OAuth callback uses this as
     * its hard gate.
     */
    public boolean isAcademicEmail(String email) {
        return lookup(email).isPresent();
    }

    /**
     * Lowercase keys of every supported institution, in registry order. Used
     * by the catalog seeder to bootstrap `universities` table entries.
     */
    public List<String> allUniversityKeys() {
        return REGISTRY.values().stream()
                .map(Record::key)
                .distinct()
                .collect(Collectors.toUnmodifiableList());
    }

    /** Student vs staff guess based on the matched sub-domain prefix. */
    private static MemberKind inferKind(String fullDomain, String matchedSuffix) {
        // What's "extra" before the matched suffix?
        if (fullDomain.equals(matchedSuffix)) {
            // No prefix at all — bare uni domain → typically staff.
            return MemberKind.STAFF;
        }
        String prefix = fullDomain.substring(0, fullDomain.length() - matchedSuffix.length());
        // prefix here ends with "." (e.g. "post.")
        for (String studentPrefix : STUDENT_PREFIXES) {
            if (prefix.endsWith(studentPrefix)) return MemberKind.STUDENT;
        }
        // Bare suffix matched + something else as prefix → probably department/faculty.
        return MemberKind.STAFF;
    }
}
