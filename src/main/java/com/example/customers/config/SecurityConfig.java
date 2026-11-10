package com.example.customers.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Set<String> ROLES = Set.of("USER", "ADMIN");

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter) throws Exception {
        http.csrf(csrf -> csrf.disable())     // stateless API authenticated by bearer token
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.GET, "/customers", "/customers/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/customers").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/customers/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/customers/*").hasRole("ADMIN")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }

    /** Maps Keycloak's realm_access.roles claim to ROLE_USER / ROLE_ADMIN. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            if (realm != null && realm.get("roles") instanceof Collection<?> roles) {
                for (Object role : roles) {
                    if (ROLES.contains(String.valueOf(role))) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                }
            }
            return authorities;
        });
        return converter;
    }
}
