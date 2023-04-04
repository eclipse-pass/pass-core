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

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.json.JsonArray;

import com.yahoo.elide.RefreshableElide;
import org.eclipse.pass.policy.interfaces.PolicyResolver;
import org.eclipse.pass.policy.rules.Context;
import org.eclipse.pass.policy.rules.Validator;
import org.eclipse.pass.policy.rules.model.PolicyRules;
import org.eclipse.pass.policy.rules.model.RepositoryRules;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Represents PolicyService object.
 * Handles business logic needed to complete requests and provide responses to
 * Servlets.
 *
 * @author David McIntyre
 */
public class PolicyService {

    RefreshableElide refreshableElide;
    PolicyResolver policyResolver;

    public PolicyService(RefreshableElide refreshableElide) throws IOException {
        this.refreshableElide = refreshableElide;
        String rulesDoc = System.getenv("POLICY_RULES_FILE") != null ?
                          System.getenv("POLICY_RULES_FILE") :
                          "src/main/resources/policies/aws.json";
        this.policyResolver = new Validator().validate(rulesDoc);
    }

    public List<PolicyRules> findPolicies(String submission, Map<String, String> headers) throws RuntimeException,
        IOException {
        Context context = new Context(submission, headers, refreshableElide);

        return policyResolver.resolve(context);
    }

    public List<RepositoryRules> findRepositories(URI submissionURI, Map<String, Object> headers)
        throws RuntimeException {
        return null;
    }

    public JSONArray createPolicyResponseJSONArray(List<PolicyRules> policyRulesList) {
        JSONArray jasonArray = new JSONArray();
        for ( PolicyRules policyRules : policyRulesList) {
            JSONObject policyObject = new JSONObject();
            policyObject.put("policy-id", policyRules.getId());
            policyObject.put("type", policyRules.getType());
            jasonArray.add(policyObject);
        }

        return jasonArray;
    }

    public JsonArray createRepositoryResponseJsonArray() {
        return null;

    }
    // public void reconcileRepositories() {

    // }

    // Policy Service Functions
    // public void requestPolicies() {
    // }

    // public void requestRepositories() {
    // }

    // public void doRequest(){
    // }
}