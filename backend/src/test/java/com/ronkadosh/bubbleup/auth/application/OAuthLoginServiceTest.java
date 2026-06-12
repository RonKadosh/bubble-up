package com.ronkadosh.bubbleup.auth.application;

import com.ronkadosh.bubbleup.auth.api.dto.AuthResponse;
import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginServiceTest {

    @Test
    void new_academic_google_signup_is_verified_immediately() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtService jwtService = mock(JwtService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        UniversityEmailRegistry universityRegistry = mock(UniversityEmailRegistry.class);

        OAuthLoginService service = new OAuthLoginService(
                userRepository, jwtService, refreshTokenService, universityRegistry);

        when(universityRegistry.lookup("student@post.bgu.ac.il"))
                .thenReturn(Optional.of(new UniversityEmailRegistry.Match(
                        "bgu",
                        "Ben-Gurion University of the Negev",
                        UniversityEmailRegistry.MemberKind.STUDENT,
                        "post.bgu.ac.il"
                )));
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@post.bgu.ac.il")).thenReturn(Optional.empty());
        when(jwtService.generateToken(any(), eq("student@post.bgu.ac.il"), eq(UserRole.STUDENT))).thenReturn("access-token");
        when(refreshTokenService.issue(any(), isNull()))
                .thenReturn(new RefreshTokenService.IssuedRefresh("refresh-token", null));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = service.loginOrRegister(
                "google-sub-1",
                "student@post.bgu.ac.il",
                true,
                "Student User"
        );

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());

        assertThat(savedUser.getValue().isEmailVerified()).isTrue();
        assertThat(response.emailVerified()).isTrue();
        assertThat(response.email()).isEqualTo("student@post.bgu.ac.il");
        assertThat(response.role()).isEqualTo("STUDENT");
    }

    @Test
    void existing_unverified_user_stays_pending_on_repeated_google_sign_in() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtService jwtService = mock(JwtService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        UniversityEmailRegistry universityRegistry = mock(UniversityEmailRegistry.class);

        OAuthLoginService service = new OAuthLoginService(
                userRepository, jwtService, refreshTokenService, universityRegistry);

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("student@post.bgu.ac.il")
                .emailVerified(false)
                .role(UserRole.STUDENT)
                .displayName("Existing User")
                .build();

        when(universityRegistry.lookup("student@post.bgu.ac.il"))
                .thenReturn(Optional.of(new UniversityEmailRegistry.Match(
                        "bgu",
                        "Ben-Gurion University of the Negev",
                        UniversityEmailRegistry.MemberKind.STUDENT,
                        "post.bgu.ac.il"
                )));
        when(userRepository.findByGoogleSub("google-sub-2")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@post.bgu.ac.il")).thenReturn(Optional.of(existingUser));
        when(jwtService.generateToken(existingUser.getId(), existingUser.getEmail(), existingUser.getRole())).thenReturn("access-token");
        when(refreshTokenService.issue(existingUser.getId(), null))
                .thenReturn(new RefreshTokenService.IssuedRefresh("refresh-token", null));

        AuthResponse response = service.loginOrRegister(
                "google-sub-2",
                "student@post.bgu.ac.il",
                true,
                "Existing User"
        );

        assertThat(existingUser.getGoogleSub()).isEqualTo("google-sub-2");
        assertThat(existingUser.isEmailVerified()).isFalse();
        assertThat(response.emailVerified()).isFalse();
    }

    @Test
    void non_academic_google_email_is_rejected() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtService jwtService = mock(JwtService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        UniversityEmailRegistry universityRegistry = mock(UniversityEmailRegistry.class);

        OAuthLoginService service = new OAuthLoginService(
                userRepository, jwtService, refreshTokenService, universityRegistry);

        when(universityRegistry.lookup("student@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loginOrRegister(
                "google-sub-3",
                "student@gmail.com",
                true,
                "Student User"
        ))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_ACADEMIC_EMAIL);
    }

    @Test
    void unknownAcIlGoogleEmailIsStillAcceptedAsAcademic() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtService jwtService = mock(JwtService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        UniversityEmailRegistry universityRegistry = mock(UniversityEmailRegistry.class);

        OAuthLoginService service = new OAuthLoginService(
                userRepository, jwtService, refreshTokenService, universityRegistry);

        when(universityRegistry.lookup("student@unknown-college.ac.il"))
                .thenReturn(Optional.of(new UniversityEmailRegistry.Match(
                        "unknown-college",
                        "Academic institution (unknown-college.ac.il)",
                        UniversityEmailRegistry.MemberKind.UNKNOWN,
                        "unknown-college.ac.il"
                )));
        when(userRepository.findByGoogleSub("google-sub-4")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@unknown-college.ac.il")).thenReturn(Optional.empty());
        when(jwtService.generateToken(any(), eq("student@unknown-college.ac.il"), eq(UserRole.STUDENT))).thenReturn("access-token");
        when(refreshTokenService.issue(any(), isNull()))
                .thenReturn(new RefreshTokenService.IssuedRefresh("refresh-token", null));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = service.loginOrRegister(
                "google-sub-4",
                "student@unknown-college.ac.il",
                true,
                "Student User"
        );

        assertThat(response.email()).isEqualTo("student@unknown-college.ac.il");
        assertThat(response.emailVerified()).isTrue();
        assertThat(response.role()).isEqualTo("STUDENT");
    }
}
