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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.pass.policy.interfaces.VariableResolver;
import org.eclipse.pass.policy.rules.Variable;
import org.eclipse.pass.policy.rules.model.RepositoryRules;

/**
 * Represents the RepositoryRules object
 * RepositoryRules resolves rulesets to return relevant policies or repositories
 * in
 * a repository.
 *
 * @author David McIntyre
 */
public class RepositoryRulesUtil {

    private RepositoryRulesUtil() {
        //empty constructor
    }

    /**
     * Resolve interpolates any variables in a repository and resolves against a
     * ruleset
     * to a list of applicable Repositories.
     *
     *
     * @param repoData
     * @param variables - the ruleset to be resolved against
     * @return List&lt;Repository&gt; - the List of resolved Repositories
     * @throws RuntimeException - Repository could not be resolved
     */
    static public List<RepositoryRules> resolve(RepositoryRules repoData, VariableResolver variables)
        throws RuntimeException {
        List<RepositoryRules> resolvedRepos = new ArrayList<>();
        RepositoryRules repositoryRules = repoData;

        if (Variable.isVariable(repositoryRules.getId())) {

            // resolve repositoryData ID/s
            List<String> resolvedIDs = new ArrayList<>();

            try {
                resolvedIDs.addAll(variables.resolve(repositoryRules.getId()));

                for (String id : resolvedIDs) {
                    RepositoryRules resolved = new RepositoryRules();
                    resolved.setId(id);
                   // resolved.setSelected(true);
                    resolvedRepos.add(resolved);
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("Could not resolve property ID " + repositoryRules.getId().toString(), e);
            }
        } else {
            resolvedRepos.add(repositoryRules);
        }

        return resolvedRepos;
    }

}
