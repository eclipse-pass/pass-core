/*
 * Copyright 2023 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.pass.main.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.saml2.provider.service.web.authentication.Saml2WebSsoAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;


/**
 * Configure Spring Security filter chain.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    @Autowired
    private PassAuthenticationFilter passAuthFilter;

    @Value("${pass.logout-success-url}")
    private String logoutSuccessUrl;

    @Value("${pass.default-login-success-url}")
    private String defaultLoginSuccessUrl;

    @Value("${pass.csp}")
    private String contentSecurityPolicy;

    /**
     * Return a configured Spring Security filter chain
     *
     * @param http HttpSecurity
     * @return filter chain
     * @throws Exception on error
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(CsrfConfigurer::disable);

        // Set Content Security Policy header only for /app/
        ContentSecurityPolicyHeaderWriter cspHeaderWriter = new ContentSecurityPolicyHeaderWriter();
        cspHeaderWriter.setPolicyDirectives(contentSecurityPolicy);

        DelegatingRequestMatcherHeaderWriter appCspHeaderWriter = new DelegatingRequestMatcherHeaderWriter(
                new AntPathRequestMatcher("/app/**"), cspHeaderWriter);

        http.headers(h -> h.addHeaderWriter(appCspHeaderWriter));

        // Ensure that favicon.ico requests are public so they do not interfere with SAML login
        // All other requests must be authorized.
        http.authorizeHttpRequests((authorizeHttpRequests) ->
            authorizeHttpRequests.requestMatchers("/favicon.ico", "/app/favicon.ico").permitAll().
                anyRequest().authenticated());

        // Prevent a continue parameter from being added after login
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        http.requestCache(rc -> rc.requestCache(requestCache));

        http.httpBasic(Customizer.withDefaults());

        http.saml2Login(s -> s.defaultSuccessUrl(defaultLoginSuccessUrl));
        http.saml2Metadata(Customizer.withDefaults());

        // Logout clears the SP session, but does not hit the IDP
        http.logout(l -> l.logoutSuccessUrl(logoutSuccessUrl));

        http.addFilterAfter(passAuthFilter, Saml2WebSsoAuthenticationFilter.class);

        return http.build();
    }
}
