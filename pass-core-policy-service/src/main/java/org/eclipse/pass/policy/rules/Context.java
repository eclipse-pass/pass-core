/*
 * Copyright 2022 Johns Hopkins University
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
package org.eclipse.pass.policy.rules;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.yahoo.elide.RefreshableElide;
import org.eclipse.pass.object.ElideDataStorePassClient;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.model.Policy;
import org.eclipse.pass.object.model.Repository;
import org.eclipse.pass.policy.components.ResolvedObject;
import org.eclipse.pass.policy.components.VariablePinner;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents the Context object
 * Context establishes a rule evaluation/resolution context
 *
 * @author David McIntyre
 */
public class Context extends VariablePinner {

    RefreshableElide refreshableElide;
    private String submission;
    private Map<String, String> headers;
    private Map<String, Object> values;

    public Context(RefreshableElide refreshableElide) {
        this.refreshableElide = refreshableElide;
        this.headers = new HashMap<>();
        this.values = new HashMap<>();
    }

    public Context(String submission, Map<String, String> headers) {
        this.headers = new HashMap<String, String>();
        this.values = new HashMap<String, Object>();
    }

    public Context(String submission, Map<String, String> headers, RefreshableElide refreshableElide) {
        this.submission = submission;
        this.headers = headers;
        this.refreshableElide = refreshableElide;
        this.values = new HashMap<>();
    }

    public Context(String submission, Map<String, String> headers, RefreshableElide refreshableElide,
                   Map<String, Object> values) {
        this.submission = submission;
        this.headers = headers;
        this.refreshableElide = refreshableElide;
        this.values = values;
    }

    protected PassClient getNewClient() {
        return new ElideDataStorePassClient(refreshableElide);
    }

    /**
     * Init values map with request headers
     *
     * @param source - the URI or JSON blob to be resolved
     * @throws IOException - values map could not be initialised
     */
    public void init(String source) throws IOException {

        // if the values && headers map are already initialised, we're done
        if (this.values.size() == 0) {
            if (this.submission == null) {
                throw new IOException("Context requires a submission URI");
            }

            this.values.put("submission", this.submission);
        }

        if (this.headers.size() == 0) {
            throw new IOException("Context requires a map of request headers");
        }

        JSONObject object = new JSONObject(this.headers);
        ResolvedObject headers = new ResolvedObject(source, object);
        this.values.put("header", headers);
    }

    @Override
    public List<String> resolve(String source) throws RuntimeException {
        // Initialise the values map and create a new list to store the resolved
        // variables
        try {
            this.init(source);
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialise context : " + this, e);
        }
        List<String> resolved = new ArrayList<>();

        // Check for proper variable conversion. If the conversion is null, return the
        // source as the resolved variable.
        Variable variable = Variable.toVariable(source);
        if (variable == null) {
            resolved.add(source);
            return resolved;
        }

        // Resolve each segment of the variable (e.g. a, a.b, a.b.c, a.b.c.d)
        Variable segment = new Variable("");
        for (segment = variable.shift(); variable.isShifted(); segment = segment.shift()) {
            try {
                this.resolveSegment(segment);
            } catch (IOException e) {
                throw new RuntimeException("Could not resolve variable segment " + segment.getSegmentName(), e);
            }
        }

        // Get the fully-resolved variable from the values map and determine type.
        Object typedVariable = this.values.get(variable.getFullName());
        if ((typedVariable instanceof String) && !(typedVariable instanceof List<?>)) {
            // Case String
            // Add variable to resolved list
            resolved.add((String) typedVariable);
            return resolved;
        } else if (typedVariable instanceof String && typedVariable instanceof List<?>) {
            // Cast List<String>
            // Return the unique variables in the list
            return unique((List<String>) typedVariable);
        } else if (typedVariable instanceof ResolvedObject && !(typedVariable instanceof List<?>)) {
            // Case ResolvedObject
            // Return the source of the object
            ResolvedObject val = (ResolvedObject) typedVariable;
            resolved.add(val.getSource());
            return resolved;
        } else if (typedVariable instanceof ResolvedObject && typedVariable instanceof List<?>) {
            // Case List<ResolvedObject>
            // Return the source of each unique object in the list
            for (ResolvedObject val : (List<ResolvedObject>) typedVariable) {
                resolved.add(val.getSource());
            }
            return unique(resolved);
        } else if (typedVariable instanceof Object && typedVariable instanceof List<?>) {
            // Case List<Object>
            // Return any strings present in the list of Objects
            for (Object val : (List<Object>) typedVariable) {
                if (val instanceof String) {
                    resolved.add((String) val);
                }
            }
            return unique(resolved);
        } else if (typedVariable == null) {
            // Case null
            // If the variable is not in the values map, return an empty list
            return new ArrayList<>();
        }

        // The variable could not be resolved
        return null;
    }

    @Override
    public VariablePinner pin(Object variable, Object value) throws IOException {
        if (variable instanceof String && value instanceof String) {
            Variable parsed = Variable.toVariable((String) variable);

            if (parsed == null) {
                return this;
            }

            Map<String, Object> pinnedValues = new HashMap<>();
            Iterator<Entry<String, String>> it = headers.entrySet().iterator();

            while (it.hasNext()) {
                Entry<String, String> header = (Entry<String, String>) it.next();
                pinnedValues.put(header.getKey(), header.getValue());
            }

            List<String> segments = Arrays.asList(parsed.getFullName().split("."));

            pinnedValues.put(parsed.getFullName(), value);
            pinnedValues.put(segments.get(segments.size() - 1), value);

            this.values = pinnedValues;

            return this;

        } else {
            throw new IOException("Must supply two Strings: A variable, and a value to pin");
        }
    }

    /**
     * Resolves a variable segment (e.g. ${x.y} out of ${x.y.z})
     *
     * @param segment - the segment to be resolved
     * @throws IOException - segment could not be resolved
     */
    public void resolveSegment(Variable segment) throws IOException {

        // If we already have a value, no need to re-resolve it. Likewise, no point in
        // resolving ${} from ${x}
        Boolean hasKey = this.values.containsKey(segment.getSegmentName());
        Boolean hasValue = this.values.get(segment.getSegmentName()) != null;
        if ((hasKey && hasValue) || segment.prev().getSegmentName() == "") {
            return;
        }

        // We need to resolve the previous segment into a map, (or list of maps) if it
        // isn't already, and extract its value
        Object prevValue = this.values.get(segment.prev().getSegmentName());

        // ${foo} is a JSON object, or list of JSON objects, or a JSON blob that had
        // previously been resolved from URIs. So just look up key 'bar' and save those
        // values to ${foo.bar}
        if (prevValue instanceof ResolvedObject && !(prevValue instanceof List<?>)) {
            // Case ResolvedObject
            this.extractValue(segment, (ResolvedObject) prevValue);
        } else if (prevValue instanceof ResolvedObject && prevValue instanceof List<?>) {
            // Case List<ResolvedObject>
            this.extractValues(segment, (List<ResolvedObject>) prevValue);
        } else if (prevValue instanceof String && !(prevValue instanceof List<?>)) {
            // Case String
            // ${foo} is a String, or list of Strings. In order to find ${foo.bar}, see if
            // each foo is a stringified JSON blob, or an HTTP URI.
            // If it's a blob, parse to a JSON object and save it as a ResolvedObject to
            // ${foo.bar}.
            // If it's a URI, dereference it and, if its a JSON blob, parse it to a JSON
            // object and save a ResolvedObject containing both the URI and the resulting
            // blob to ${foo.bar}
            this.resolveToObject(segment.prev(), (String) prevValue);

            this.resolveSegment(segment);
        } else if (prevValue instanceof String && prevValue instanceof List<?>) {
            // Case List<String>
            this.resolveToObjects(segment.prev(), (List<String>) prevValue);

            this.resolveSegment(segment);
        } else if (prevValue instanceof Object && prevValue instanceof List<?>) {
            // Case Object
            // ${foo} is a list of some sort, but we don't know the type of the items. If
            // they are URIs, dereference the URIs.
            // If the Result is a JSON blob, parse it and look up the value of the key 'bar'
            // in each blob. Save to ${foo.bar}
            List<String> list = new ArrayList<String>();

            for (Object item : (List<Object>) prevValue) {
                if (!(item instanceof String)) {
                    throw new IOException(
                        "Expecting list items to be strings, instead got " + item.getClass().getName());
                }

                list.add((String) item);
            }

            try {
                this.resolveToObjects(segment.prev(), list);
            } catch (Exception e) {
                throw new IOException("Could not resolve all uris in " + segment.prev().getSegmentName(), e);
            }

            this.resolveSegment(segment);
        } else if (prevValue == null) {
            // Case Null
            // ${bar} has no value, so of course ${foo.bar} has no value either

            this.values.put(segment.getSegmentName(), new ArrayList<String>());
            this.values.put(segment.getSegment(), new ArrayList<String>());
        } else {
            // Case Default
            // ${bar} is some unexpected type.

            throw new IOException(segment.prev().getSegmentName() + " is a" + prevValue.getClass().getName()
                                  + ", cannot parse into an object to extract " + segment.getSegmentName());
        }
    }

    /**
     * Set ${foo.bar} to foo[bar]
     *
     * @param v        - the variable to extract
     * @param resolved - the resolved Object
     * @throws IOException - Object is not a String
     */
    public void extractValue(Variable v, ResolvedObject resolved) throws IOException {
        Object val = resolved.getObject().get(v.getSegment());

        if (val == null) {
            this.values.put(v.getSegmentName(), new ArrayList<String>());
            this.values.put(v.getSegment(), new ArrayList<String>());
        }

        if (!(val instanceof String) || val instanceof List<?>) {
            throw new IOException(v.getSegmentName() + " is " + val.getClass().getName() + ", or a list, not a String");
        }

        List<String> valList = new ArrayList<String>();
        valList.add((String) val);

        this.values.put(v.getSegmentName(), valList);
        this.values.put(v.getSegment(), valList); // this is the shortcut ${properties}, instead of ${x.y.properties}
    }

    /**
     * Append foo[bar] to ${foo.bar} for each foo
     *
     * @param v            - the variable to extract
     * @param resolvedList - the resolved Object List
     * @throws IOException - Object does not contain a list of Strings
     */
    public void extractValues(Variable v, List<ResolvedObject> resolvedList) throws IOException {
        List<String> vals = new ArrayList<String>();

        for (ResolvedObject resolved : resolvedList) {
            if (resolved.getObject().has(v.getSegment())
                && resolved.getObject().get(v.getSegment()) != null) {

                Object typedVal = resolved.getObject().get(v.getSegment());

                if (typedVal instanceof String && !(typedVal instanceof List<?>)) {
                    // Case String
                    vals.add((String) typedVal);
                } else if (typedVal instanceof Object && typedVal instanceof List<?>) {
                    // Case List<Object>
                    for (Object item : (List<Object>) typedVal) {
                        if (item instanceof String) {
                            String strVal = (String) item;
                            vals.add(strVal);
                        } else {
                            throw new IOException(
                                v.getSegmentName() + " is a list of " + item.getClass().getName()
                                + ", not Strings");
                        }
                    }
                } else {
                    // Case default
                    throw new IOException(
                        v.getSegmentName() + " is a " + typedVal.getClass().getName() + ", not a String");
                }
            }
        }

        this.values.put(v.getSegmentName(), vals);
        this.values.put(v.getSegment(), vals); // this is the shortcut ${properties}, instead of ${x.y.properties}
    }

    /**
     * Resolve a String to an object. This will only work if the String is a valid
     * http URI, or a JSON blob.
     *
     * @param v      - the variable to resolve
     * @param source - the URI to be resolved
     * @throws RuntimeException - the source is not a valid URI or JSON blob
     */
    public void resolveToObject(Variable v, String source) throws RuntimeException {
        ResolvedObject resolved = new ResolvedObject();
        resolved.setSource(source);

        try (PassClient passClient = getNewClient()) {
            // If it's a URI, try resolving it, otherwise, attempt to decode it as a JSON
            // blob
            if (source.startsWith("http")) {
                URI uri = new URI(source);

                String path = uri.getPath();
                String idStr = path.substring(path.lastIndexOf('/') + 1);
                Long id = Long.parseLong(idStr);

                if (source.contains("/policies/")) {
                    resolved.setObject(passClient.getObject(Policy.class, id));
                } else if (source.contains("/repositories/")) {
                    resolved.setObject(passClient.getObject(Repository.class, id));
                } else {
                    throw new IOException("Expected request for Policy or Repository, instead got " + source);
                }

            } else {
                resolved.setObject(new JSONObject(source));
            }
        } catch (URISyntaxException | JSONException | IOException e) {
            throw new RuntimeException("Unable to resolve source to an object " + source, e);
        }

        this.values.put(v.getSegmentName(), resolved);
        this.values.put(v.getSegment(), resolved);
    }

    /**
     * Resolve each of a list of URIs to an object. This will only work if each URI
     * is a valid URI, or a JSON blob.
     *
     * @param v    - the variable to resolve
     * @param vals - the list of URIs to be resolved
     * @throws RuntimeException - source could not be resolved
     */
    public void resolveToObjects(Variable v, List<String> vals) throws RuntimeException {
        List<ResolvedObject> objects = new ArrayList<ResolvedObject>();

        try (PassClient passClient = getNewClient()) {
            for (String source : vals) {
                ResolvedObject resolved = new ResolvedObject();
                resolved.setSource(source);
                resolved.setObject(new JSONObject());

                // If it's a URI, try resolving it, otherwise, attempt to decode it as a JSON
                // blob
                if (source.startsWith("http")) {
                    URI uri = new URI(source);
                    String path = uri.getPath();
                    String idStr = path.substring(path.lastIndexOf('/') + 1);
                    Long id = Long.parseLong(idStr);

                    if (source.contains("/policies/")) {
                        resolved.setObject(passClient.getObject(Policy.class, id));
                    } else if (source.contains("/repositories/")) {
                        resolved.setObject(passClient.getObject(Repository.class, id));
                    } else {
                        throw new IOException("Expected request for Policy or Repository, instead got " + source);
                    }
                } else {
                    resolved.setObject(new JSONObject(source));
                }

                objects.add(resolved);
            }

            this.values.put(v.getSegmentName(), objects);
            this.values.put(v.getSegment(), objects);

        } catch (URISyntaxException | JSONException | IOException e) {
            throw new RuntimeException("Unable to resolve source to an object", e);
        }
    }

    /**
     * Returns a supplied list of Strings with duplicates removed.
     *
     * @param vals - the list of strings to be parsed for unique values
     * @return List&lt;String&gt; - the list with duplicates removed
     */
    public List<String> unique(List<String> vals) {

        if (vals.size() < 2) {
            return vals;
        }

        List<String> uniqueVals = vals.stream().distinct().collect(Collectors.toList());

        return uniqueVals;
    }

    /**
     * @return String
     */
    public String getSubmission() {
        return this.submission;
    }

    /**
     * @param submission - the ubmission to set
     */
    public void setSubmission(String submission) {
        this.submission = submission;
    }

    /**
     * @return Map&lt;String, String&gt;
     */
    public Map<String, String> getHeaders() {
        return this.headers;
    }

    /**
     * @param headers - the headers map to set
     */
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    /**
     * @return Map&lt;String, Object&gt;
     */
    public Map<String, Object> getValues() {
        return this.values;
    }

    /**
     * @param values - the values map to set
     */
    public void setValues(Map<String, Object> values) {
        this.values = values;
    }

}
