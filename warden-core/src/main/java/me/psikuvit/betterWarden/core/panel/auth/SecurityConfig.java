package me.psikuvit.betterWarden.core.panel.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(PanelUserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers("/api/v1/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/**", "/ws/nodes", "/health").permitAll()
                        .requestMatchers("/api/panel/auth/login", "/api/panel/auth/csrf").permitAll()
                        .requestMatchers("/api/panel/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable()); // custom logout endpoint - see PanelAuthController

        return http.build();
    }
}
