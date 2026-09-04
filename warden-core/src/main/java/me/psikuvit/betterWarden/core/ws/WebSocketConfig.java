package me.psikuvit.betterWarden.core.ws;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NodeWebSocketHandler handler;
    private final CoreConfig config;

    public WebSocketConfig(NodeWebSocketHandler handler, CoreConfig config) {
        this.handler = handler;
        this.config = config;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/nodes")
                .addInterceptors(new NodeHandshakeInterceptor(config));
    }
}
