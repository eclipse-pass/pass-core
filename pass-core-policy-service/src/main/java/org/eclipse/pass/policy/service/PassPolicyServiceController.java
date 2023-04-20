/*
 *
 * Copyright 2023 Johns Hopkins University
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
package org.eclipse.pass.policy.service;

import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.yahoo.elide.RefreshableElide;
import org.eclipse.pass.policy.rules.model.PolicyRules;
import org.eclipse.pass.policy.rules.model.RepositoryRules;
import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This class defines Policy and Repository service endpoints and orchestrates responses
 *
 * @author jrm
 */
@RestController
public class  PassPolicyServiceController {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(PassPolicyServiceController.class);
    private final PolicyService policyService;

    public PassPolicyServiceController(RefreshableElide refreshableElide) throws IOException {
        this.policyService = new PolicyService(refreshableElide);
    }

    public PassPolicyServiceController(PolicyService policyService) {
        this.policyService = policyService;
    }

    public String baseUrl = System.getenv("PASS_CORE_BASE_URL");

    /**
     * Handles incoming GET requests to the /policies endpoint
     *
     * @param request the incoming request
     * @param response the outgoing response
     * @throws IOException if an IO exception occurs
     */
    @GetMapping("/policy/policies")
    public void doGetPolicy(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        System.out.println("MOOOOO - service called");
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        LOG.info("Servicing new request......");
        LOG.debug("Context path: " + request.getContextPath() + "; query string " + request.getQueryString());

        // retrieve submission URI from request
        String submissionParameter = request.getParameter("submission");
        String submission;

        // handle empty request submission error
        if (submissionParameter == null) {
            LOG.error("No submission query param provided");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No submission query param provided");
            return;
        } else {
            submission = baseUrl + "/policies/" + submissionParameter;
        }

        // retrieve map of headers and values from request
        Enumeration<String> headerNames = request.getHeaderNames();
        Map<String, String> headers = new HashMap<>();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String key = headerNames.nextElement();
                String value = request.getHeader(key);
                headers.put(key, value);
            }
        }

        // findPolicies() relevant to request
        try  {
            List<PolicyRules> policyRulesList = policyService.findPolicies(submission, headers);
            JSONArray policyResourceArray = policyService.createPolicyResponseJSONArray(policyRulesList);
            response.getWriter().append(policyResourceArray.toString());
            LOG.info("Returning policies result for submission " + submission);
            response.setStatus(200);
        } catch (RuntimeException e) {
            LOG.error("Unable to find relevant policies", e);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

       // response.getWriter().append("Served at: ").append(request.getContextPath());
    }


    /**
     * Handles incoming POST requests for the /policies endpoint
     *
     * @param request the incoming request
     * @param response the outgoing response
     * @throws IOException if an IO exception occurs
     */
    @PostMapping("/policy/policies")
    public void doPostPolicy(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        // handle wrong request content-type
        if (request.getHeader("Content-Type") != "application/x-www-form-urlencoded") {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                               "Expected media type: application/x-www-form-urlencoded but got "
                               + request.getHeader("Content-Type"));
        }

        response.getWriter().append("Served at: ").append(request.getContextPath());
    }

    /**
     * Handles incoming GET requests to the /repositories endpoint
     *
     * @param request the incoming request
     * @param response the outgoing response
     * @throws IOException if an IO exception occurs
     */
    @GetMapping("/policy/repositories")
    protected void doGetRepository(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        LOG.info("Servicing new request......");
        LOG.debug("Context path: " + request.getContextPath() + "; query string " + request.getQueryString());

        // retrieve submission URI from request
        URI submission = URI.create( baseUrl + "/repositories/" + request.getParameter("submission"));

        // handle empty request submission error
        if (submission == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No submission query param provided");
        }

        // retrieve map of headers and values from request
        Enumeration<String> headerNames = request.getHeaderNames();
        Map<String, Object> headers = new HashMap<String, Object>();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String key = (String) headerNames.nextElement();
                String value = request.getHeader(key);
                headers.put(key, value);
            }
        }

        // call to policy service
        try {
            List<RepositoryRules> repositories = policyService.findRepositories(submission, headers);
        } catch (RuntimeException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }

        response.getWriter().append("Served at: ").append(request.getContextPath());
    }

    /**
     * Handles incoming POST requests to the /repositories endpoint
     *
     * @param request the incoming request
     *  @param response the outgoing response
     * @throws IOException if an IO exception occurs
     */
    @PostMapping("/policy/repositories")
    protected void doPostRepository(HttpServletRequest request, HttpServletResponse response)
        throws  IOException {
        // handle wrong request content-type
        if (request.getHeader("Content-Type") != "application/x-www-form-urlencoded") {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                               "Expected media type: application/x-www-form-urlencoded but got "
                               + request.getHeader("Content-Type"));
        }

        response.getWriter().append("Served at: ").append(request.getContextPath());
    }
}


