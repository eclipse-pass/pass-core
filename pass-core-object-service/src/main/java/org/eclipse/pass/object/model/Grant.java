/*
 * Copyright 2018 Johns Hopkins University
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
package org.eclipse.pass.object.model;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.yahoo.elide.annotation.Include;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.eclipse.pass.object.converter.AwardStatusToStringConverter;

/**
 * Grant model for the PASS system
 *
 * @author Karen Hanson
 */

@Include
@Entity
@Table(name = "pass_grant")
public class Grant extends PassEntity {

    /**
     * Award number from funder
     */
    private String awardNumber;

    /**
     * Status of award
     */
    @Convert(converter = AwardStatusToStringConverter.class)
    private AwardStatus awardStatus;

    /**
     * A local key assigned to the Grant within the researcher's institution to support matching
     * between PASS and a local system. In the case of JHU this is the key assigned by COEUS
     */
    private String localKey;

    /**
     * Title of the research project
     */
    private String projectName;

    /**
     * The funder.id of the sponsor that is the original source of the funds
     */
    @ManyToOne
    private Funder primaryFunder;

    /**
     * The funder.id of the organization from which funds are directly received
     */
    @ManyToOne
    private Funder directFunder;

    /**
     * The User who is the Principal investigator
     */
    @ManyToOne
    private User pi;
    /**
     * List of User who are the co-principal investigators
     */
    @ManyToMany(fetch = FetchType.EAGER)
    private List<User> coPis = new ArrayList<>();

    /**
     * Date the grant was awarded
     */
    private ZonedDateTime awardDate;

    /**
     * Date the grant started
     */
    private ZonedDateTime startDate;

    /**
     * Date the grant ended
     */
    private ZonedDateTime endDate;

    /**
     * Grant constructor
     */
    public Grant() {
    }

    /**
     * Copy constructor, this will copy the values of the object provided into the new object
     *
     * @param grant the grant to copy
     */
    public Grant(Grant grant) {
        super(grant);
        this.awardNumber = grant.awardNumber;
        this.awardStatus = grant.awardStatus;
        this.localKey = grant.localKey;
        this.projectName = grant.projectName;
        this.primaryFunder = grant.primaryFunder;
        this.directFunder = grant.directFunder;
        this.pi = grant.pi;
        this.coPis = new ArrayList<User>(grant.coPis);
        this.awardDate = grant.awardDate;
        this.startDate = grant.startDate;
        this.endDate = grant.endDate;
    }

    /**
     * @return the awardNumber
     */
    public String getAwardNumber() {
        return awardNumber;
    }

    /**
     * @param awardNumber the awardNumber to set
     */
    public void setAwardNumber(String awardNumber) {
        this.awardNumber = awardNumber;
    }

    /**
     * @return the awardStatus
     */
    public AwardStatus getAwardStatus() {
        return awardStatus;
    }

    /**
     * @param awardStatus the awardStatus to set
     */
    public void setAwardStatus(AwardStatus awardStatus) {
        this.awardStatus = awardStatus;
    }

    /**
     * @return the localKey
     */
    public String getLocalKey() {
        return localKey;
    }

    /**
     * @param localKey the localKey to set
     */
    public void setLocalKey(String localKey) {
        this.localKey = localKey;
    }

    /**
     * @return the projectName
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * @param projectName the projectName to set
     */
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * @return the primaryFunder
     */
    public Funder getPrimaryFunder() {
        return primaryFunder;
    }

    /**
     * @param primaryFunder the primaryFunder to set
     */
    public void setPrimaryFunder(Funder primaryFunder) {
        this.primaryFunder = primaryFunder;
    }

    /**
     * @return the directFunder
     */
    public Funder getDirectFunder() {
        return directFunder;
    }

    /**
     * @param directFunder the directFunder to set
     */
    public void setDirectFunder(Funder directFunder) {
        this.directFunder = directFunder;
    }

    /**
     * @return the pi
     */
    public User getPi() {
        return pi;
    }

    /**
     * @param pi the pi to set
     */
    public void setPi(User pi) {
        this.pi = pi;
    }

    /**
     * @return the coPis
     */
    public List<User> getCoPis() {
        return coPis;
    }

    /**
     * @param coPis the coPis to set
     */
    public void setCoPis(List<User> coPis) {
        this.coPis = coPis;
    }

    /**
     * @return the awardDate
     */
    public ZonedDateTime getAwardDate() {
        return awardDate;
    }

    /**
     * @param awardDate the awardDate to set
     */
    public void setAwardDate(ZonedDateTime awardDate) {
        this.awardDate = awardDate;
    }

    /**
     * @return the startDate
     */
    public ZonedDateTime getStartDate() {
        return startDate;
    }

    /**
     * @param startDate the startDate to set
     */
    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    /**
     * @return the endDate
     */
    public ZonedDateTime getEndDate() {
        return endDate;
    }

    /**
     * @param endDate the endDate to set
     */
    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
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
        Grant other = (Grant) obj;
        return Objects.equals(awardDate == null ? null : awardDate.toInstant(),
                    other.awardDate == null ? null : other.awardDate.toInstant())
               && Objects.equals(awardNumber, other.awardNumber)
               && awardStatus == other.awardStatus && listEquals(coPis, other.coPis)
               && Objects.equals(directFunder, other.directFunder)
               && Objects.equals(endDate == null ? null : endDate.toInstant(),
                    other.endDate == null ? null : other.endDate.toInstant())
               && Objects.equals(localKey, other.localKey) && Objects.equals(pi, other.pi)
               && Objects.equals(primaryFunder, other.primaryFunder) && Objects.equals(projectName, other.projectName)
               && Objects.equals(startDate == null ? null : startDate.toInstant(),
                       other.startDate == null ? null : other.startDate.toInstant());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), awardNumber, localKey);
    }

    @Override
    public String toString() {
        return "Grant [awardNumber=" + awardNumber + ", awardStatus=" + awardStatus + ", localKey=" + localKey
                + ", projectName=" + projectName + ", primaryFunder=" + primaryFunder + ", directFunder=" + directFunder
                + ", pi=" + pi + ", coPis=" + coPis + ", awardDate=" + awardDate + ", startDate=" + startDate
                + ", endDate=" + endDate + ", id=" + getId() + "]";
    }
}
