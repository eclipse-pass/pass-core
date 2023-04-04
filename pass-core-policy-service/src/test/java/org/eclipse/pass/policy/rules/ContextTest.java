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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.yahoo.elide.RefreshableElide;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.model.Policy;
import org.eclipse.pass.policy.components.ResolvedObject;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Unit tests for Context rules resolution
 *
 * @author - David McIntyre
 */
@DisplayName("Context Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ContextTest {

    @Mock
    private PassClient mockPassClient;

    @Mock
    private Policy mockPolicy;

    Context context;

    @Autowired
    RefreshableElide refreshableElide;

    @BeforeAll
    public void init() {
        MockitoAnnotations.openMocks(this);
    }

    @BeforeEach
    public void setup() {
        context = new Context(refreshableElide);
    }

    /**
     * Unit tests for init() method in Context
     */
    @Test
    @DisplayName("Test: Test init() with valid submission & headers")
    public void testInitValid() {
        context.setSubmission("${example.submission}");
        context.setHeaders(new HashMap<String, String>() {
            {
                put("Content-Type", "application/json");
                put("Accept", "application/json");
            }
        });

        try {
            context.init("source");
            assertEquals(2, context.getValues().size());
            assertEquals(2, context.getHeaders().size());
            assertEquals("${example.submission}", context.getValues().get("submission"));
            assertTrue(context.getValues().get("header") instanceof ResolvedObject);
        } catch (Exception e) {
            fail("Unexpected Exception thrown for valid input");
        }
    }

    @Test
    @DisplayName("Test: Test init() with only valid submission")
    public void testInitSubmissionOnly() {
        context.setSubmission("${example.submission}");

        try {
            context.init("source");
            fail("Expected exception to be thrown for no request headers");
        } catch (Exception e) {
            assertEquals("Context requires a map of request headers", e.getMessage());
        }
    }

    @Test
    @DisplayName("Test: Test init() with only valid headers")
    public void testInitHeadersOnly() {
        context.setHeaders(new HashMap<>() {
            {
                put("Content-Type", "application/json");
                put("Accept", "application/json");
            }
        });

        try {
            context.init("source");
            fail("Expected exception to be thrown for no submission URI");
        } catch (IOException e) {
            assertEquals("Context requires a submission URI", e.getMessage());
        }
    }

    /**
     * Unit tests for resolve() method in Context
     */

    /**
     * Unit tests for pin() method in Context
     */

    /**
     * Unit tests for resolveSegment() method in Context
     */

    /**
     * Unit tests for extractValue() method in Context
     */

    /**
     * Unit tests for extractValues() method in Context
     */

    /**
     * Unit tests for resolveToObject() method in Context
     */
    @Test
    @DisplayName("Test: Test resolveToObject() with a valid submission URI")
    public void testResolveToObjectValidURI() throws IOException {
        String uri = "http://example.com/policies/1";
        Variable v = new Variable("foo.bar.baz.policy");
        v.setSegment("policySegmentName");
        v.setSegmentName("policySegment");
        ResolvedObject expected = null;

        try {
            expected = new ResolvedObject("http://example.com/policies/1", mockPolicy);
        } catch (IOException e) {
            fail("Unexpected exception thrown for valid submission URI", e);
        }

        Context context1 = Mockito.spy(context);
        when(mockPassClient.getObject(Policy.class, 1L))
                .thenReturn(mockPolicy);
        Mockito.doReturn(mockPassClient).when(context1).getNewClient();
        try {
            context1.resolveToObject(v, uri);
        } catch (RuntimeException e) {
            fail("Unexpected Exception thrown for valid submission", e);
        }

        assertTrue(expected.equals(context1.getValues().get("policySegmentName")));
        assertTrue(expected.equals(context1.getValues().get("policySegmentName")));
    }

    @Test
    @DisplayName("Test: Test resolveToObject() with an invalid submission URI")
    public void testResolveToObjectInvalidURI() throws URISyntaxException, IOException {
        String uri = "http://example.com/1";
        Variable v = new Variable("foo.bar.baz.policy");
        v.setSegment("policySegmentName");
        v.setSegmentName("policySegment");

        Context context1 = Mockito.spy(context);
        when(mockPassClient.getObject(Policy.class, 1L))
            .thenReturn(mockPolicy);
        Mockito.doReturn(mockPassClient).when(context1).getNewClient();

        try {
            context1.resolveToObject(v, uri);
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof IOException);
            return;
        }

        fail("resolveToObject() should throw a RuntimeException when invalid submissions are given");
    }

    @Test
    @DisplayName("Test: Test resolveToObject() with an invalid submission")
    public void testResolveToObjectInvalid() throws IOException {
        String uri = "example.com/policies/1";
        Variable v = new Variable("foo.bar.baz.policy");
        v.setSegment("policySegmentName");
        v.setSegmentName("policySegment");

        Context context1 = Mockito.spy(context);
        when(mockPassClient.getObject(Policy.class, 1L))
            .thenReturn(mockPolicy);
        Mockito.doReturn(mockPassClient).when(context1).getNewClient();
        when(context1.getNewClient()).thenReturn(mockPassClient);
        try {
            context1.resolveToObject(v, uri);
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof JSONException);
            return;
        }

        fail("resolveToObject() should throw a RuntimeException when invalid submissions are given");
    }

    @Test
    @DisplayName("Test: Test resolveToObject() with a valid JSON string")
    public void testResolveToObjectValidJSON() throws IOException {
        String json = "{"
                + "\"policy-id\": \"1\","
                + "\"description\": \"test\","
                + "}";
        Variable v = new Variable("foo.bar.baz.policy");
        v.setSegment("policySegmentName");
        v.setSegmentName("policySegment");
        ResolvedObject expected = new ResolvedObject(json, new JSONObject(json));

        Context context1 = Mockito.spy(context);
        Mockito.doReturn(mockPassClient).when(context1).getNewClient();
        try {
            context1.resolveToObject(v, json);
        } catch (RuntimeException e) {
            fail("Unexpected Exception thrown for valid submission", e);
        }

        assertTrue(expected.equals(context1.getValues().get("policySegment")));
        assertTrue(expected.equals(context1.getValues().get("policySegmentName")));
    }

    @Test
    @DisplayName("Test: Test resolveToObject() with an invalid JSON string")
    public void testResolveToObjectInvalidJSON() throws IOException {
        String json = "{"
                + "\"policy-id\"1\","
                + "\"description\": \"test\","
                + "}";
        Variable v = new Variable("foo.bar.baz.policy");
        v.setSegment("policySegmentName");
        v.setSegmentName("policySegment");

        Context context1 = Mockito.spy(context);
        Mockito.doReturn(mockPassClient).when(context1).getNewClient();
        when(context1.getNewClient()).thenReturn(mockPassClient);
        try {
            context1.resolveToObject(v, json);
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof JSONException);
            return;
        }

        fail("resolveToObject() should throw a RuntimeException when invalid submissions are given");
    }

    /**
     * Unit tests for resolveToObjects() method in Context
     */
    @Test
    @DisplayName("Test: Test resolveToObjects() with a list of valid submission URIs")
    public void testResolveToObjectsValidURI() {
        List<String> uris = Arrays.asList(
                "http://example.com/policies/1",
                "http://example.com/policies/2");
        Variable v = new Variable("foo.bar.baz.policy");
        v.setSegment("policySegmentName");
        v.setSegmentName("policySegment");
        List<ResolvedObject> expected;

        Context context1 = Mockito.spy(context);

        try {
            expected = Arrays.asList(
                    new ResolvedObject("http://example.com/policies/1", mockPolicy),
                    new ResolvedObject("http://example.com/policies/2", mockPolicy));

            when(mockPassClient.getObject(Policy.class, 1L))
                    .thenReturn(mockPolicy);
            when(mockPassClient.getObject(Policy.class, 2L))
                    .thenReturn(mockPolicy);
            Mockito.doReturn(mockPassClient).when(context1).getNewClient();

            try {
                context1.resolveToObjects(v, uris);
            } catch (RuntimeException e) {
                fail("Unexpected Exception thrown for valid submission", e);
            }

            assertTrue(expected.equals(context1.getValues().get("policySegment")));
            assertTrue(expected.equals(context1.getValues().get("policySegmentName")));
        } catch (IOException e) {
            fail("Unexpected Exception thrown for valid submission", e);
        }
    }

    @Test
    @DisplayName("Test: Test resolveToObjects() with a list of invalid submission URIs")
    public void testResolveToObjectsInvalidURI() throws IOException {
        List<String> uris = Arrays.asList(
                "http://example.com/1",
                "http://example.com/2");
        Variable v = new Variable("foo.bar.baz.policy");
        v.setSegment("policySegmentName");
        v.setSegmentName("policySegment");

        when(mockPassClient.getObject(Policy.class, 1L))
            .thenReturn(mockPolicy);
        Context context1 = Mockito.spy(context);
        Mockito.doReturn(mockPassClient).when(context1).getNewClient();
        when(context1.getNewClient()).thenReturn(mockPassClient);
        try {
            context1.resolveToObjects(v, uris);
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof IOException);
            return;
        }

        fail("resolveToObjects() should throw a RuntimeException when invalid submissions are given");
    }

    @Test
    @DisplayName("Test: Test resolveToObjects() with a list of invalid submissions")
    public void testResolveToObjectsInvalid() throws IOException {
        List<String> uris = Arrays.asList(
                "example.com/policies/1",
                "example.com/policies/2");
        Variable v = new Variable("foo.bar.baz.policy");
        v.setSegment("policySegmentName");
        v.setSegmentName("policySegment");

        Context context1 = Mockito.spy(context);
        when(mockPassClient.getObject(Policy.class, 1L))
            .thenReturn(mockPolicy);
        Mockito.doReturn(mockPassClient).when(context1).getNewClient();
        when(context1.getNewClient()).thenReturn(mockPassClient);

        try {
            context1.resolveToObjects(v, uris);
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof JSONException);
            return;
        }

        fail("resolveToObjects() should throw a RuntimeException when invalid submissions are given");
    }

    @Test
    @DisplayName("Test: Test resolveToObjects() with a list of valid JSON strings")
    public void testResolveToObjectsValidJSON() throws  IOException {
        List<String> json = Arrays.asList(
                "{"
                        + "\"policy-id\": \"1\","
                        + "\"description\": \"test\","
                        + "}",
                "{"
                        + "\"policy-id\": \"2\","
                        + "\"description\": \"test\","
                        + "}");
        Variable v = new Variable("foo.bar.baz.policy");
        v.setSegment("policySegmentName");
        v.setSegmentName("policySegment");
        List<ResolvedObject> expected = Arrays.asList(
                new ResolvedObject(json.get(0), new JSONObject(json.get(0))),
                new ResolvedObject(json.get(1), new JSONObject(json.get(1))));

        Context context1 = Mockito.spy(context);
        Mockito.doReturn(mockPassClient).when(context1).getNewClient();
        when(context1.getNewClient()).thenReturn(mockPassClient);

        try {
            context1.resolveToObjects(v, json);
        } catch (RuntimeException e) {
            fail("Unexpected Exception thrown for valid submission", e);
        }

        assertTrue(expected.equals(context1.getValues().get("policySegment")));
        assertTrue(expected.equals(context1.getValues().get("policySegmentName")));
    }

    @Test
    @DisplayName("Test: Test resolveToObjects() with a list of invalid JSON strings")
    public void testResolveToObjectsInvalidJSON() throws IOException {
        List<String> json = Arrays.asList(
                "{"
                        + "\"policy-id\"1\","
                        + "\"description\": \"test\","
                        + "}",
                "{"
                        + "\"policy-id,"
                        + "\"description\": \"test\","
                        + "}");
        Variable v = new Variable("foo.bar.baz.policy");
        v.setSegment("policySegmentName");
        v.setSegmentName("policySegment");

        Context context1 = Mockito.spy(context);
        when(mockPassClient.getObject(Policy.class, 1L))
            .thenReturn(mockPolicy);
        Mockito.doReturn(mockPassClient).when(context1).getNewClient();
        when(context1.getNewClient()).thenReturn(mockPassClient);

        try {
            context1.resolveToObjects(v, json);
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof JSONException);
            return;
        }

        fail("resolveToObjects() should throw a RuntimeException when invalid submissions are given");
    }
}
