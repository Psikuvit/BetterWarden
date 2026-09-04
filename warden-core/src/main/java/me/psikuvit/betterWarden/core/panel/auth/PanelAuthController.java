package me.psikuvit.betterWarden.core.panel.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import me.psikuvit.betterWarden.core.model.PanelUser;
import me.psikuvit.betterWarden.core.repo.PanelUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
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

    public record UserView(String username, String role, boolean mustChangePassword) {
        static UserView of(PanelUser u) {
            return new UserView(u.getUsername(), u.getRole().name(), u.isMustChangePassword());
        }
    }

    private final AuthenticationManager authenticationManager;
    private final PanelUserRepository users;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public PanelAuthController(AuthenticationManager authenticationManager, PanelUserRepository users) {
        this.authenticationManager = authenticationManager;
        this.users = users;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        Authentication authRequest = UsernamePasswordAuthenticationToken.unauthenticated(req.username(), req.password());
        Authentication result;
        try {
            result = authenticationManager.authenticate(authRequest);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(result);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return users.findByUsernameIgnoreCase(req.username())
                .map(u -> ResponseEntity.ok(UserView.of(u)))
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
    public ResponseEntity<UserView> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        return users.findByUsernameIgnoreCase(authentication.getName())
                .map(u -> ResponseEntity.ok(UserView.of(u)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /** No-op endpoint - GETting it (permitAll) is enough to make Spring Security issue the XSRF-TOKEN cookie for the SPA to read. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }
}
