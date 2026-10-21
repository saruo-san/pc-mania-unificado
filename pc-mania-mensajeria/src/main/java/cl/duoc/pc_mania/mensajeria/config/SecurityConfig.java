package cl.duoc.pc_mania.mensajeria.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;

@Configuration
public class SecurityConfig {

    private final String clientIssuer;
    private final String clientId;
    private final String adminIssuer;
    private final String adminClientId;
    private final String writeScope;
    private final String adminScope;

    public SecurityConfig(
            @Value("${security.cognito.client.issuer}") String clientIssuer,
            @Value("${security.cognito.client.client-id}") String clientId,
            @Value("${security.cognito.admin.issuer}") String adminIssuer,
            @Value("${security.cognito.admin.client-id}") String adminClientId,
            @Value("${security.messaging.write-scope}") String writeScope,
            @Value("${security.messaging.admin-scope}") String adminScope) {
        this.clientIssuer = clientIssuer;
        this.clientId = clientId;
        this.adminIssuer = adminIssuer;
        this.adminClientId = adminClientId;
        this.writeScope = writeScope;
        this.adminScope = adminScope;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/messaging/events")
                        .hasAuthority("SCOPE_" + writeScope)
                        .requestMatchers("/api/messaging/admin/**")
                        .hasAuthority("SCOPE_" + adminScope)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(authenticationManagerResolver()));
        return http.build();
    }

    @Bean
    AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver() {
        Map<String, AuthenticationManager> managers = Map.of(
                clientIssuer, authenticationManager(clientIssuer, clientId),
                adminIssuer, authenticationManager(adminIssuer, adminClientId));
        return request -> new org.springframework.security.oauth2.server.resource.authentication
                .JwtIssuerAuthenticationManagerResolver((String issuer) -> managers.get(issuer))
                .resolve(request);
    }

    private AuthenticationManager authenticationManager(String issuer, String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(issuer + "/.well-known/jwks.json")
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> tokenUseValidator =
                new JwtClaimValidator<>("token_use", "access"::equals);
        OAuth2TokenValidator<Jwt> clientIdValidator =
                new JwtClaimValidator<>("client_id", clientId::equals);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator, tokenUseValidator, clientIdValidator));

        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(jwtAuthenticationConverter());
        return provider::authenticate;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthorityPrefix("SCOPE_");
        authorities.setAuthoritiesClaimName("scope");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
