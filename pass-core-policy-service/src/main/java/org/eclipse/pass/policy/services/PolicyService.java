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

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.yahoo.elide.RefreshableElide;
import org.eclipse.pass.object.model.Policy;
import org.eclipse.pass.object.model.Repository;
import org.eclipse.pass.policy.rules.Context;
import org.eclipse.pass.policy.rules.DSL;

/**
 * Represents PolicyService object.
 * Handles business logic needed to complete requests and provide responses to
 * Servlets.
 *
 * @author David McIntyre
 */
public class PolicyService {

    RefreshableElide refreshableElide;

    public PolicyService(RefreshableElide refreshableElide) {
        this.refreshableElide = refreshableElide;
    }

    //public PolicyServiceImpl(PassClient client) {
   //     this.passClient = client;
    //}

    public List<Policy> findPolicies(String submission, Map<String, String> headers) throws RuntimeException {
        Context context = new Context(submission, headers, refreshableElide);
        DSL dsl = new DSL();
        try {
            return dsl.resolve(context);
        } catch (RuntimeException e) {
            throw new RuntimeException("Could not resolve policy rule", e);
        }
    }

    // public void sendPolicies() {

    // }

    public List<Repository> findRepositories(URI submissionURI, Map<String, Object> headers) throws RuntimeException {
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