package me.psikuvit.betterWarden.core.panel.branding;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/spec/04-PANEL.txt §5 DESIGN: server logo + accent colour, both driven by config.yml.
 * permitAll (see SecurityConfig) - the login page and public landing page need this before
 * anyone is authenticated, same reasoning as /api/panel/auth/csrf.
 */
@RestController
public class BrandingController {

    public record BrandingResponse(String serverName, String logoUrl, String accentColor) {
    }

    private final CoreConfig config;

    public BrandingController(CoreConfig config) {
        this.config = config;
    }

    @GetMapping("/api/panel/branding")
    public BrandingResponse branding() {
        return new BrandingResponse(config.getServerName(), config.getBranding().getLogoUrl(),
                config.getBranding().getAccentColor());
    }
}
