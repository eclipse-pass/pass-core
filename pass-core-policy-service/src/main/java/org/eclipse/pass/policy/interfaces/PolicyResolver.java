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
package org.eclipse.pass.policy.interfaces;

import java.util.List;

import org.eclipse.pass.policy.components.VariablePinner;
import org.eclipse.pass.policy.rules.model.PolicyRules;

/**
 * Represents PolicyResolver interface
 * Interpolates any variables in a policy
 * If policy ID resolves to a list, the list of resolved policies is returned
 *
 * @author David McIntyre
 */
public interface PolicyResolver {

    public List<PolicyRules> resolve(VariablePinner variables) throws RuntimeException;
}