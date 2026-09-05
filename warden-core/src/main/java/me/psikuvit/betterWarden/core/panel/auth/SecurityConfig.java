package me.psikuvit.betterWarden.core.panel.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.http.HttpStatus;

/**
 * Two completely separate auth layers coexist here on purpose:
 *  - /api/v1/** and /ws/nodes are node-to-node traffic, guarded by NodeAuthInterceptor /
 *    NodeHandshakeInterceptor (a shared token header) - NOT Spring Security. Both are permitAll
 *    here and CSRF-exempt: a machine client presenting X-Node-Token has no CSRF token to send,
 *    and doesn't need one (CSRF is a browser-session-cookie problem, not a header-token one).
 *  - /api/panel/** is the human staff panel - session-cookie auth via Spring Security, CSRF
 *    protection kept ON (spec §6 requires it), using the cookie-token pattern so a plain fetch()
 *    from React can read the token and echo it back.
 * Everything else (the SPA shell itself - index.html, JS/CSS bundles, client-side routes like
 * /dash) is permitAll: the app has to load before it can even show a login form. The API calls
 * that page then makes are what's actually gated.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** PanelRole (docs/spec/04-PANEL.txt §3) is a strict ladder - OWNER outranks ADMIN outranks
     * MODERATOR outranks VIEWER - so @PreAuthorize("hasRole('MODERATOR')") should also admit
     * ADMIN/OWNER, not just an exact MODERATOR match. Without this bean hasRole() only matches
     * the literal granted authority. */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_OWNER > ROLE_ADMIN
                ROLE_ADMIN > ROLE_MODERATOR
                ROLE_MODERATOR > ROLE_VIEWER
                """);
    }

    @Bean
    public AuthenticationManager authenticationManager(PanelUserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers("/api/v1/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/**", "/ws/nodes", "/health").permitAll()
                        .requestMatchers("/api/panel/auth/login", "/api/panel/auth/csrf").permitAll()
                        .requestMatchers("/api/panel/setup/**").permitAll()
                        .requestMatchers("/api/panel/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable) // custom logout endpoint - see PanelAuthController
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }
}
