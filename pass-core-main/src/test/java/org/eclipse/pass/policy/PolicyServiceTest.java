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
package org.eclipse.pass.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;

import com.yahoo.elide.RefreshableElide;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.pass.main.ShibIntegrationTest;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.model.AggregatedDepositStatus;
import org.eclipse.pass.object.model.Funder;
import org.eclipse.pass.object.model.Grant;
import org.eclipse.pass.object.model.Policy;
import org.eclipse.pass.object.model.Repository;
import org.eclipse.pass.object.model.Source;
import org.eclipse.pass.object.model.Submission;
import org.eclipse.pass.object.model.SubmissionStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PolicyServiceTest extends ShibIntegrationTest {

    @Autowired
    protected RefreshableElide refreshableElide;

    private Submission submission;

    Repository repository1 = new Repository();
    Repository repository2 = new Repository();
    Repository repository3 = new Repository();
    Policy policy1 = new Policy();
    Funder funder1 = new Funder();
    Policy policy2 = new Policy();
    Policy policy3 = new Policy();
    Funder funder2 = new Funder();
    Grant grant = new Grant();

    @BeforeAll
    public void setupObjects() throws IOException {

        System.setProperty("INSTITUTION", "johnshopkins.edu");
        System.setProperty("INSTITUTIONAL_REPOSITORY_NAME", "JScholarship");
        System.setProperty("INSTITUTIONAL_POLICY_TITLE", "JHU Open Access Policy");

        repository1.setName("Repository 1");
        repository1.setDescription("Repository for policy1");

        repository2.setName("Repository 2");
        repository2.setDescription("Repository for policy2");

        repository3.setName("JScholarship");
        repository3.setDescription("JHU Institutional Repository");

        policy1.getRepositories().add(repository1);
        policy1.setInstitution(URI.create("http://www.jhu.edu"));

        funder1.setPolicy(policy1);
        funder1.setName("Direct Funder");

        policy2.getRepositories().add(repository2);

        policy3.setTitle("JHU Open Access Policy");

        funder2.setPolicy(policy2);
        funder2.setName("Primary Funder");

        grant.setProjectName("Test Project");
        grant.setPrimaryFunder(funder2);
        grant.setDirectFunder(funder1);

        submission = new Submission();
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.NOT_STARTED);
        submission.setSubmissionStatus(SubmissionStatus.DRAFT);
        submission.setSubmitterName("Bessie");
        submission.setSource(Source.OTHER);
        submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
        submission.getGrants().add(grant);

        try (PassClient client = PassClient.newInstance(refreshableElide)) {
            client.createObject(repository1);
            client.createObject(repository2);
            client.createObject(repository3);
            client.createObject(policy1);
            client.createObject(funder1);
            client.createObject(policy2);
            client.createObject(funder2);
            client.createObject(policy3);
            client.createObject(grant);
            client.createObject(submission);
        }
    }

    @Test
    public void SubmissionPoliciesTest() throws IOException, JSONException {
        HttpUrl url = formServiceUrl("policies", submission.getId().toString());
        Request.Builder builder = new Request.Builder();
        setShibHeaders(builder);

        Request okHttpRequest = builder
            .url(url)
            .build();

        Call call = client.newCall(okHttpRequest);

        try (Response okHttpResponse = call.execute()) {
            assertEquals(200, okHttpResponse.code());
            JSONArray result = new JSONArray(okHttpResponse.body().string());
            assertEquals(3, result.length());
        }
    }

    @Test
    public void SubmissionRepositoriesTest() throws IOException, JSONException {
        HttpUrl url = formServiceUrl("repositories", submission.getId().toString());
        Request.Builder builder = new Request.Builder();
        setShibHeaders(builder);

        Request okHttpRequest = builder
            .url(url)
            .build();

        Call call = client.newCall(okHttpRequest);

        try (Response okHttpResponse = call.execute()) {
            assertEquals(200, okHttpResponse.code());
            JSONArray result = new JSONArray(okHttpResponse.body().string());
            assertEquals(3, result.length());
        }

    }

    private HttpUrl formServiceUrl(String endpoint, String parameterValue) {
        return new HttpUrl.Builder()
            .scheme("http")
            .host("localhost")
            .port(getPort())
            .addPathSegment("policy")
            .addPathSegment(endpoint)
            .addQueryParameter("submission", parameterValue)
            .build();
    }

    @AfterAll
    public void tearDown() throws IOException {
        try (PassClient client = PassClient.newInstance(refreshableElide)) {
            client.deleteObject(repository1);
            client.deleteObject(repository2);
            client.deleteObject(repository3);
            client.deleteObject(policy1);
            client.deleteObject(funder1);
            client.deleteObject(policy2);
            client.deleteObject(funder2);
            client.deleteObject(policy3);
            client.deleteObject(grant);
            client.deleteObject(submission);
        }
    }
}
