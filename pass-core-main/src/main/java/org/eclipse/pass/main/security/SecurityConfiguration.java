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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;

/**
 * Configure Spring Security filter chain.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    @Autowired
    private ShibAuthenticationFilter shibAuthFilter;

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
        http.formLogin(FormLoginConfigurer::disable);
        http.logout(LogoutConfigurer::disable);
        http.anonymous(AnonymousConfigurer::disable);
        http.exceptionHandling(ExceptionHandlingConfigurer::disable);
        http.headers(HeadersConfigurer::disable);
        http.requestCache(RequestCacheConfigurer::disable);
        // This is needed because SecurityContextHolderAwareRequestFilter uses
        // HttpSessionSecurityContextRepository by default, and we don't want to use the session for saving
        // SecurityContext.
        http.servletApi((servletApi) ->
            servletApi.addObjectPostProcessor(new ObjectPostProcessor<SecurityContextHolderAwareRequestFilter>() {
                @Override
                public <O extends SecurityContextHolderAwareRequestFilter> O postProcess(O filter) {
                    filter.setSecurityContextRepository(new RequestAttributeSecurityContextRepository());
                    return filter;
                }
            }));
        http.authorizeHttpRequests((authorizeHttpRequests) ->
            authorizeHttpRequests.anyRequest().authenticated());
        // Note that default basic auth is stateless
        http.httpBasic(Customizer.withDefaults());
        http.addFilterBefore(shibAuthFilter, BasicAuthenticationFilter.class);

        return http.build();
    }
}
