/*
 *
 * Copyright 2022 Johns Hopkins University
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.eclipse.pass.doi.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.JsonObject;

/**
 * ExternalDoiService classes provide configuration needed for specific implementations'
 * connections, as well as a method to process the raw JSON object returned by the external service to
 * suit the requirements of the PASS UI.
 *
 * @author jrm
 */
public abstract class ExternalDoiService {
    final static String MAILTO = "pass@jhu.edu";

    /**
     * The name of the external service
     * @return the name of the external service
     */
    public abstract String name();

    /**
     * The base URL of the external service
     * @return the base URL of the external service
     */
    public  abstract String baseUrl();

    /**
     * A key, value map of query parameters used by the external service; null if there aren't any.
     * @return the map
     */
    public  abstract Map<String, String> parameterMap();

    /**
     * A key, value map of headers used by the external service; null if there aren't any.
     * @return the map
     */
    public  abstract Map<String, String> headerMap();

    /**
     * A method to transform the raw external service's JSON response to suit the UI requirements
     * @param object the raw external JSON object
     * @return the transformed JSON object
     */
    public  abstract JsonObject processObject(JsonObject object);

    /**
     * check to see whether supplied DOI is in valid format after splitting off a possible prefix
     *
     * @return the valid suffix, or null if invalid
     */
    String verify(String doi) {
        if (doi == null) {
            return null;
        }
        String criterion = "doi.org/";
        int i = doi.indexOf(criterion);
        String suffix = i >= 0 ? doi.substring(i + criterion.length()) : doi;

        Pattern pattern = Pattern.compile("^10\\.\\d{4,9}/[-._;()/:a-zA-Z0-9]+$");

        Matcher matcher = pattern.matcher(suffix);
        return matcher.matches() ? suffix : null;
    }
}
