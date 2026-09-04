package me.psikuvit.betterWarden.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * React Router routes (e.g. /dash, /players/123) don't exist as real server resources - a direct
 * navigation or refresh needs the server to serve index.html for them instead of 404ing, so React
 * Router can take over client-side.
 *
 * First attempt at this (SpaForwardController, a @GetMapping regex matching "no dot in the first
 * path segment") had a real bug, caught only by actually loading the page in a browser rather than
 * curling individual known paths: the regex only excluded the FIRST segment from the fallback, so
 * /assets/index-xyz.js (first segment "assets" has no dot) got swallowed by the /** suffix and
 * forwarded to index.html too - the browser loaded HTML where it expected JS/CSS and failed with
 * a MIME-type error, not a 404.
 *
 * This is the standard, documented Spring approach instead: try to resolve the actual static
 * resource first (correct for every real file, at any depth, regardless of extension), and only
 * fall back to index.html when nothing on disk matches - i.e. an actual client-side route.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
