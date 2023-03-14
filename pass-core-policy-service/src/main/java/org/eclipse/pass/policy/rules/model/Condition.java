package org.eclipse.pass.policy.rules.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.pass.policy.interfaces.Evaluation;
import org.eclipse.pass.policy.interfaces.VariableResolver;
import org.eclipse.pass.policy.rules.Variable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents the Condition object
 * Condition in the policy rules DSL is a JSON object that determines whether a
 * policy applies or not.
 * It is generally of the form:
 * {
 * "anyOf": [
 * {"equals":{"one":"two"}},
 * {"endsWith":{"one":"gone"}}
 * ]
 * }
 *
 * @author David McIntyre
 */

public class Condition {

    public Condition() {

    }
  
    ObjectMapper objectMapper = new ObjectMapper();

    @JsonProperty("conditions")
    private JSONObject conditions = new JSONObject();

    @JsonProperty("anyOf")
    List<Map<String, Map<String,String>>> anyOf = new ArrayList<>();

    @JsonProperty("contains")
    HashMap<String, String> contains = new HashMap<>();

    @JsonProperty("endsWith")
    HashMap<String, String> endsWith = new HashMap<>();

    @JsonProperty("equals")
    HashMap<String, JSONObject> equals = new HashMap<>();

    @JsonProperty("noneOf")
    List<Map<String, Map<String,String>>> noneOf = new ArrayList<>();

    public Condition(JSONObject conditions) {
        this.conditions = conditions;
    }

    public JSONObject getConditions() {
        return conditions;
    }

    public void setConditions(JSONObject conditions) {
        this.conditions = conditions;
    }

    public List<Map<String, Map<String, String>>> getAnyOf() {
        return anyOf;
    }

    public void setAllOf(List<Map<String, Map<String, String>>> anyOf) {
        this.anyOf = anyOf;
    }

    public HashMap<String, String> getContains() {
        return contains;
    }

    public void setContains(HashMap<String, String> contains) {
        this.contains = contains;
    }

    public HashMap<String, String> getEndsWith() {
        return endsWith;
    }

    public void setEndsWith(HashMap<String, String> endsWith) {
        this.endsWith = endsWith;
    }

    public HashMap<String, JSONObject> getEquals() {
        return equals;
    }

    public void setEquals(HashMap<String, JSONObject> equals) {
        this.equals = equals;
    }

    public List<Map<String, Map<String, String>>> getNoneOf() {
        return noneOf;
    }

    public void setNoneOf(List<Map<String, Map<String, String>>> noneOf) {
        this.noneOf = noneOf;
    }

    /**
     * Checks if a policy meets any conditions supplied and returns a Boolean value
     * indicating whether or not a policy is applicable. If no conditions are
     * present, then the policy is applicable.
     *
     * @param variables - optional variables. Set to null to pass through
     * @return Boolean - condition applies or not
     * @throws RuntimeException - could not evaluate condition
     */
    public Boolean apply(VariableResolver variables) throws RuntimeException {
        Boolean passes;

        // If there are no conditions present, then the policy is applicable
        if (conditions.isEmpty()) {
            return passes = true;
        }

      //  JSONObject conditions = stringify(conditions);

        for (String cond : conditions.keySet()) {
            passes = false;
            try {
                switch (cond) {
                    case "endsWith":
                        passes = endsWith(conditions.getJSONArray(cond), variables);
                        break;
                    case "equals":
                        passes = equals(conditions.getJSONArray(cond), variables);
                        break;
                    case "anyOf":
                        passes = anyOf(conditions.getJSONObject(cond), variables);
                        break;
                    case "noneOf":
                        passes = noneOf(conditions.getJSONObject(cond), variables);
                        break;
                    case "contains":
                        passes = contains(conditions.getJSONArray(cond), variables);
                        break;
                    default:
                        throw new IOException("Unknown condition " + cond);
                }

                if (!passes) {
                    return false;
                }

            } catch (JSONException | IOException e) {
                throw new RuntimeException("Could not evaluate condition " + cond, e);
            }
        }

        return true;
    }

    /**
     * @param fromCondition - the values and their evaluators
     * @param variables     - optional variables. Set to null to pass through
     * @return Boolean - condition applies or not
     * @throws IOException - failed to resolve condition
     */
    public Boolean endsWith(Object fromCondition, VariableResolver variables) throws IOException {
        Evaluation test = (a, b) -> a.toString().endsWith(b.toString());
        return eachPair(fromCondition, variables, test);
    }

    /**
     * @param fromCondition - the values and their evaluators
     * @param variables     - optional variables. Set to null to pass through
     * @return Boolean - condition applies or not
     * @throws IOException - failed to resolve condition
     */
    public Boolean equals(Object fromCondition, VariableResolver variables) throws IOException {
        Evaluation test = (a, b) -> a.toString().equals(b.toString());
        return eachPair(fromCondition, variables, test);
    }

    /**
     * @param fromCondition - the values and their evaluators
     * @param variables     - optional variables. Set to null to pass through
     * @return Boolean - condition applies or not
     * @throws IOException - failed to resolve condition
     */
    public Boolean contains(Object fromCondition, VariableResolver variables) throws IOException {
        Evaluation test = (a, b) -> a.toString().contains(b.toString());
        return eachPair(fromCondition, variables, test);
    }

    /**
     * @param arg       - the JSON object of values and their evaluators
     * @param variables - optional variables. Set to null to pass through
     * @return Boolean - condition applies or not
     * @throws IOException - failed to resolve condition
     */
    public Boolean anyOf(Object arg, VariableResolver variables) throws IOException {
        if (!(arg instanceof JSONObject)) {
            throw new IOException("Expecting a JSONObject, but got " + arg.getClass());
        }

        try {
            JSONObject args = (JSONObject) arg;

            Boolean passes;

            for (String evaluation : args.keySet()) {
                passes = false;
                if (!(args.get(evaluation) instanceof JSONArray)) {
                    throw new IOException(
                        "Expecting a JSONArray as an evaluator item, but got " + args.get(evaluation).getClass());
                }

                JSONArray values = (JSONArray) args.get(evaluation);
                JSONObject test = new JSONObject().put(evaluation, values);

                try {
                    passes = new Condition(test).apply(variables);
                } catch (RuntimeException e) {
                    throw new RuntimeException("Condition failed to apply: " + test, e);
                }

                if (passes) {
                    return true;
                }
            }
        } catch (JSONException e) {
            throw new IOException("Failed to create JSON object from arg");
        }

        return false;
    }

    /**
     * @param arg       - the JSON object of values and their evaluators
     * @param variables - optional variables. Set to null to pass through
     * @return Boolean - condition applies or not
     * @throws IOException - failed to resolve condition
     */
    public Boolean noneOf(Object arg, VariableResolver variables) throws IOException {
        Boolean passes = anyOf(arg, variables);
        return !passes;
    }

    /**
     * Evaluate each pair of values supplied using the given test function supplied
     * (either endsWith(), contains() or equals()).
     *
     * @param source    - the values to be evaluated
     * @param variables - optional variables. Set to null to pass through
     * @param test      - supplied test function for evaluation of value pair
     * @return Boolean - value pair passes test function or not
     * @throws IOException - could not test value pair
     */
    public Boolean eachPair(Object source, VariableResolver variables, Evaluation test) throws IOException {
        if (!(source instanceof JSONArray)) {
            throw new IOException("Expecting a JSON array, instead got a " + source.getClass());
        }

        try {
            JSONArray fromCondition = (JSONArray) source;

            try {
                String a = fromCondition.getString(1);
                String b = fromCondition.getString(0);

                // If there's no resolver, just pass through.
                if (variables == null) {
                    variables = new Variable("");
                }

                try {
                    a = singleValued(variables.resolve(a));
                } catch (IOException e) {
                    throw new RuntimeException("Could not resolve variable " + a, e);
                }

                try {
                    b = singleValued(variables.resolve(b));
                } catch (IOException e) {
                    throw new RuntimeException("Could not resolve variable " + b, e);
                }

                if (!test.eval(a, b)) {
                    return false;
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("Condition contains a " + source.getClass() + " instead of a String");
            }
        } catch (JSONException e) {
            throw new IOException("Unable to create JSONArray from source", e);
        }
        return true;
    }

    public String singleValued(List<String> list) throws IOException {
        if (list.isEmpty()) {
            return "";
        }

        if (list.size() != 1) {
            throw new IOException("Expecting single valued string, instead got " + list);
        }

        return list.get(0).toString();
    }

    public JSONObject stringify(List<Condition> conditions) {
        String condString = "";
        try {
           condString = objectMapper.writeValueAsString(conditions);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return new JSONObject(condString);
    }
}

