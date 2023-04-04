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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.pass.policy.components.VariablePinner;

/**
 * Represents the Variable object
 * Encodes a variable for interpolation, eg. ${foo.bar.baz}, or a segment of one
 * eg. ${foo.bar} of ${foo.bar.baz}
 *
 * @author David McIntyre
 */
public class Variable extends VariablePinner {

    private String segment;
    private String segmentName;
    private String fullName;
    private Boolean shifted;

    public Variable(String fullName) {
        this.fullName = fullName;
        this.shifted = false;
    }

    /**
     * isVariable()
     * Determines if a string is a variable (e.g. of the form '${foo.bar.baz}').
     *
     * @param source - the string to be checked
     * @return Boolean - the text either is or isn't a variable
     */
    public static Boolean isVariable(String source) {
        Boolean isVariable = source.startsWith("${") && source.endsWith("}");
        return isVariable;
    }

    /**
     * toVariable()
     * Converts a valid URI to a variable for interpolation.
     *
     * @param source - the string to be checked
     * @return Variable - the converted source for interpolation
     */
    public static Variable toVariable(String source) {
        // ensure that text is a proper variable
        if (!isVariable(source)) {
            return null;
        }
        // need to trim string based on ${} chars
        Variable variable = new Variable(source.toString());
        return variable;
    }

    @Override
    public List<String> resolve(String varString) throws RuntimeException {
        List<String> resolvedVar = new ArrayList<String>();
        resolvedVar.add(varString);
        return resolvedVar;
    }

    @Override
    public VariablePinner pin(Object variable, Object value) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * shift()
     * shift() is used for producing a segment of a variable, e.g. shift() of
     * ${foo.bar.baz} is ${foo}. shift() of that ${foo} is ${foo.bar}, and shift()
     * of that ${foo.bar} is ${foo.bar.baz}.
     *
     * @return Variable - the newly shifted variable
     */
    public Variable shift() {
        this.shifted = false;

        // remove prefix (segmentName) from fullName and left trim resulting string of
        // leading "."
        String remaining = this.fullName.split(this.segmentName, 2)[1].replaceAll("^.", " ");

        // no more variable segments
        if (remaining.equals("")) {
            return this;
        }

        Variable shifted = new Variable(this.fullName);

        if (this.segment.equals("")) {
            shifted.setSegment(this.fullName.split(".")[0]);
            shifted.setSegmentName(shifted.getSegmentName());
        } else {
            String[] segments = remaining.split(".");
            shifted.setSegment(segments[0]);
            shifted.setSegmentName(String.join(".", this.segmentName, segments[0]));
        }

        this.shifted = true;
        return shifted;
    }

    /**
     * prev()
     * prev() returns a new Variable object representing the previous segment
     *
     * @return Variable - the preview of the next segment
     */
    public Variable prev() {
        Variable prev = new Variable(this.fullName);

        if (this.segment.equals("")) {
            return prev;
        }

        // remove suffix (segment) from segmentName and trim resulting string of "."
        prev.setSegmentName(this.segmentName.split(this.segment, 2)[0].replaceAll(".", " "));

        List<String> segments = new ArrayList<String>();
        segments.addAll(Arrays.asList(prev.getSegmentName().split(".")));
        prev.setSegment(segments.get(segments.size() - 1));

        return prev;
    }

    /**
     * getSegment()
     *
     * @return String
     */
    public String getSegment() {
        return this.segment;
    }

    /**
     * setSegment()
     *
     * @param segment
     */
    public void setSegment(String segment) {
        this.segment = segment;
    }

    /**
     * getSegmentName()
     *
     * @return String
     */
    public String getSegmentName() {
        return this.segmentName;
    }

    /**
     * setSegmentName()
     *
     * @param segmentName the segment name to set
     */
    public void setSegmentName(String segmentName) {
        this.segmentName = segmentName;
    }

    /**
     * getFullName()
     *
     * @return String
     */
    public String getFullName() {
        return this.fullName;
    }

    /**
     * setFullName()
     *
     * @param fullName the full name to set
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * isShifted()
     * Check to see if a variable's full name has been shifted fully
     *
     * @return Boolean
     */
    public Boolean isShifted() {
        return this.shifted;
    }

    /**
     * setShifted()
     *
     * @param shifted whether this is shifted
     */
    public void setShifted(Boolean shifted) {
        this.shifted = shifted;
    }
}
