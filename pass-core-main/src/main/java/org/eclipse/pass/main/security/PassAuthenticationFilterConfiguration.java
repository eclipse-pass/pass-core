package org.eclipse.pass.main.security;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for a PassAuthenticationFilter
 */
@Configuration
@ConfigurationProperties(prefix = "pass.auth")
public class PassAuthenticationFilterConfiguration {
    // Map attributes to asserting party key
    private Map<PassAuthenticationFilter.Attribute, String> attribute_map;

    /**
     * @return Mapping of user attributes to keys in SAML response
     */
    public Map<PassAuthenticationFilter.Attribute, String> getAttributeMap() {
        return attribute_map;
    }

    /**
     * Set the user attribute mapping.
     *
     * @param attribute_map Mapping of user attributes to keys in SAML response
     */
    public void setAttributeMap(Map<PassAuthenticationFilter.Attribute, String> attribute_map) {
        this.attribute_map = attribute_map;
    }
}
