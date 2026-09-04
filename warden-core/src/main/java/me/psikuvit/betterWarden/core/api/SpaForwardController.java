package me.psikuvit.betterWarden.core.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * React Router routes (e.g. /dash, /players/123) don't exist as real server resources - only
 * clicking a link inside the already-loaded app works without this. A direct navigation or a
 * page refresh has to hit the server first, so anything that isn't /api/**, /ws/**, /health, or
 * an actual static file (has a dot: .js, .css, .svg, ...) gets forwarded to index.html instead,
 * letting React Router take over client-side. Static files with a dot never match this pattern,
 * so they still fall through to Spring Boot's own classpath:/static/ handling untouched.
 */
@Controller
public class SpaForwardController {

    private static final String PATTERN = "/{path:^(?!api|ws|health)[^.]*}";

    @GetMapping(PATTERN)
    public String forwardRoot() {
        return "forward:/index.html";
    }

    @GetMapping(PATTERN + "/**")
    public String forwardNested() {
        return "forward:/index.html";
    }
}
