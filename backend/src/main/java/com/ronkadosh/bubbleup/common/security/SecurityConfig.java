package com.ronkadosh.bubbleup.common.security;

import com.ronkadosh.bubbleup.auth.api.OAuth2LoginFailureHandler;
import com.ronkadosh.bubbleup.auth.api.OAuth2LoginSuccessHandler;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.config.CorsProperties;
import com.ronkadosh.bubbleup.common.context.RequestContextFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final EmailVerificationGateFilter emailVerificationGateFilter;
    private final CorsProperties corsProperties;
    private final OAuth2LoginSuccessHandler oauthSuccessHandler;
    private final OAuth2LoginFailureHandler oauthFailureHandler;
    /**
     * Resolved at startup. Empty in environments where Google OAuth2 hasn't
     * been configured (CI without secrets, fresh local clone before .env is
     * filled in). When absent, the OAuth login chain is skipped so the rest
     * of the app still boots.
     */
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          EmailVerificationGateFilter emailVerificationGateFilter,
                          CorsProperties corsProperties,
                          OAuth2LoginSuccessHandler oauthSuccessHandler,
                          OAuth2LoginFailureHandler oauthFailureHandler,
                          ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.jwtFilter = jwtFilter;
        this.emailVerificationGateFilter = emailVerificationGateFilter;
        this.corsProperties = corsProperties;
        this.oauthSuccessHandler = oauthSuccessHandler;
        this.oauthFailureHandler = oauthFailureHandler;
        this.clientRegistrations = clientRegistrations;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        HttpSecurity chain = http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ApiPaths.AUTH_BASE + "/**").permitAll()
                        // /ws handshake is public; JWT is enforced at STOMP CONNECT by StompAuthChannelInterceptor.
                        .requestMatchers("/ws/**").permitAll()
                        // Avatar stream is public so <img src=...> works without an Authorization header.
                        // The privacy boundary is on /users/{id}/profile (co-member gated).
                        .requestMatchers(HttpMethod.GET, ApiPaths.USERS_BASE + "/*/avatar").permitAll()
                        // OAuth2 endpoints (Spring Security auto-publishes these).
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .anyRequest().authenticated()
                )
                // Missing/invalid JWT must return 401 (not the Spring default 403). The frontend
                // axios interceptor refreshes only on 401; 403 is reserved for "authenticated but
                // not allowed" (NOT_GROUP_MEMBER, etc.). See backend/CLAUDE.md.
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(emailVerificationGateFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(new RequestContextFilter(), JwtAuthenticationFilter.class);

        // Only wire OAuth2 login when a ClientRegistrationRepository exists —
        // i.e. when GOOGLE_OAUTH_CLIENT_ID + _SECRET are set. This lets dev
        // environments boot without credentials configured.
        if (clientRegistrations.getIfAvailable() != null) {
            chain.oauth2Login(oauth -> oauth
                    .successHandler(oauthSuccessHandler)
                    .failureHandler(oauthFailureHandler)
            );
        }

        return chain.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(corsProperties.allowedOrigins().split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
