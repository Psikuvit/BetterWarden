package me.psikuvit.betterWarden.core.panel.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import me.psikuvit.betterWarden.core.repo.PanelUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** /api/panel/auth/login and /csrf are permitAll (see SecurityConfig); /me and /logout require an existing session. */
@RestController
@RequestMapping("/api/panel/auth")
public class PanelAuthController {

    public record LoginRequest(String username, String password) {
    }

    private final SessionAuthenticator sessionAuthenticator;
    private final PanelUserRepository users;

    public PanelAuthController(SessionAuthenticator sessionAuthenticator, PanelUserRepository users) {
        this.sessionAuthenticator = sessionAuthenticator;
        this.users = users;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        try {
            sessionAuthenticator.authenticateAndStartSession(req.username(), req.password(), request, response);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }

        return users.findByUsernameIgnoreCase(req.username())
                .map(u -> ResponseEntity.ok(PanelUserView.of(u)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<PanelUserView> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        return users.findByUsernameIgnoreCase(authentication.getName())
                .map(u -> ResponseEntity.ok(PanelUserView.of(u)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /**
     * GETting this (permitAll) is what makes Spring Security issue the XSRF-TOKEN cookie for the
     * SPA to read - but only because the CsrfToken parameter forces it to actually resolve.
     * Spring Security 6.4+ made the token lazy/deferred by default: a handler that never touches
     * it (this used to just return 204 with no parameter at all) never triggers
     * CookieCsrfTokenRepository.saveToken(), so no cookie is ever written. Caught this by
     * actually curling the endpoint and finding no Set-Cookie header, not from reading a changelog.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }
}
