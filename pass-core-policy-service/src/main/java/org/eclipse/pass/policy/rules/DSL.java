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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.pass.policy.components.VariablePinner;
import org.eclipse.pass.policy.interfaces.PolicyResolver;
import org.eclipse.pass.policy.rules.model.PolicyRules;
import org.eclipse.pass.policy.rules.util.PolicyRulesUtil;

/**
 * Represents the DSL object
 * DSL encapsulates to a policy rules document
 *
 * @author David McIntyre
 */
public class DSL implements PolicyResolver {

    @JsonProperty("$schema")
    private String schema; // json:"$schema"

    @JsonProperty("policy-rules")
    private List<PolicyRules> policyRulesList; // json:"policy-rules"

    /**
     * DSL.resolve()
     * Resolves a list of applicable Policies using a provided ruleset against a
     * database of policies that are instantiated at runtime.
     *
     * @param variables - the ruleset to be resolved against
     * @return List&lt;Policy&gt; - the List of resolved policies
     * @throws RuntimeException - Policy rule could not be resolved
     */
    @Override
    public List<PolicyRules> resolve(VariablePinner variables) throws RuntimeException {
        List<PolicyRules> resolvedPolicies = new ArrayList<>();

        for (PolicyRules policyRules : policyRulesList) {
            try {
                List<PolicyRules> resolved = PolicyRulesUtil.resolve(policyRules, variables);

                // If a resolved policy or policies exist, append to final list
                if (resolved.size() > 0) {
                    resolvedPolicies.addAll(resolved);
                }

            } catch (RuntimeException e) {
                throw new RuntimeException("Could not resolve policy rule", e);
            }
        }

        return PolicyRulesUtil.uniquePolicies(resolvedPolicies);
    }

    public String getSchema(){ return this.schema;}

    public void setSchema(String schema) { this.schema = schema;}

    public List<PolicyRules> getPolicyRuleList() { return this.policyRulesList; }

    public void setPolicyRuleList(List<PolicyRules> policyRulesList) {this.policyRulesList = policyRulesList;}
}
