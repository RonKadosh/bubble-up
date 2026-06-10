package com.ronkadosh.bubbleup.auth.application;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 *       (from the matched sub-domain), purely for UX defaults.</li>
 * </ul>
 *
 * <p>Lookup walks the email domain from most-specific to least-specific so
 * that arbitrary department sub-domains (e.g. {@code joe@cs.bgu.ac.il})
 * still resolve to BGU even though {@code cs.bgu.ac.il} isn't listed.
 *
 * <p>If no registry entry matches but the domain still ends in {@code .ac.il},
 * we accept it as a generic academic institution with {@link MemberKind#UNKNOWN}.
 * That keeps Bubble.up aligned with the real product rule ("Israeli academic
 * accounts only") even when an institution uses a sub-domain variant we have
 * not enumerated yet.
 */
@Component
public class UniversityEmailRegistry {

    public enum MemberKind { STUDENT, STAFF, UNKNOWN }

    /**
     * Public, immutable description of a matched institution.
     *
     * @param key          stable lowercase identifier (used by the catalog seed)
     * @param displayName  human-readable name (English; localised in the UI layer)
     * @param kind         student / staff inferred from the matched sub-domain
     * @param matchedDomain the longest registry suffix that matched
     */
    public record Match(
            String key,
            String displayName,
            MemberKind kind,
            String matchedDomain
    ) {}

    /**
     * Registry entry. {@code kind} is set per-domain so we don't have to infer
     * STUDENT vs STAFF from string prefixes (which over-matches: "s." was
     * accidentally matching "cs.", and "g." accidentally matching arbitrary
     * department sub-domains).
     */
    private record Record(String key, String displayName, MemberKind kind) {}

    /**
     * Map of registered domain suffix → institution record. Insertion order is
     * preserved so the longest matches stay near the top, but lookup explicitly
     * walks shortest-to-longest by stripping leading sub-domains.
     *
     * <p>Sources cross-checked against institutions' admissions / IT
     * documentation (Jan 2026). Add new institutions here as the user base
     * expands — one line and no migration needed (ddl-auto: create-drop).
     */
    private static final Map<String, Record> REGISTRY = build();

    private static Map<String, Record> build() {
        Map<String, Record> r = new LinkedHashMap<>();

        // Helper: STD = student domain, STF = staff/faculty domain.
        // Used for compactness in the table below.

        // ─── Research universities (the "big 10") ─────────────────────────
        r.put("post.bgu.ac.il",        std("bgu",      "Ben-Gurion University of the Negev"));
        r.put("bgu.ac.il",             stf("bgu",      "Ben-Gurion University of the Negev"));

        r.put("mail.tau.ac.il",        std("tau",      "Tel Aviv University"));
        r.put("post.tau.ac.il",        std("tau",      "Tel Aviv University"));
        r.put("tauex.tau.ac.il",       stf("tau",      "Tel Aviv University"));
        r.put("tau.ac.il",             stf("tau",      "Tel Aviv University"));

        r.put("mail.huji.ac.il",       std("huji",     "Hebrew University of Jerusalem"));
        r.put("mscc.huji.ac.il",       stf("huji",     "Hebrew University of Jerusalem"));
        r.put("huji.ac.il",            stf("huji",     "Hebrew University of Jerusalem"));

        r.put("campus.technion.ac.il", std("technion", "Technion — Israel Institute of Technology"));
        r.put("technion.ac.il",        stf("technion", "Technion — Israel Institute of Technology"));

        r.put("weizmann.ac.il",        stf("weizmann", "Weizmann Institute of Science"));

        r.put("live.biu.ac.il",        std("biu",      "Bar-Ilan University"));
        r.put("biu.ac.il",             stf("biu",      "Bar-Ilan University"));

        r.put("campus.haifa.ac.il",    std("haifa",    "University of Haifa"));
        r.put("univ.haifa.ac.il",      stf("haifa",    "University of Haifa"));
        r.put("staff.haifa.ac.il",     stf("haifa",    "University of Haifa"));
        r.put("haifa.ac.il",           stf("haifa",    "University of Haifa"));

        r.put("post.runi.ac.il",       std("runi",     "Reichman University (Herzliya)"));
        r.put("runi.ac.il",            stf("runi",     "Reichman University (Herzliya)"));

        r.put("oumail.openu.ac.il",    std("openu",    "The Open University of Israel"));
        r.put("openu.ac.il",           stf("openu",    "The Open University of Israel"));

        r.put("live.ariel.ac.il",      std("ariel",    "Ariel University"));
        r.put("ariel.ac.il",           stf("ariel",    "Ariel University"));

        // ─── Academic colleges (add more as needed) ───────────────────────
        r.put("s.afeka.ac.il",         std("afeka",    "Afeka College of Engineering"));
        r.put("afeka.ac.il",           stf("afeka",    "Afeka College of Engineering"));

        r.put("post.bezalel.ac.il",    std("bezalel",  "Bezalel Academy of Arts and Design"));
        r.put("bezalel.ac.il",         stf("bezalel",  "Bezalel Academy of Arts and Design"));

        r.put("colman.ac.il",          stf("colman",   "The College of Management Academic Studies"));

        r.put("hit.ac.il",             stf("hit",      "Holon Institute of Technology"));

        r.put("hac.ac.il",             stf("hadassah", "Hadassah Academic College"));

        r.put("g.jct.ac.il",           std("jct",      "Jerusalem College of Technology — Machon Lev"));
        r.put("jct.ac.il",             stf("jct",      "Jerusalem College of Technology — Machon Lev"));

        r.put("kinneret.ac.il",        stf("kinneret", "Kinneret Academic College"));

        r.put("yvc.ac.il",             stf("yvc",      "Max Stern Yezreel Valley College"));

        r.put("mta.ac.il",             stf("mta",      "Academic College of Tel Aviv-Yaffo"));

        r.put("netanya.ac.il",         stf("netanya",  "Netanya Academic College"));

        r.put("ono.ac.il",             stf("ono",      "Ono Academic College"));

        r.put("pac.ac.il",             stf("peres",    "Peres Academic Center"));

        r.put("ruppin.ac.il",          stf("ruppin",   "Ruppin Academic Center"));

        r.put("ac.sce.ac.il",          std("sce",      "Sami Shamoon College of Engineering"));
        r.put("sce.ac.il",             stf("sce",      "Sami Shamoon College of Engineering"));

        r.put("sapir.ac.il",           stf("sapir",    "Sapir Academic College"));

        r.put("shenkar.ac.il",         stf("shenkar",  "Shenkar College of Engineering, Design and Art"));

        r.put("telhai.ac.il",          stf("telhai",   "Tel-Hai Academic College"));

        r.put("wgalil.ac.il",          stf("wgalil",   "Western Galilee College"));

        r.put("zefat.ac.il",           stf("zefat",    "Zefat Academic College"));

        return r;
    }

    private static Record std(String key, String displayName) {
        return new Record(key, displayName, MemberKind.STUDENT);
    }

    private static Record stf(String key, String displayName) {
        return new Record(key, displayName, MemberKind.STAFF);
    }

    /**
     * Attempt to match the supplied email to a known Israeli academic
     * institution. Returns empty when:
     * <ul>
     *   <li>the email is malformed (no {@code @});</li>
     *   <li>the domain doesn't end in {@code .ac.il}; or</li>
     *   <li>the domain is just {@code ac.il} with no institution label.</li>
     * </ul>
     */
    public Optional<Match> lookup(String email) {
        if (email == null) return Optional.empty();
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) return Optional.empty();

        String domain = email.substring(at + 1).toLowerCase(Locale.ROOT).trim();
        if (domain.isBlank() || !domain.endsWith(".ac.il")) return Optional.empty();

        // Walk from most-specific (full domain) to least-specific by stripping
        // one leading sub-domain at a time.
        String d = domain;
        while (d.contains(".")) {
            Record hit = REGISTRY.get(d);
            if (hit != null) {
                return Optional.of(new Match(hit.key, hit.displayName, hit.kind, d));
            }
            int dot = d.indexOf('.');
            d = d.substring(dot + 1);
        }
        return genericAcademicFallback(domain);
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

    private Optional<Match> genericAcademicFallback(String domain) {
        String suffix = ".ac.il";
        if (!domain.endsWith(suffix) || domain.length() <= suffix.length()) {
            return Optional.empty();
        }

        String[] labels = domain.split("\\.");
        if (labels.length < 3) {
            return Optional.empty();
        }

        String institutionLabel = labels[labels.length - 3].trim();
        if (institutionLabel.isBlank()) {
            return Optional.empty();
        }

        String rootDomain = institutionLabel + suffix;
        return Optional.of(new Match(
                institutionLabel,
                "Academic institution (" + rootDomain + ")",
                MemberKind.UNKNOWN,
                rootDomain
        ));
    }
}
