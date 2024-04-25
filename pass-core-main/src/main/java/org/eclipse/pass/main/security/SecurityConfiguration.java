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

import java.util.List;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.authentication.OpenSaml4AuthenticationRequestResolver;
import org.springframework.security.saml2.provider.service.web.authentication.Saml2AuthenticationRequestResolver;
import org.springframework.security.saml2.provider.service.web.authentication.Saml2WebSsoAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
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

    @Value("${pass.login-processing-path}")
    private String loginProcessingPath;

    @Value("${pass.csp}")
    private String contentSecurityPolicy;

    @Value("${pass.logout-delete-cookies}")
    private List<String> logoutDeleteCookies;

    /**
     * Return a configured Spring Security filter chain
     *
     * @param http HttpSecurity
     * @return filter chain
     * @throws Exception on error
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Disable unused functionality
        http.csrf(CsrfConfigurer::disable);
        http.formLogin(FormLoginConfigurer::disable);
        http.anonymous(AnonymousConfigurer::disable);

        // Set Content Security Policy header only for /app/
        ContentSecurityPolicyHeaderWriter cspHeaderWriter = new ContentSecurityPolicyHeaderWriter();
        cspHeaderWriter.setPolicyDirectives(contentSecurityPolicy);

        DelegatingRequestMatcherHeaderWriter appCspHeaderWriter = new DelegatingRequestMatcherHeaderWriter(
                new AntPathRequestMatcher("/app/**"), cspHeaderWriter);

        http.headers(h -> h.addHeaderWriter(appCspHeaderWriter));

        // Ensure that favicon.ico requests are public so they do not interfere with SAML login.
        // Make /error public so problems that occur before authentication are not hidden.
        // All other requests must be authorized.
        http.authorizeHttpRequests((authorizeHttpRequests) ->
            authorizeHttpRequests.requestMatchers("/error", "/favicon.ico", "/app/favicon.ico").permitAll().
                anyRequest().authenticated());

        // Prevent a continue parameter from being added after login
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        http.requestCache(rc -> rc.requestCache(requestCache));

        // Prevent WWW-Authenticate header causing a browser popup after a session timeout
        http.httpBasic((c -> c.authenticationEntryPoint(
                (request, response, authException) ->  response.sendError(HttpStatus.UNAUTHORIZED.value(),
                        HttpStatus.UNAUTHORIZED.getReasonPhrase()))));

        http.saml2Login(s -> s.defaultSuccessUrl(defaultLoginSuccessUrl).
                        loginProcessingUrl(loginProcessingPath));

        http.saml2Metadata(Customizer.withDefaults());
        http.saml2Logout(Customizer.withDefaults());

        // Delete specified cookies on logout.
        // Each cookie is specified as a name and path separated by whitespace.
        Cookie[] cookies = logoutDeleteCookies.stream().map(s -> {
            String[] parts = s.trim().split("\\s+");

            Cookie c = new Cookie(parts[0], null);
            c.setPath(parts[1]);
            c.setMaxAge(0);
            return c;
        }).toArray(Cookie[]::new);

        CookieClearingLogoutHandler logoutHandler = new CookieClearingLogoutHandler(cookies);
        http.logout(l -> l.logoutSuccessUrl(logoutSuccessUrl).addLogoutHandler(logoutHandler));

        // Map SAML user to PASS user
        http.addFilterAfter(passAuthFilter, Saml2WebSsoAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Ensure that ForceAuthN is set to true.
     *
     * @param registrations
     * @return auth request resolver
     */
    @Bean
    Saml2AuthenticationRequestResolver authenticationRequestResolver(RelyingPartyRegistrationRepository registrations) {
        RelyingPartyRegistrationResolver registrationResolver =
                new DefaultRelyingPartyRegistrationResolver(registrations);
        OpenSaml4AuthenticationRequestResolver authenticationRequestResolver =
                new OpenSaml4AuthenticationRequestResolver(registrationResolver);
        authenticationRequestResolver.setAuthnRequestCustomizer((context) -> context
                .getAuthnRequest().setForceAuthn(true));
        return authenticationRequestResolver;
    }
}
