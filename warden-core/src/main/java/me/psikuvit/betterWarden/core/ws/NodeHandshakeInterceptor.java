package me.psikuvit.betterWarden.core.ws;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.network.NodeAuthInterceptor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/** Same shared node-token as the REST API (NodeAuthInterceptor) - checked once, at the WS handshake. */
public class NodeHandshakeInterceptor implements HandshakeInterceptor {

    private final CoreConfig config;

    public NodeHandshakeInterceptor(CoreConfig config) {
        this.config = config;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        String presented = request.getHeaders().getFirst(NodeAuthInterceptor.TOKEN_HEADER);
        String expected = config.getSecurity().getNodeToken();
        if (expected == null || expected.isBlank() || presented == null || !presented.equals(expected)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, Exception exception) {
    }
}
