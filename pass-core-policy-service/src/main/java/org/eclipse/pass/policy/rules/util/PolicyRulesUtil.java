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
package org.eclipse.pass.policy.rules.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.pass.policy.components.VariablePinner;
import org.eclipse.pass.policy.rules.Variable;
import org.eclipse.pass.policy.rules.model.Condition;
import org.eclipse.pass.policy.rules.model.PolicyRules;
import org.eclipse.pass.policy.rules.model.RepositoryRules;
import org.json.JSONObject;

/**
 * Represents the PolicyRules object
 * PolicyRules resolves rulesets to return relevant policies or repositories in
 * a repository.
 *
 * @author David McIntyre
 */
public class PolicyRulesUtil {

    private PolicyRulesUtil(){
        //empty constructor
    }

    /**
     * Resolve interpolates any variables in a policy and resolves against a ruleset
     * to a list of applicable Policies.
     *
     * @param policyRules - the PolicyData
     * @param variables - the ruleset to be resolved against
     * @return List&lt;Policy&gt; - the List of resolved Policies
     * @throws RuntimeException - Policy could not be resolved
     */
    public static List<PolicyRules> resolve(PolicyRules policyRules, VariablePinner variables)
        throws RuntimeException {
        List<PolicyRules> resolvedPolicies = new ArrayList<>();
        List<RepositoryRules> resolvedRepos = new ArrayList<>();
        List<Condition> conditions = policyRules.getConditions();

        // If the policy ID is a variable, we need to resolve/expand it. If the result
        // is a list of IDs, we return a list of policies, each one with an ID from the
        // list.
        if (Variable.isVariable(policyRules.getId())) {

            // resolve policy IDs
            List<String> resolvedIDs = new ArrayList<>();
            try {
                resolvedIDs.addAll(variables.resolve(policyRules.getId()));

                String curID = resolvedIDs.get(0); // for RuntimeException handling
                try {

                    for (String id : resolvedIDs) {
                        // Now that we have a concrete ID, resolve any other variables elsewhere in the
                        // policy. Some of them may depend on knowing the ID we just found.
                        //
                        // We take a shortcut by pinning only the ID variable, meaning ${foo.bar.baz.id}
                        // is pinned, but ${foo.bar} is not.
                        curID = id; // for exception handling
                        PolicyRules resolved = new PolicyRules();
                        resolved.setId(policyRules.getId());
                        resolved.setDescription(policyRules.getDescription());
                        resolved.setType(policyRules.getType());
                        resolved.setRepositories(policyRules.getRepositories());
                        resolved.setConditions(policyRules.getConditions());
                        resolve(resolved, variables.pin(policyRules.getId(), id));

                        resolvedPolicies.add(resolved);
                    }
                } catch ( IOException | RuntimeException e) {
                    throw new RuntimeException("Could not resolve policy rule for " + curID, e);
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("Could not resolve property ID " + policyRules.getId(), e);
            }

        } else {

            // Individual policy. Resolve the repositories section, and filter by condition
            // to see if it is applicable
            try {
                resolvedRepos.addAll(resolveRepositories(policyRules, variables));
                policyRules.setRepositories(resolvedRepos);

                try {
                    Boolean valid = ConditionUtil.apply(new JSONObject(conditions), variables);

                    if (valid) {
                        resolvedPolicies.add(policyRules);
                    }
                } catch (RuntimeException e) {
                    throw new RuntimeException("Failed to apply conditions to policy " + policyRules.getId(), e);
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("Could not resolve repositories in policy " + policyRules.getId(), e);
            }
        }

        return uniquePolicies(resolvedPolicies);
    }

    /**
     * Receives a PolicyRule and a set of variables to resolve against. Returns a list
     * of applicable policies to the given policy.
     *
     * @param policyRules    - the parent PolicyData for repositories
     * @param variables - the variables to resolved against
     * @return List&lt;Repository&gt; - the list of resolved repositories
     * @throws RuntimeException - repositories could not be resolved
     */
    public static List<RepositoryRules> resolveRepositories(PolicyRules policyRules, VariablePinner variables)
        throws RuntimeException {
        List<RepositoryRules> resolvedRepos = new ArrayList<>();

        try {
            for (RepositoryRules repo : policyRules.getRepositories()) {
                List<RepositoryRules> repos = RepositoryRulesUtil.resolve(repo, variables);

                resolvedRepos.addAll(repos);
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Could not resolve repositories for " + policyRules.getId(), e);
        }

        return resolvedRepos;
    }

    /**
     * Applies conditions present if any for the current policy. Evaluates
     * conditions on supplied variables. Returns true if all conditions are met,
     * otherwise returns false.
     *
     * @param variables - the variables to check conditions against
     * @return Boolean - true if variables meet all conditions, false otherwise
     * @throws RuntimeException - Condition could not be resolved
     */
 /*   private Boolean applyConditions(JSONObject conditionObject, VariablePinner variables) throws RuntimeException {
        Boolean valid;
        try {
            for (String cond : conditionObject.keySet()) {
                valid = cond.apply(variables);

                if (!valid) {
                    return false;
                }
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Invalid condition", e);
        }

        return true;
    }
*/
    /**
     * uniquePolicies()
     * Removes duplicates from a given list of Policies and returns the unique List.
     *
     * @param policies - the list of policies with potential duplicates
     * @return List&lt;Policy&gt; - the list of unique policies
     */
    public static List<PolicyRules> uniquePolicies(List<PolicyRules> policies) {

        if (policies.size() < 2) {
            return policies;
        }

        List<PolicyRules> uniquePolicies = policies.stream().distinct().collect(Collectors.toList());

        return uniquePolicies;
    }
}
