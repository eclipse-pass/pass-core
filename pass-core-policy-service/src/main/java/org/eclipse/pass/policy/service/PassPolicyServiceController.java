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
import java.security.Principal;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.yahoo.elide.RefreshableElide;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.model.Policy;
import org.eclipse.pass.object.model.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This class defines Policy and Repository service endpoints and orchestrates responses
 *
 * @author jrm
 */
@RestController
public class PassPolicyServiceController {

    private static final long serialVersionUID = 1L;
    private String institutionalPolicyId = System.getenv("INSTITUTIONAL_POLICY_ID");
    private String institutionalRepositoryId = System.getenv("INSTITUTIONAL_REPOSITORY_ID");
    private static final Logger LOG = LoggerFactory.getLogger(PassPolicyServiceController.class);
    private final PolicyService policyService;

    @Autowired
    private RefreshableElide refreshableElide;

    @Autowired
    public PassPolicyServiceController(RefreshableElide refreshableElide) throws IOException {
        this.policyService = new PolicyServiceSimpleImpl(refreshableElide);
    }

    public PassPolicyServiceController(PolicyService policyService) {
        this.policyService = policyService;
    }

    /**
     * Handles incoming GET requests to the /policy/policies endpoint
     *
     * @param request the incoming request
     * @param response the outgoing response
     * @throws IOException if an IO exception occurs
     */
    @GetMapping("/policy/policies")
    public void doGetPolicies(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        LOG.info("Servicing new request......");
        LOG.debug("Context path: " + request.getContextPath() + "; query string " + request.getQueryString());

        // retrieve submission ID from request
        String submissionParameter = request.getParameter("submission");
        Long submissionId = Long.parseLong(submissionParameter);
        Principal userPrincipal = request.getUserPrincipal();

        // handle empty or invalid request submission error
        if (submissionId == null) {
            LOG.error("Submission query parameter missing or invalid");
            set_error_response(response, "Missing or invalid submission parameter: " +
                                         "must be a String representation of a Long", HttpStatus.BAD_REQUEST);
            return;
        }

        Set<Policy> policies = policyService.findPoliciesForSubmission(submissionId, userPrincipal);

        JsonArrayBuilder jab = Json.createArrayBuilder();
        for (Policy policy : policies) {
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add("id", policy.getId().toString());
            if ( policy.getId().toString().equals(institutionalPolicyId)) {
                job.add("type", "institution");
            } else {
                job.add("type", "funder");
            }
            jab.add(job.build());
        }
        set_response(response, (JsonObject) jab.build(), HttpStatus.OK);
    }

    /**
     * Handles incoming GET requests to the /policy/repositories endpoint
     *
     * @param request the incoming request
     * @param response the outgoing response
     * @throws IOException if an IO exception occurs
     */
    @GetMapping("/policy/repositories")
    public void doGetRepositories(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        LOG.info("Servicing new request......");
        LOG.debug("Context path: " + request.getContextPath() + "; query string " + request.getQueryString());

        // retrieve submission parameter value from request
        String submissionParameterValue = request.getParameter("submission");
        Long submissionId = Long.parseLong(submissionParameterValue);
        Principal userPrincipal = request.getUserPrincipal();

        // handle empty or invalid request submission error
        if (submissionId == null) {
            set_error_response(response, "Missing or invalid submission parameter: " +
                                         "must be a String representation of a Long", HttpStatus.BAD_REQUEST);
            return;
        }
        Set<Repository> repositories = policyService.findRepositoriesForSubmission(submissionId, userPrincipal);
        JsonArrayBuilder jab = Json.createArrayBuilder();
        for (Repository repository : repositories) {
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add("url", PassClient.getUrl(refreshableElide, repository));
            if ( repository.getId().toString().equals(institutionalRepositoryId)) {
                job.add("selected", "true");
            } else {
                job.add("selected", "false");
            }
            jab.add(job.build());
        }
        set_response(response, (JsonObject) jab.build(), HttpStatus.OK);
    }

    private void set_response(HttpServletResponse response, JsonObject obj, HttpStatus status) throws IOException {
        response.getWriter().print(obj.toString());
        response.setStatus(status.value());
    }

    private void set_error_response(HttpServletResponse response, String message,
                                    HttpStatus status) throws IOException {
        JsonObject obj = Json.createObjectBuilder().add("message", message).build();

        set_response(response, obj, status);
        LOG.error(message);
    }

}
