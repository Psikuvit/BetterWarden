package me.psikuvit.betterWarden.core.panel.setup;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.psikuvit.betterWarden.core.model.PanelRole;
import me.psikuvit.betterWarden.core.model.PanelUser;
import me.psikuvit.betterWarden.core.panel.auth.PanelUserView;
import me.psikuvit.betterWarden.core.panel.auth.SessionAuthenticator;
import me.psikuvit.betterWarden.core.repo.PanelUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Entirely permitAll (see SecurityConfig) - by definition nobody can be logged in yet the first time this runs. */
@RestController
@RequestMapping("/api/panel/setup")
public class SetupController {

    public record CompleteRequest(String setupCode, String username, String password) {
    }

    private final PanelUserRepository users;
    private final SetupCodeService setupCode;
    private final PasswordEncoder encoder;
    private final SessionAuthenticator sessionAuthenticator;

    public SetupController(PanelUserRepository users, SetupCodeService setupCode,
                            PasswordEncoder encoder, SessionAuthenticator sessionAuthenticator) {
        this.users = users;
        this.setupCode = setupCode;
        this.encoder = encoder;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    public record VerifyRequest(String setupCode) {
    }

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("needed", users.count() == 0);
    }

    /** Lets step 1 of the wizard confirm the code before asking for account details in step 2 - doesn't consume it, /complete still re-checks. */
    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest req) {
        if (!setupCode.verify(req.setupCode())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired setup code"));
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/complete")
    public ResponseEntity<?> complete(@RequestBody CompleteRequest req, HttpServletRequest request, HttpServletResponse response) {
        if (users.count() > 0) {
            return ResponseEntity.status(409).body(Map.of("error", "Setup was already completed"));
        }
        if (!setupCode.verify(req.setupCode())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired setup code"));
        }
        if (req.username() == null || req.username().isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "Username is required"));
        }
        if (req.password() == null || req.password().length() < 8) {
            return ResponseEntity.status(400).body(Map.of("error", "Password must be at least 8 characters"));
        }

        PanelUser owner = new PanelUser();
        owner.setUsername(req.username());
        owner.setPasswordHash(encoder.encode(req.password()));
        owner.setRole(PanelRole.OWNER);
        owner.setCreatedAt(Instant.now());
        owner.setMustChangePassword(false);
        users.save(owner);
        setupCode.invalidate();

        // Straight into a session - no reason to make them log in again immediately after.
        sessionAuthenticator.authenticateAndStartSession(req.username(), req.password(), request, response);

        return ResponseEntity.ok(PanelUserView.of(owner));
    }
}
