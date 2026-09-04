package me.psikuvit.betterWarden.core.network;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.psikuvit.betterWarden.core.config.CoreConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Guards /api/v1/nodes/** with the shared warden.security.node-token - see CoreHandshake for how a CLIENT learns it. No per-node identity yet, just "is this the right shared secret" (documented gap - see PLAN.md). */
@Component
public class NodeAuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "X-Node-Token";

    private final CoreConfig config;

    public NodeAuthInterceptor(CoreConfig config) {
        this.config = config;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String presented = request.getHeader(TOKEN_HEADER);
        String expected = config.getSecurity().getNodeToken();
        if (expected == null || expected.isBlank() || presented == null || !presented.equals(expected)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        return true;
    }
}
