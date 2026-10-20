package cl.duoc.pc_mania.app.config;

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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;

@Configuration
public class SecurityConfig {

    private final String clienteIssuer;
    private final String clienteClientId;
    private final String adminIssuer;
    private final String adminClientId;

    public SecurityConfig(
            @Value("${security.cognito.cliente.issuer}") String clienteIssuer,
            @Value("${security.cognito.cliente.client-id}") String clienteClientId,
            @Value("${security.cognito.admin.issuer}") String adminIssuer,
            @Value("${security.cognito.admin.client-id}") String adminClientId) {
        this.clienteIssuer = clienteIssuer;
        this.clienteClientId = clienteClientId;
        this.adminIssuer = adminIssuer;
        this.adminClientId = adminClientId;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/productos/**")
                        .hasAnyAuthority("SCOPE_pcmania-api/read", "SCOPE_pcmania-api/write")
                        .requestMatchers(HttpMethod.POST, "/api/productos/**")
                        .hasAuthority("SCOPE_pcmania-api/write")
                        .requestMatchers(HttpMethod.PUT, "/api/productos/**")
                        .hasAuthority("SCOPE_pcmania-api/write")
                        .requestMatchers(HttpMethod.DELETE, "/api/productos/**")
                        .hasAuthority("SCOPE_pcmania-api/write")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(authenticationManagerResolver()));
        return http.build();
    }

    @Bean
    AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver() {
        Map<String, AuthenticationManager> managers = Map.of(
                clienteIssuer, authenticationManager(clienteIssuer, clienteClientId),
                adminIssuer, authenticationManager(adminIssuer, adminClientId));

        return request -> new org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver(
                (String issuer) -> managers.get(issuer)).resolve(request);
    }

    private AuthenticationManager authenticationManager(String issuer, String clientId) {
        String jwksUri = issuer + "/.well-known/jwks.json";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> tokenUseValidator = new JwtClaimValidator<>("token_use", "access"::equals);
        OAuth2TokenValidator<Jwt> clientIdValidator = new JwtClaimValidator<>("client_id", clientId::equals);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, tokenUseValidator, clientIdValidator));

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