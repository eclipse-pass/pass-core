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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.yahoo.elide.RefreshableElide;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.PassClientResult;
import org.eclipse.pass.object.PassClientSelector;
import org.eclipse.pass.object.RSQL;
import org.eclipse.pass.object.model.PassEntity;
import org.eclipse.pass.object.model.User;
import org.eclipse.pass.object.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter responsible for mapping a SAML user to a PASS user. The PASS user is
 * created if it does not exist and otherwise updated to reflect the information
 * provided by the IDP. The PASS user name becomes the name of the Principal.
 * <p>
 * If the request has not been authenticated through SAML, the mapping step is skipped.
 * In any case, the request is passed down the chain.
 * <p>
 * A cache of maximum size pass.auth.max-cache-size of recent authentications is
 * maintained. It is cleared every pass.auth.cache-duration minutes.
 */
@Component
public class PassAuthenticationFilter extends OncePerRequestFilter {
    static final String EMPLOYEE_ID_TYPE = "employeeid";
    static final String UNIQUE_ID_TYPE = "unique-id";
    static final String INSTITUIONAL_ID_TYPE = "eppn";

    private static final Logger LOG = LoggerFactory.getLogger(PassAuthenticationFilter.class);

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
        SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    private final RefreshableElide elide;

    private PassAuthenticationFilterConfiguration config;

    /**
     * Attributes about a user provided by the IDP.
     */
    public enum Attribute {
        /**
         * Display name
         */
        DISPLAY_NAME,

        /**
         * Email address
         */
        EMAIL,

        /**
         * eduPersonPrincipalName
         */
        EPPN,

        /**
         * Given name
         */
        GIVEN_NAME,

        /**
         * Surname
         */
        SURNAME,

        /**
         * ID assigned by employer
         */
        EMPLOYEE_ID,

        /**
         * Unique id
         */
        UNIQUE_ID,

        /**
         * Affiliation identifiers separated by a semicolon.
         */
        SCOPED_AFFILIATION;
    }

    /**
     * @param refreshableElide RefreshableElide
     * @param config PassAuthenticationFilterConfiguration
     */
    public PassAuthenticationFilter(RefreshableElide refreshableElide, PassAuthenticationFilterConfiguration config) {
        this.config = config;
        this.elide = refreshableElide;
    }

    // Do authentication and return Authentication object representing success.
    // Throw AuthenticationException if there is trouble with the user credentials
    private Authentication authenticate(Saml2AuthenticatedPrincipal principal)
            throws AuthenticationException, IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Principal: " + principal.getName());

            principal.getAttributes().forEach((key, values) -> {
                LOG.debug(key + ": " + values);
            });
        }

        User user = parse_user(principal.getAttributes());
        create_or_update_pass_user(user);

        return new PassAuthentication(user);
    }

    // Ensure that only one user is created
    private synchronized void create_or_update_pass_user(User user) throws IOException {
        try (PassClient pass_client = PassClient.newInstance(elide)) {
            User pass_user = find_pass_user(pass_client, user);

            if (pass_user == null) {
                pass_client.createObject(user);

                LOG.info("Created user: {}", user.getUsername());
            } else {
                update_pass_user(pass_client, user, pass_user);
            }
        }
    }

    private void update_pass_user(PassClient pass_client, User shib_user, User pass_user) throws IOException {
        boolean update = false;

        if (!Objects.equals(pass_user.getUsername(), shib_user.getUsername())) {
            pass_user.setUsername(shib_user.getUsername());
            update = true;
        }

        if (!Objects.equals(pass_user.getEmail(), shib_user.getEmail())) {
            pass_user.setEmail(shib_user.getEmail());
            update = true;
        }

        if (!Objects.equals(pass_user.getDisplayName(), shib_user.getDisplayName())) {
            pass_user.setDisplayName(shib_user.getDisplayName());
            update = true;
        }

        if (!Objects.equals(pass_user.getFirstName(), shib_user.getFirstName())) {
            pass_user.setFirstName(shib_user.getFirstName());
            update = true;
        }

        if (!Objects.equals(pass_user.getLastName(), shib_user.getLastName())) {
            pass_user.setLastName(shib_user.getLastName());
            update = true;
        }

        if (!PassEntity.listEquals(pass_user.getLocatorIds(), shib_user.getLocatorIds())) {
            pass_user.setLocatorIds(shib_user.getLocatorIds());
            update = true;
        }

        if (!Objects.equals(pass_user.getAffiliation(), shib_user.getAffiliation())) {
            pass_user.setAffiliation(shib_user.getAffiliation());
            update = true;
        }

        if (!PassEntity.listEquals(pass_user.getRoles(), shib_user.getRoles())) {
            pass_user.setRoles(shib_user.getRoles());
            update = true;
        }

        if (update) {
            pass_client.updateObject(pass_user);
            LOG.info("Updated user: {}", shib_user.getUsername());
        }
    }

    private User find_pass_user(PassClient pass_client, User user) throws IOException {
        PassClientSelector<User> selector = new PassClientSelector<>(User.class);

        for (String locator_id : user.getLocatorIds()) {
            selector.setFilter(RSQL.hasMember("locatorIds", locator_id));
            PassClientResult<User> result = pass_client.selectObjects(selector);

            if (result.getTotal() == 1) {
                return result.getObjects().get(0);
            } else if (result.getTotal() > 1) {
                throw new BadCredentialsException("Found multiple users matching locator: " + locator_id);
            }
        }

        return null;
    }

    /**
     * @param attributes
     * @return User representing the information in the request.
     */
    private User parse_user(Map<String, List<Object>> attributes) {
        User user = new User();

        String display_name = get(attributes, Attribute.DISPLAY_NAME, true);
        String given_name = get(attributes, Attribute.GIVEN_NAME, true);
        String surname = get(attributes, Attribute.SURNAME, true);
        String email = get(attributes, Attribute.EMAIL, true);
        String eppn = get(attributes, Attribute.EPPN, true);
        String employee_id = get(attributes, Attribute.EMPLOYEE_ID, false);
        String unique_id = get(attributes, Attribute.UNIQUE_ID, true);
        String affiliation = get(attributes, Attribute.SCOPED_AFFILIATION, false);

        String[] eppn_parts = eppn.split("@");

        if (eppn_parts.length != 2) {
            throw new BadCredentialsException("EPPN attribute malformed: " + eppn);
        }

        String domain = eppn_parts[1];
        String institutional_id = eppn_parts[0].toLowerCase();

        if (domain.isEmpty() || institutional_id.isEmpty()) {
            throw new BadCredentialsException("EPPN attribute malformed: " + eppn);
        }

        unique_id = String.join(":", domain, UNIQUE_ID_TYPE, unique_id.split("@")[0]);

        // The locator id list has durable ids first.
        user.getLocatorIds().add(unique_id);

        institutional_id = String.join(":", domain, INSTITUIONAL_ID_TYPE, institutional_id);
        user.getLocatorIds().add(institutional_id);

        if (employee_id != null && !employee_id.isEmpty()) {
            employee_id = String.join(":", domain, EMPLOYEE_ID_TYPE, employee_id);
            user.getLocatorIds().add(employee_id);
        }

        user.getAffiliation().add(domain);

        if (affiliation != null) {
            for (String s : affiliation.split(";")) {
                user.getAffiliation().add(s);
            }
        }

        user.setDisplayName(display_name);
        user.setEmail(email);
        user.setFirstName(given_name);
        user.setLastName(surname);
        user.setUsername(eppn);
        user.getRoles().add(UserRole.SUBMITTER);

        return user;
    }

    private String get(Map<String, List<Object>> attributes, Attribute attr, boolean required)
            throws AuthenticationException {
        String key = config.getAttributeMap().get(attr);

        List<Object> values = attributes.get(key);

        if (values == null || values.size() == 0 && required) {
            throw new BadCredentialsException("Missing attribute: " + attr + "[" + key + "]");
        }

        if (values.size() > 1) {
            throw new BadCredentialsException("Too many attributes: " + attr + "[" + key + "]");
        }

        String value = null;

        if (values.get(0) != null) {
            value = values.get(0).toString().trim();
        }

        if (value == null || value.isEmpty()) {
            value = null;

            if (required) {
                throw new BadCredentialsException("Missing attribute: " + attr + "[" + key + "]");
            }
        }

        return value;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContext context = this.securityContextHolderStrategy.getContext();
        Authentication auth = context.getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Saml2AuthenticatedPrincipal) {
            try {
                context.setAuthentication(authenticate((Saml2AuthenticatedPrincipal) auth.getPrincipal()));
                securityContextRepository.saveContext(context, request, response);

                LOG.debug("Shib user logged in {}", auth.getName());
            } catch (AuthenticationException e) {
                // This should not happen
                LOG.error("Login failed", e);

                response.setStatus(HttpStatus.BAD_REQUEST.value());
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
