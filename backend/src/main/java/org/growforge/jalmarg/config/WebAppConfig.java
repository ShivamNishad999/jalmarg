package org.growforge.jalmarg.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optionally serves the built web app (frontend/dist) from the same server, so one
 * HTTPS address (e.g. CloudFront in front of EC2) gives both the website and /api.
 * Set WEB_DIR to the folder that holds index.html. Unknown non-API paths fall back to
 * index.html so React Router links like /report work on refresh.
 * API controllers always win: they are matched before this catch-all resource handler.
 */
@Configuration
public class WebAppConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebAppConfig.class);

    private final String webDir;

    public WebAppConfig(@Value("${jalmarg.web.dir:}") String webDir) {
        this.webDir = webDir == null ? "" : webDir.trim();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (webDir.isEmpty()) return;
        Path dir = Path.of(webDir).toAbsolutePath().normalize();
        if (!Files.isRegularFile(dir.resolve("index.html"))) {
            log.warn("WEB_DIR {} has no index.html; web app not served", dir);
            return;
        }
        log.info("Serving web app from {}", dir);
        registry.addResourceHandler("/**")
                .addResourceLocations(dir.toUri().toString())
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        // "/" (and any folder path) is the app itself
                        if (path.isEmpty() || path.endsWith("/")) return location.createRelative("index.html");
                        Resource r = location.createRelative(path);
                        if (r.exists() && r.isReadable() && r.getFile().isFile()) return r;
                        // never answer API or actuator calls with the web page
                        if (path.startsWith("api/") || path.startsWith("actuator")) return null;
                        // files with an extension that do not exist are real 404s
                        String last = path.substring(path.lastIndexOf('/') + 1);
                        if (last.contains(".")) return null;
                        return location.createRelative("index.html");
                    }
                });
    }
}
