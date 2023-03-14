package org.eclipse.pass.policy.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.pass.policy.rules.util.ConditionUtil;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Condition rules resolution
 *
 * @author - David McIntyre
 */
@DisplayName("Condition Tests")
public class ConditionTest {

    @Test
    @DisplayName("Test apply() function with valid endsWith Condition")
    public void testTrueApplyEndsWith() throws Exception {
        JSONObject conditions = new JSONObject();
        List<String> values = new ArrayList<>();
        values.add("ing");
        values.add("testString");
        conditions.put("endsWith", values);

        Boolean result = ConditionUtil.apply(conditions, null);
        assertTrue(result);
    }

    @Test
    @DisplayName("Test apply() function with valid contains Condition")
    public void testTrueApplyContains() throws Exception {
        JSONObject conditions = new JSONObject();
        List<String> values = new ArrayList<>();
        values.add("estS");
        values.add("testString");
        conditions.put("contains", values);

        Boolean result = ConditionUtil.apply(conditions,null);
        assertTrue(result);
    }

    @Test
    @DisplayName("Test apply() function with valid equals Condition")
    public void testTrueApplyEquals() throws Exception {
        JSONObject conditions = new JSONObject();
        List<String> values = new ArrayList<>();
        values.add("testString");
        values.add("testString");
        conditions.put("equals", values);

        Boolean result = ConditionUtil.apply(conditions,null);
        assertTrue(result);
    }

    @Test
    @DisplayName("Test apply() function with valid anyOf Condition")
    public void testTrueApplyAnyOf() throws Exception {
        JSONObject conditions = new JSONObject();
        JSONObject evaluators = new JSONObject();
        List<String> equalsValues = new ArrayList<>();
        equalsValues.add("testString");
        equalsValues.add("testString");
        List<String> containsValues = new ArrayList<>();
        containsValues.add("invalid");
        containsValues.add("testString");
        evaluators.put("equals", equalsValues);
        evaluators.put("contains", containsValues);
        conditions.put("anyOf", evaluators);

        Boolean result = ConditionUtil.apply(conditions,null);
        assertTrue(result);
    }

    @Test
    @DisplayName("Test apply() function with valid noneOf Condition")
    public void testTrueApplyNoneOf() throws Exception {
        JSONObject conditions = new JSONObject();
        JSONObject evaluators = new JSONObject();
        List<String> equalsValues = new ArrayList<>();
        equalsValues.add("invalid");
        equalsValues.add("testString");
        List<String> containsValues = new ArrayList<>();
        containsValues.add("invalid");
        containsValues.add("testString");
        evaluators.put("equals", equalsValues);
        evaluators.put("contains", containsValues);
        conditions.put("noneOf", evaluators);

       // Boolean result = new Condition(conditions).apply(null);
        Boolean result = ConditionUtil.apply(conditions, null);
        assertTrue(result);
    }

    @Test
    @DisplayName("Test apply() function with invalid endsWith Condition")
    public void testFalseApplyEndsWith() throws Exception {
        JSONObject conditions = new JSONObject();
        List<String> values = new ArrayList<String>();
        values.add("invalid");
        values.add("testString");
        conditions.put("endsWith", values);

        Boolean result = ConditionUtil.apply(conditions,null);
        assertFalse(result);
    }

    @Test
    @DisplayName("Test apply() function with invalid contains Condition")
    public void testFalseApplyContains() throws Exception {
        JSONObject conditions = new JSONObject();
        List<String> values = new ArrayList<String>();
        values.add("invalid");
        values.add("testString");
        conditions.put("contains", values);

        Boolean result = ConditionUtil.apply(conditions,null);
        assertFalse(result);
    }

    @Test
    @DisplayName("Test apply() function with invalid equals Condition")
    public void testFalseApplyEquals() throws Exception {
        JSONObject conditions = new JSONObject();
        List<String> values = new ArrayList<String>();
        values.add("invalid");
        values.add("testString");
        conditions.put("equals", values);

        Boolean result = ConditionUtil.apply(conditions,null);
        assertFalse(result);
    }

    @Test
    @DisplayName("Test apply() function with invalid anyOf Condition")
    public void testFalseApplyAnyOf() throws Exception {
        JSONObject conditions = new JSONObject();
        JSONObject evaluators = new JSONObject();
        List<String> equalsValues = new ArrayList<String>();
        equalsValues.add("invalid");
        equalsValues.add("testString");
        List<String> containsValues = new ArrayList<String>();
        containsValues.add("invalid");
        containsValues.add("testString");
        evaluators.put("equals", equalsValues);
        evaluators.put("contains", containsValues);
        conditions.put("anyOf", evaluators);

        Boolean result = ConditionUtil.apply(conditions,null);
        assertFalse(result);
    }

    @Test
    @DisplayName("Test apply() function with invalid noneOf Condition")
    public void testFalseApplyNoneOf() throws Exception {
        JSONObject conditions = new JSONObject();
        JSONObject evaluators = new JSONObject();
        List<String> equalsValues = new ArrayList<String>();
        equalsValues.add("invalid");
        equalsValues.add("invalid");
        List<String> containsValues = new ArrayList<>();
        containsValues.add("invalid");
        containsValues.add("testString");
        evaluators.put("equals", equalsValues);
        evaluators.put("contains", containsValues);
        conditions.put("noneOf", evaluators);

        Boolean result = ConditionUtil.apply(conditions,null);
        assertFalse(result);
    }
}
