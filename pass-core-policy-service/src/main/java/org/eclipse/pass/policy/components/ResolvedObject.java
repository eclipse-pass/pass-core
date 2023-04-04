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
package org.eclipse.pass.policy.components;

import java.io.IOException;
import java.util.Objects;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents the ResolvedObject object
 * Contains a parsed JSON object, as well as the source URI where it came from
 *
 * @author David McIntyre
 */
public class ResolvedObject {

    private String source;
    private JSONObject object;

    public ResolvedObject() {
        this.source = "";
        this.object = new JSONObject();
    }

    public ResolvedObject(String source, JSONObject object) {
        this.source = source;
        this.object = object;
    }

    public ResolvedObject(String source, Object object) throws IOException {
        this.source = source;
        this.setObject(object);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ResolvedObject)) {
            return false;
        }
        ResolvedObject resolvedObject = (ResolvedObject) o;
        return Objects.equals(source, resolvedObject.source)
                && object.toString().equals(resolvedObject.object.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, object);
    }

    /**
     * getSource()
     *
     * @return URI
     */
    public String getSource() {
        return this.source;
    }

    /**
     * setSource()
     *
     * @param source the source to set
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * getObject()
     *
     * @return Map&lt;String, String&gt;
     */
    public JSONObject getObject() {
        if (this.object.length() > 0) {
            return this.object;
        } else {
            return null;
        }
    }

    /**
     * setObject()
     *
     * @param object the object to set
     */
    public void setObject(JSONObject object) {
        this.object = object;
    }

    /**
     * setObject(Object)
     *
     * @param object - an object to be parsed into JSON
     * @throws IOException - object is not a valid JSON object
     */
    public void setObject(Object object) throws IOException {
        try {
            JSONObject json = new JSONObject(object);
            this.setObject(json);
        } catch (JSONException e) {
            throw new IOException("Unable to resolve " + object + " to a valid JSON object", e);
        }
    }

}