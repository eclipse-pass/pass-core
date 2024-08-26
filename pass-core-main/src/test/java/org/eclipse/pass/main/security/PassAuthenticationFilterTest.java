package org.eclipse.pass.main.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.pass.main.SamlIntegrationTest;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.model.User;
import org.eclipse.pass.object.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PassAuthenticationFilterTest extends SamlIntegrationTest {
    @Autowired
    private PassAuthenticationFilter passAuthFilter;

    @Test
    public void testLoggedInUser() throws IOException {
        User submitter = doSamlLogin();

        User expected = new User();

        expected.setDisplayName("Sally M. Submitter");
        expected.setEmail("sally123456789@jhu.edu");
        expected.setAffiliation(new HashSet<>(List.of("MEMBER@johnshopkins.edu",
                "FACULTY@johnshopkins.edu", "johnshopkins.edu")));
        expected.setFirstName("Sally");
        expected.setLastName("Submitter");
        expected.setLocatorIds(List.of("johnshopkins.edu:unique-id:sms123456789",
                "johnshopkins.edu:eppn:sallysubmitter123456789",
                "johnshopkins.edu:employeeid:123456789"));
        expected.setRoles(List.of(UserRole.SUBMITTER));
        expected.setUsername("sallysubmitter123456789@johnshopkins.edu");
        expected.setId(submitter.getId());

        assertEquals(expected, submitter);
    }

    @Test
    public void testLoginUpdatesUser() throws IOException {
        // Login and get the user. Then update the user.

        User submitter = doSamlLogin();

        User updated = new User(submitter);
        updated.setAffiliation(Collections.emptySet());
        updated.setDisplayName("Moo");
        updated.setEmail("Moo");
        updated.setFirstName("Moo");
        updated.setLastName("Moo");
        updated.setRoles(List.of());
        updated.setUsername("Moo");
        // Must keep locator ids

        try (PassClient pass_client = PassClient.newInstance(refreshableElide)) {
            pass_client.updateObject(updated);
        }

        // Login again. Must get new client and clear cache to force login.
        setupClient();
        User result = doSamlLogin();

        // User should have been reset.
        assertNotEquals(updated, result);
        assertEquals(submitter, result);
    }

    @Test
    public void testParseUser() {
        Map<String, List<Object>> attributes = new HashMap<>();

        attributes.put("urn:oid:2.16.840.1.113730.3.1.241", List.of("Thomas L. Submitter"));
        attributes.put("urn:oid:1.3.6.1.4.1.5923.1.1.1.9", List.of("FACULTY@johnshopkins.edu"));
        attributes.put("urn:oid:0.9.2342.19200300.100.1.3", List.of("tom987654321@jhu.edu"));
        attributes.put("urn:oid:1.3.6.1.4.1.5923.1.1.1.6", List.of("thomassubmitter987654321@johnshopkins.edu"));
        attributes.put("urn:oid:2.5.4.42", List.of("Tom"));
        attributes.put("urn:oid:2.5.4.4", List.of("Submitter"));
        attributes.put("urn:oid:2.16.840.1.113730.3.1.3", List.of("987654321"));
        attributes.put("urn:oid:1.3.6.1.4.1.5923.1.1.1.13", List.of("tls987654321@johnshopkins.edu"));

        User expected = new User();
        expected.setDisplayName("Thomas L. Submitter");
        expected.setEmail("tom987654321@jhu.edu");
        expected.setAffiliation(new HashSet<>(List.of("FACULTY@johnshopkins.edu", "johnshopkins.edu")));
        expected.setFirstName("Tom");
        expected.setLastName("Submitter");
        expected.setLocatorIds(List.of("johnshopkins.edu:unique-id:tls987654321",
                "johnshopkins.edu:eppn:thomassubmitter987654321",
                "johnshopkins.edu:employeeid:987654321"));
        expected.setUsername("thomassubmitter987654321@johnshopkins.edu");
        expected.setRoles(List.of(UserRole.SUBMITTER));

        User user = passAuthFilter.parseUser(attributes);

        assertEquals(expected, user);
    }

    @Test
    public void testParseUserMissingEmployeeIdAndAffiliation() {
        Map<String, List<Object>> attributes = new HashMap<>();

        attributes.put("urn:oid:2.16.840.1.113730.3.1.241", List.of("Thomas L. Submitter"));
        attributes.put("urn:oid:0.9.2342.19200300.100.1.3", List.of("tom987654321@jhu.edu"));
        attributes.put("urn:oid:1.3.6.1.4.1.5923.1.1.1.6", List.of("thomassubmitter987654321@johnshopkins.edu"));
        attributes.put("urn:oid:2.5.4.42", List.of("Tom"));
        attributes.put("urn:oid:2.5.4.4", List.of("Submitter"));
        attributes.put("urn:oid:1.3.6.1.4.1.5923.1.1.1.13", List.of("tls987654321@johnshopkins.edu"));

        User expected = new User();
        expected.setDisplayName("Thomas L. Submitter");
        expected.setEmail("tom987654321@jhu.edu");
        expected.setAffiliation(new HashSet<>(List.of("johnshopkins.edu")));
        expected.setFirstName("Tom");
        expected.setLastName("Submitter");
        expected.setLocatorIds(List.of("johnshopkins.edu:unique-id:tls987654321",
                "johnshopkins.edu:eppn:thomassubmitter987654321"));
        expected.setUsername("thomassubmitter987654321@johnshopkins.edu");
        expected.setRoles(List.of(UserRole.SUBMITTER));

        User user = passAuthFilter.parseUser(attributes);

        assertEquals(expected, user);
    }
}
