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
import java.util.HashSet;
import java.util.Set;

import com.yahoo.elide.RefreshableElide;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.PassClientResult;
import org.eclipse.pass.object.PassClientSelector;
import org.eclipse.pass.object.RSQL;
import org.eclipse.pass.object.model.Funder;
import org.eclipse.pass.object.model.Grant;
import org.eclipse.pass.object.model.Policy;
import org.eclipse.pass.object.model.Repository;
import org.eclipse.pass.object.model.Submission;
import org.eclipse.pass.object.model.User;

/**
 * Simple implementation of the Policy Service interface. Provides Sets of policies or repositories
 *
 * @author jrm
 */
public class PolicyServiceSimpleImpl implements PolicyService {

    private RefreshableElide refreshableElide;
    private String institutionalPolicyId = System.getenv("INSTITUTIONAL_POLICY_ID");
    private String institutionalRepositoryId = System.getenv("INSTITUTIONAL_REPOSITORY_ID");

    public PolicyServiceSimpleImpl(RefreshableElide refreshableElide) {
        this.refreshableElide = refreshableElide;
    }

    @Override
    public Set<Policy> findPoliciesForSubmission(Long submissionId, Principal userPrincipal) {
        Set<Policy> policies = new HashSet<>(); //use Set to avoid duplicates
        try (PassClient passClient = PassClient.newInstance(refreshableElide)) {
            Submission submission = passClient.getObject(Submission.class, submissionId);
            for (Grant grant : submission.getGrants()) {
                for (Funder funder : getFunders(grant)) {
                    if (funder.getPolicy() != null) {
                        policies.add(funder.getPolicy());
                    }
                }
            }

            //If the user is an affiliate of the institution, add the institutional repository
            Long policyId = null;
            try {
                policyId = Long.parseLong(institutionalPolicyId);
            } catch (NumberFormatException e) {
                System.out.println("MOO");
            }

            if (isInstitutionalUser(userPrincipal) && policyId != null) {
                Policy policy = passClient.getObject(Policy.class, policyId);
                if (policy != null) {
                    policies.add(policy);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return policies;
    }

    @Override
    public Set<Repository> findRepositoriesForSubmission(Long submissionId, Principal userPrincipal) {
        Set<Repository> repositories = new HashSet<>(); //use Set to avoid duplicates
        for (Policy policy : findPoliciesForSubmission(submissionId, userPrincipal)) {
            repositories.addAll(policy.getRepositories());
        }

        //If the user is an affiliate of the institution, add the institutional repository
        if (isInstitutionalUser(userPrincipal)) {
            try (PassClient passClient = PassClient.newInstance(refreshableElide)) {
                Long repositoryId = null;
                try {
                   repositoryId = Long.parseLong(institutionalRepositoryId);
                } catch (NumberFormatException e) {
                    System.out.println("MOO");
                }
                if (repositoryId != null) {
                    Repository repository = passClient.getObject(Repository.class, repositoryId);
                    if (repository != null) {
                        repositories.add(repository);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return repositories;
    }

    /**
     * A convenience method
     *
     * @param grant - the Grant to find Funders for
     * @return - the Set of Funders for the provided Grant
     */
    private Set<Funder> getFunders(Grant grant) {
        Set<Funder> funders = new HashSet<>(); // use Set to avoid duplicates
        Funder primaryFunder = grant.getPrimaryFunder();
        Funder directFunder = grant.getDirectFunder();

        if (primaryFunder != null) {
            funders.add(primaryFunder);
        }
        if (directFunder != null) {
            funders.add(directFunder);
        }

        return funders;
    }

    /**
     * A method to decide whether the Principal is an affiliate. If the user is found unambiguously
     * in the data store, then the user is assumed to be an affiliate.
     *
     * @param userPrincipal - the Principal on the HttpRequest in the controller
     * @return a boolean indicating whether the user is an affiliate
     */
    private boolean isInstitutionalUser(Principal userPrincipal) {
        if (userPrincipal == null || userPrincipal.getName() == null) {

            String user_name = userPrincipal.getName();

            try (PassClient client = PassClient.newInstance(refreshableElide)) {
                PassClientSelector<User> selector = new PassClientSelector<>(User.class);
                selector.setFilter(RSQL.equals("username", user_name));
                PassClientResult<User> result = client.selectObjects(selector);

                //if we find an unambiguous match, we interpret this as the user being an affiliate
                if (result.getObjects().size() == 1) {
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
