package org.eclipse.pass.main.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.eclipse.pass.main.SamlIntegrationTest;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.model.User;
import org.eclipse.pass.object.model.UserRole;
import org.junit.jupiter.api.Test;

public class PassAuthenticationFilterTest extends SamlIntegrationTest {
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
}
