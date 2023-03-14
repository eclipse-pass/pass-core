/*
 * Copyright 2023 Johns Hopkins University
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
package org.eclipse.pass.policy.rules.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RepositoryRules {

    /**
     * string representation of id of repository
     */
    @JsonProperty("repository-id")
    private String id;

    /**
     * indicates whether the repository is selected
     */
    private boolean selected;

    /**
     * RepositoryData constructor
     */
    public RepositoryRules() {
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param repositoryRules the policyData to copy
     */
    public RepositoryRules(RepositoryRules repositoryRules) {
        this.id = repositoryRules.id;
        this.selected = repositoryRules.selected;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the selected
     */
    public boolean getselected() {
        return selected;
    }

    /**
     * @param selected the selected to set
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        RepositoryRules other = (RepositoryRules) obj;
        return Objects.equals(selected, other.selected)
               && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), id);
    }

}

