package org.eclipse.pass.policy.rules;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for json validator
 *
 * @author - Jim Martino
 */
@DisplayName("Validate Tests")
public class ValidatorTest {

    Validator underTest = new Validator();

    @Test
    public void testGoodPolicyJson() throws IOException {
        Validator validator = new Validator();
        validator.validate("src/test/resources/schemas/good.json");
      //  validator.validate("src/main/resources/policies/aws.json");
    }

    @Test
    public void testBadPolicyJson() throws IOException {
        Validator validator = new Validator();
        Exception exception = assertThrows(RuntimeException.class, () -> {
            validator.validate("src/test/resources/schemas/bad.json");
        });
        String expectedMessage = "There were validation errors";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }
}
