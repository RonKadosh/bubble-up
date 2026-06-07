package com.ronkadosh.bubbleup.auth;

import com.ronkadosh.bubbleup.auth.application.UniversityEmailRegistry;
import com.ronkadosh.bubbleup.auth.application.UniversityEmailRegistry.MemberKind;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UniversityEmailRegistryTest {

    private final UniversityEmailRegistry registry = new UniversityEmailRegistry();

    @Test
    void rejectsNonAcademicEmails() {
        assertThat(registry.isAcademicEmail(null)).isFalse();
        assertThat(registry.isAcademicEmail("")).isFalse();
        assertThat(registry.isAcademicEmail("notanemail")).isFalse();
        assertThat(registry.isAcademicEmail("user@gmail.com")).isFalse();
        assertThat(registry.isAcademicEmail("user@example.com")).isFalse();
        assertThat(registry.isAcademicEmail("user@somethingelse.co.il")).isFalse();
    }

    @Test
    void rejectsAcIlDomainThatIsNotRegistered() {
        // A made-up institution that ends in .ac.il but isn't in the registry.
        assertThat(registry.isAcademicEmail("user@unknown-college.ac.il")).isFalse();
    }

    @Test
    void matchesBgu() {
        Optional<UniversityEmailRegistry.Match> student = registry.lookup("aamit@post.bgu.ac.il");
        assertThat(student).isPresent();
        assertThat(student.get().key()).isEqualTo("bgu");
        assertThat(student.get().kind()).isEqualTo(MemberKind.STUDENT);

        Optional<UniversityEmailRegistry.Match> staff = registry.lookup("prof@bgu.ac.il");
        assertThat(staff).isPresent();
        assertThat(staff.get().key()).isEqualTo("bgu");
        assertThat(staff.get().kind()).isEqualTo(MemberKind.STAFF);
    }

    @Test
    void matchesDepartmentSubdomainFallingThroughToMainDomain() {
        // joe@cs.bgu.ac.il isn't a registered domain, but the .bgu.ac.il
        // suffix is, so we fall through and still identify BGU.
        Optional<UniversityEmailRegistry.Match> match = registry.lookup("joe@cs.bgu.ac.il");
        assertThat(match).isPresent();
        assertThat(match.get().key()).isEqualTo("bgu");
        assertThat(match.get().kind()).isEqualTo(MemberKind.STAFF); // unknown prefix = best guess STAFF
    }

    @Test
    void matchesAllResearchUniversities() {
        assertThat(registry.lookup("a@mail.tau.ac.il").get().key()).isEqualTo("tau");
        assertThat(registry.lookup("a@mail.huji.ac.il").get().key()).isEqualTo("huji");
        assertThat(registry.lookup("a@campus.technion.ac.il").get().key()).isEqualTo("technion");
        assertThat(registry.lookup("a@weizmann.ac.il").get().key()).isEqualTo("weizmann");
        assertThat(registry.lookup("a@live.biu.ac.il").get().key()).isEqualTo("biu");
        assertThat(registry.lookup("a@campus.haifa.ac.il").get().key()).isEqualTo("haifa");
        assertThat(registry.lookup("a@post.runi.ac.il").get().key()).isEqualTo("runi");
        assertThat(registry.lookup("a@oumail.openu.ac.il").get().key()).isEqualTo("openu");
        assertThat(registry.lookup("a@live.ariel.ac.il").get().key()).isEqualTo("ariel");
    }

    @Test
    void matchesMixedCaseAndWhitespace() {
        Optional<UniversityEmailRegistry.Match> match = registry.lookup("Aamit@Post.BGU.Ac.IL");
        assertThat(match).isPresent();
        assertThat(match.get().key()).isEqualTo("bgu");
        assertThat(match.get().kind()).isEqualTo(MemberKind.STUDENT);
    }

    @Test
    void rejectsMalformedAtSign() {
        assertThat(registry.lookup("@post.bgu.ac.il")).isEmpty();
        assertThat(registry.lookup("user@")).isEmpty();
        assertThat(registry.lookup("no-at-sign")).isEmpty();
    }

    @Test
    void distinguishesStudentVsStaffByPrefix() {
        assertThat(registry.lookup("a@post.bgu.ac.il").get().kind()).isEqualTo(MemberKind.STUDENT);
        assertThat(registry.lookup("a@mail.tau.ac.il").get().kind()).isEqualTo(MemberKind.STUDENT);
        assertThat(registry.lookup("a@campus.technion.ac.il").get().kind()).isEqualTo(MemberKind.STUDENT);
        assertThat(registry.lookup("a@live.biu.ac.il").get().kind()).isEqualTo(MemberKind.STUDENT);

        assertThat(registry.lookup("a@bgu.ac.il").get().kind()).isEqualTo(MemberKind.STAFF);
        assertThat(registry.lookup("a@tau.ac.il").get().kind()).isEqualTo(MemberKind.STAFF);
        assertThat(registry.lookup("a@technion.ac.il").get().kind()).isEqualTo(MemberKind.STAFF);
    }

    @Test
    void allUniversityKeysReturnsDistinctLowercaseSlugs() {
        java.util.List<String> keys = registry.allUniversityKeys();
        assertThat(keys).contains("bgu", "tau", "huji", "technion", "weizmann", "biu", "haifa", "runi", "openu", "ariel");
        assertThat(keys).doesNotHaveDuplicates();
        assertThat(keys).allMatch(k -> k.equals(k.toLowerCase()));
    }
}
