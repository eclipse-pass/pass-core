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
package org.eclipse.pass.policy.components;

import java.io.IOException;

import org.eclipse.pass.policy.interfaces.VariableResolver;

/**
 * Represents VariablePinner interface
 * Pins a given value to a given variable
 *
 * @author David McIntyre
 */
public abstract class VariablePinner implements VariableResolver {

    /**
     * Pins a given value to a given variable.
     * For pinning of context values, Objects must be of type URI.
     *
     * @param variable - the URI to pin to
     * @param value    - the value to be pinned
     * @return VariablePinner - the pinned object to be returned
     * @throws IOException - incorrect object types supplied
     */
    public abstract VariablePinner pin(Object variable, Object value) throws IOException;
}
