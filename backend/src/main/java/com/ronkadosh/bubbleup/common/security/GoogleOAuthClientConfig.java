package com.ronkadosh.bubbleup.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class GoogleOAuthClientConfig {

    @Bean
    @Conditional(GoogleOAuthConfiguredCondition.class)
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_OAUTH_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_OAUTH_CLIENT_SECRET:}") String clientSecret) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("openid", "email", "profile")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();

        return new InMemoryClientRegistrationRepository(google);
    }

    public static class GoogleOAuthConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String clientId = context.getEnvironment().getProperty("GOOGLE_OAUTH_CLIENT_ID");
            String clientSecret = context.getEnvironment().getProperty("GOOGLE_OAUTH_CLIENT_SECRET");
            return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
        }
    }
}
