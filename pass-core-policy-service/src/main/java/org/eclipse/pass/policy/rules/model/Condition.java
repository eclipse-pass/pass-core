package org.eclipse.pass.policy.rules.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * @author jrm
 */

public class Condition {

    private List<Map<String, Map<String, String>>> anyOf = new ArrayList<>();

    private HashMap<String, String> contains = new HashMap<>();

    private HashMap<String, String> endsWith = new HashMap<>();

    private HashMap<String, JSONObject> equals = new HashMap<>();

    private List<Map<String, Map<String, String>>> noneOf = new ArrayList<>();

    public List<Map<String, Map<String, String>>> getAnyOf() {
        return anyOf;
    }

    public void setAnyOf(List<Map<String, Map<String, String>>> anyOf) {
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
}

