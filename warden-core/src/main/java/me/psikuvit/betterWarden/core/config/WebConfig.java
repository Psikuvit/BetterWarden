package me.psikuvit.betterWarden.core.config;

import me.psikuvit.betterWarden.core.network.NodeAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final NodeAuthInterceptor nodeAuth;

    public WebConfig(NodeAuthInterceptor nodeAuth) {
        this.nodeAuth = nodeAuth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(nodeAuth).addPathPatterns("/api/v1/**");
    }
}
