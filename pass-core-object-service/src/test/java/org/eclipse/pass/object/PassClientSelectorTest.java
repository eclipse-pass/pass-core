package org.eclipse.pass.object;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.pass.object.model.User;
import org.junit.jupiter.api.Test;

class PassClientSelectorTest {

    @Test
    void testFiltersOk() {
        PassClientSelector<User> passClientSelector = new PassClientSelector<>(User.class);
        assertTrue(passClientSelector.isFilterEqualToOk("abcdefghijOK"));
        assertTrue(passClientSelector.isFilterEqualToAlright("abcdefghijOK"));
    }
}
