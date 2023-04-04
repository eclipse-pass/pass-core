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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PolicyRules {

    /**
     * string representation of id of policy
     */
    @JsonProperty("policy-id")
    private String id;

    /**
     * Several sentence description of policy
     */
    private String description;

    /**
     * A link to the type of policy resource
     */
    private String type;

    /**
     * List of RepositoryRules that can satisfying this policy
     */
    private List<RepositoryRules> repositories = new ArrayList<>();

    /**
     * the conditions which apply
     */
    private List<Condition> conditions = new ArrayList<>();

    /**
     * PolicyRule constructor
     */
    public PolicyRules() {
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param policyRules the policyData to copy
     */
    public PolicyRules(PolicyRules policyRules) {
        this.id = policyRules.id;
        this.description = policyRules.description;
        this.type = policyRules.type;
        this.repositories = new ArrayList<>(policyRules.repositories);
        this.conditions = policyRules.conditions;
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
     * @return the policy description
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the policy resource type
     */
    public String getType() {
        return this.type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the list of RepositoryRules
     */
    public List<RepositoryRules> getRepositories() {
        return repositories;
    }

    /**
     * @param repositories list of Repository to set
     */
    public void setRepositories(List<RepositoryRules> repositories) {
        this.repositories = repositories;
    }

    /**
     * @return the list of conditions
     */
    public List<Condition> getConditions() {
        return conditions;
    }

    /**
     * @param conditions list of conditions to set
     */
    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
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
        PolicyRules other = (PolicyRules) obj;
        return Objects.equals(description, other.description) && Objects.equals(type, other.type)
               && Objects.equals(conditions, other.conditions) && Objects.equals(repositories, other.repositories)
               && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), id);
    }

    @Override
    public String toString() {
        return "Policy [id=" + id + ", description=" + description + ", type=" + type
               + ", repositories=" + repositories + ", conditions=" + conditions + ", id=" + getId() + "]";
    }

}

