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

import java.security.Principal;
import java.util.Set;

import org.eclipse.pass.object.model.Policy;
import org.eclipse.pass.object.model.Repository;

/**
 * Interface for Policy Sevice implementations.
 *
 * @author jrm
 */
public interface PolicyService {

    /**
     *
     * @param submissionId - the string value of the Submission ID
     * @param userPrincipal - the Principal from the Http Request in the Controller
     * @return a Set of found Policies
     */
    Set<Policy> findPoliciesForSubmission(Long submissionId, Principal userPrincipal);

    /**
     *
     * @param submissionId - the string value of the Submission ID
     * @param userPrincipal - - the Principal from the Http Request in the Controller
     * @return a Set of found Repositoriess
     */
    Set<Repository> findRepositoriesForSubmission(Long submissionId, Principal userPrincipal);

}
