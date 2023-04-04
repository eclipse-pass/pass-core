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
package org.eclipse.pass.policy.services;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.yahoo.elide.RefreshableElide;
import org.eclipse.pass.policy.rules.model.PolicyRules;
import org.json.simple.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Unit tests for the PassPolicyRestController
 *
 * @author David McIntyre
 */
@DisplayName("PolicyServlet Tests")
public class PassPolicyServiceControllerTest {

    @Autowired
    RefreshableElide refreshableElide;

    // test doGetPolicy()
    @Test
    @DisplayName("Test: Test of doGet() method. Should return a response to a given request")
    void TestDoGetPolicy() throws Exception {
        // mock HttpServletRequest & HttpServletResponse & PolicyService
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        PolicyService mockPolicyService = mock(PolicyService.class);

        // mock the returned values of
        Map<String, String> headers = new HashMap();
        headers.put("Context-Path", "test.submission.com");
        headers.put("Query-String", "test.query");
        Enumeration<String> headerNames = Collections.enumeration(headers.keySet());

        when(mockRequest.getParameter("submission")).thenReturn("valid submission");
        when(mockRequest.getHeaderNames()).thenReturn(headerNames);
        when(mockRequest.getHeader("Context-Path")).thenReturn("test.submission.com");
        when(mockRequest.getHeader("Query-String")).thenReturn("test.query");

        // mock the returned value of policyService.findPolicies()
        String submission = System.getenv("PASS_CORE_BASE_URL") + "/policies/valid submission";
        List<PolicyRules> policies = new ArrayList<>();
        policies.add(new PolicyRules());
        when(mockPolicyService.findPolicies(submission, headers)).thenReturn(policies);
        when(mockPolicyService.createPolicyResponseJSONArray(policies)).thenReturn(new JSONArray());
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(mockResponse.getWriter()).thenReturn(writer);

        new PassPolicyServiceController(mockPolicyService).doGetPolicy(mockRequest, mockResponse);

        verify(mockRequest, atLeast(1)).getParameter("submission");
        verify(mockRequest, atLeast(1)).getHeaderNames();
        verify(mockPolicyService, atLeast(1)).findPolicies(submission, headers);
        writer.flush();
        assertTrue(stringWriter.toString().contains("Served at:"));
    }

    // test doPostPolicy ()
    @Test
    @DisplayName("Test: Test of doPost() method. Should return a response to a given request")
    void TestDoPostPolicy() throws Exception {
        // mock HttpServletRequest & HttpServletResponse
        PolicyService mockPolicyService = mock(PolicyService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        // mock the returned value of request.getParameter
        when(request.getHeader("Content-Type")).thenReturn("application/x-www-form-urlencoded");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        new PassPolicyServiceController(refreshableElide).doPostPolicy(request, response);
        verify(request, atLeast(1)).getHeader("Content-Type");
        assertTrue(stringWriter.toString().contains("Served at:"));
    }

}
