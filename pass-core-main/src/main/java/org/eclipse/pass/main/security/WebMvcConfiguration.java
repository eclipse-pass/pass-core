package org.eclipse.pass.main.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Configure request handling for the app and swagger.
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${pass.app-location}")
    private String appLocation;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Ensure that /app/ goes to the index.html
        registry.addViewController("/app/").setViewName("forward:/app/index.html");

        // Some web browsers will make requests to /favicon.ico automatically which can
        /// mess up the SAML login process
        registry.addViewController("/favicon.ico").setViewName("forward:/app/favicon.ico");

        // Redirect / to the swagger UI. Must redirect so resources can be loaded.
        registry.addViewController("/").setViewName("redirect:/app/index.html");

        // Make /swagger/ accessible as the swagger ui
        registry.addViewController("/swagger/").setViewName("forward:/swagger/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Ensure that all requests under /app/ that are not found resolve to /app/index.html.
        // This lets the UI handle routes it controls.

        String index_html = appLocation + "index.html";

        registry.addResourceHandler("/app/**")
            .addResourceLocations(appLocation)
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String path, Resource base) throws IOException {
                    Resource res = base.createRelative(path);
                    return res.isReadable() ? res : resourceLoader.getResource(index_html);
                }
            });
    }
}
